// Top-level build file
// AGP 8.3.2 requires Gradle 8.4+ (เราใช้ 8.6 — safe)
// Kotlin 1.9.23 compatible กับ AGP 8.3.x
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
