plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.safetyclient"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.safetyclient"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.6-lan-heartbeat"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
