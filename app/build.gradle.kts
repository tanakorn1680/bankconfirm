plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.bankamountreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bankamountreader"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 2
        versionName   = "2.0.0"

        // ── ตั้งค่า config ที่นี่ (เผา-เข้า APK ผ่าน BuildConfig) ──────────
        buildConfigField("String", "BANK_APP_1_PACKAGE", "\"com.kasikorn.retail.mbanking.wap\"")
        buildConfigField("String", "BANK_APP_2_PACKAGE", "\"com.truemoney.android.wallet\"")
        buildConfigField("String", "API_BASE_URL",        "\"https://scid-ecru.vercel.app\"")
        buildConfigField("String", "DEVICE_TOKEN",        "\"mytoken2026\"")
    }

    buildTypes {
        debug {
            isDebuggable      = true
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig  = true
        viewBinding  = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.recyclerview)
    // LocalBroadcastManager — ใช้ broadcast service → activity แบบ real-time
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
}
