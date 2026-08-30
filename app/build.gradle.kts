plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val repositoryDir = rootDir
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
    compileSdk = 36

    defaultConfig {
        applicationId = "org.tinitalk.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "COMMIT_HASH", "\"$commitHash\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("main").assets.srcDir(rootProject.file("ssh-scripts"))

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
