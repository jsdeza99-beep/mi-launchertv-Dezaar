import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val fixedKeystore = layout.buildDirectory.file("fixed.keystore").get().asFile
if (!fixedKeystore.exists()) {
    fixedKeystore.parentFile.mkdirs()
    fixedKeystore.writeBytes(
        Base64.getDecoder().decode(
            "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"
        )
    )
}

android {
    namespace = "com.tono.launcher"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = fixedKeystore
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.tono.launcher"
        minSdk = 24
        targetSdk = 34
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
