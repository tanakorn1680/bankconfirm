plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bankamountreader"
    // compileSdk 34 = Android 14, stable ใน GitHub Actions ubuntu-latest
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bankamountreader"
        // minSdk 26 = Android 8.0 ครอบคลุมมือถือ Android ส่วนใหญ่ที่ยังใช้งานอยู่
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // =====================================================================
        // Configuration — เปลี่ยนได้ง่ายใน BuildConfig
        // =====================================================================
        buildConfigField("String", "BANK_APP_1_PACKAGE", "\"com.kasikorn.retail.mbanking.wap\"")
        buildConfigField("String", "BANK_APP_2_PACKAGE", "\"com.scb.phone\"")
        buildConfigField("String", "API_BASE_URL", "\"https://scid-ecru.vercel.app\"")
        buildConfigField("String", "DEVICE_TOKEN", "\"mytoken2026\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // HTTP + JSON
    implementation(libs.okhttp)
    implementation(libs.gson)
    // UI components
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
}
