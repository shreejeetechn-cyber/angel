plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.safetyserver"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.safetyserver"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "0.6-compact-lan-heartbeat"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
