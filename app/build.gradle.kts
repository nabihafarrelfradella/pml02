plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.pml_uts"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.pml_uts"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    // Image loading Coil
    implementation("io.coil-kt:coil:2.6.0")

    // HTTP Client & JSON Parser for Laravel API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // QR Code Generator (ZXing)
    implementation("com.google.zxing:core:3.5.3")

    // Supabase Storage SDK (Poin 33 Penilaian UTS 7%)
    val supabaseVersion = "2.3.1"
    val ktorVersion = "2.3.11"
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}