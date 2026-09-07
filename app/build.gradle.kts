import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val repositoryDir = rootDir
val tinitalkAdminVersion = "0.2.0"
val releaseTag = providers.gradleProperty("releaseTag").orNull
val releaseSigningPropertiesFile = rootProject.file("keystore/release.properties")
val releaseSigningPropertiesResult = runCatching {
    Properties().apply {
        if (releaseSigningPropertiesFile.isFile) {
            releaseSigningPropertiesFile.inputStream().use(::load)
        }
    }
}
val releaseSigningProperties = releaseSigningPropertiesResult.getOrDefault(Properties())

fun signingProperty(name: String): String? =
    releaseSigningProperties.getProperty(name)?.takeIf(String::isNotBlank)

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
        versionCode = 2
        versionName = tinitalkAdminVersion
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

    signingConfigs {
        create("release") {
            storeFile = signingProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = signingProperty("storePassword")
            keyAlias = signingProperty("keyAlias")
            keyPassword = signingProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("min") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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

val validateReleaseConfiguration = tasks.register("validateReleaseConfiguration") {
    group = "verification"
    description = "Checks release signing settings and the optional release tag."
    doLast {
        check(releaseSigningPropertiesFile.isFile) {
            "Release signing is not configured: create keystore/release.properties (see README.md)."
        }
        check(releaseSigningPropertiesResult.isSuccess) {
            "Cannot read keystore/release.properties. Check the file format and permissions."
        }
        for (name in listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) {
            check(signingProperty(name) != null) { "Missing '$name' in keystore/release.properties." }
        }
        check(rootProject.file(checkNotNull(signingProperty("storeFile"))).isFile) {
            "Release keystore does not exist. Check storeFile in keystore/release.properties."
        }
        check(releaseTag == null || releaseTag == "v$tinitalkAdminVersion") {
            "Release tag must match the app version: v$tinitalkAdminVersion."
        }
    }
}

// Gate release packaging/signing, not IDE sync, lint or unit tests.
tasks.configureEach {
    if (name in setOf("validateSigningRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle", "assembleRelease", "bundleRelease")) {
        dependsOn(validateReleaseConfiguration)
    }
}

tasks.register<Copy>("exportReleaseApk") {
    group = "build"
    description = "Copies the signed release APK to dist with its version in the filename."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(repositoryDir.resolve("dist"))
    rename { "tinitalk-admin-v$tinitalkAdminVersion.apk" }
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
