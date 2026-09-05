plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val repositoryDir = rootDir
val tinitalkAdminAbi = providers.gradleProperty("tinitalkAdminAbi").getOrElse("all")
require(tinitalkAdminAbi == "arm64" || tinitalkAdminAbi == "all") {
    "tinitalkAdminAbi must be 'arm64' or 'all'"
}

val commitHash = runCatching {
    val process = ProcessBuilder(
        "git",
        "-c",
        "safe.directory=${repositoryDir.absolutePath.replace('\\', '/')}",
        "rev-parse",
        "--short=8",
        "HEAD",
    ).directory(repositoryDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    check(process.waitFor() == 0 && output.matches(Regex("[0-9a-fA-F]+")))
    output
}.getOrDefault("unknown")

android {
    namespace = "org.tinitalk.admin"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.tinitalk.admin"
        minSdk = 26
        // Updating compileSdk must not opt into new runtime permission requirements.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "COMMIT_HASH", "\"$commitHash\"")
        if (tinitalkAdminAbi == "arm64") {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("main").assets.directories.add(rootProject.file("ssh-scripts").path)

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        // Preserve the license texts shared by Bouncy Castle's JARs.
        resources.merges += "/META-INF/LICENSE.md"
        resources.excludes += setOf(
            "/org/bouncycastle/pqc/legacy/picnic/lowmcL1.bin.properties",
            "/org/bouncycastle/pqc/legacy/picnic/lowmcL3.bin.properties",
            "/org/bouncycastle/pqc/legacy/picnic/lowmcL5.bin.properties",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sshj)

    // Use Bouncy Castle's coordinated versions for SSHJ's transitive dependencies.
    implementation(platform(libs.bouncycastle.bom))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
