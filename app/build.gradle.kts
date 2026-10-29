plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.dropbridge.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dropbridge.app"
        minSdk = 26
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
    }
    // Fixed signing key: every build is signed the same, so new APKs install over old ones
    signingConfigs {
        create("dropbridge") {
            storeFile = file("dropbridge.p12")
            storePassword = "dropbridge"
            keyAlias = "dropbridge"
            keyPassword = "dropbridge"
            storeType = "pkcs12"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("dropbridge")
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
