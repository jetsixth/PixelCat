plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kloss.pixelcat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kloss.pixelcat"
        minSdk = 30          // Wear OS 3+
        targetSdk = 34       // Galaxy Watch Ultra = Wear OS 5 (API 34)
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
