plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services") version "4.5.0"
}

android {
    namespace = "dev.vixxer.mensajero"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vixxer.mensajero"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.3.6"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        val turnUsuario = (project.findProperty("VIXXER_TURN_USUARIO") as String?)
            ?: System.getenv("VIXXER_TURN_USUARIO")
            ?: "openrelayproject"
        val turnCredencial = (project.findProperty("VIXXER_TURN_CREDENCIAL") as String?)
            ?: System.getenv("VIXXER_TURN_CREDENCIAL")
            ?: "openrelayproject"
        buildConfigField("String", "TURN_USUARIO", "\"$turnUsuario\"")
        buildConfigField("String", "TURN_CREDENCIAL", "\"$turnCredencial\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                when (project.findProperty("capturas"))
                {
                    "grabar" -> it.systemProperty("roborazzi.test.record", "true")
                    "verificar" -> it.systemProperty("roborazzi.test.verify", "true")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":nucleo")) {
        exclude(group = "com.goterl", module = "lazysodium-java")
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.19.1@aar")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("io.getstream:stream-webrtc-android:1.3.10")
    implementation("com.google.firebase:firebase-messaging:25.1.1")

    testImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.71.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.71.0")
}
