plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jhio.subtitulos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jhio.subtitulos"
        minSdk = 29          // Android 10: mínimo para capturar audio de otras apps
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") } // Samsung A56 = arm64
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Traducción sin internet (japonés -> español)
    implementation("com.google.mlkit:translate:17.0.3")
    // Reconocimiento de voz sin internet (Vosk)
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
