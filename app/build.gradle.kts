plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.vixxer.mensajero"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vixxer.mensajero.nativo"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-f2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":nucleo")) {
        exclude(group = "com.goterl", module = "lazysodium-java")
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.biometric:biometric:1.1.0")
}
