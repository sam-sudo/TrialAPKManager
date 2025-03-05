plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.ck.trialapkmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ck.trialapkmanager"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        resourceConfigurations.addAll(listOf("en","es"))// ✅ Solo mantiene inglés y español
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks") // 🔹 Ruta al archivo JKS
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "prueba123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "my-key"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "prueba123"
        }
    }

    buildTypes {
        buildTypes {
            release {
                isMinifyEnabled = true // ✅ Habilita R8
                isShrinkResources = true // ✅ Elimina recursos no utilizados
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
                signingConfig = signingConfigs.getByName("release") // 🔹 Usa la firma en release

            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3) // Material 3 para Composables básicos
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    
}