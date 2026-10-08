// Compose UI: theme, components and screens, written against the common androidx.compose API.
//
// This module is compiled here with Compose Desktop purely to type-check the UI code. The Android app
// (:app) compiles the very same sources against androidx Compose (see app/build.gradle.kts), so nothing
// is duplicated. There are no tests in this module on purpose: all behaviour lives in :presentation and
// :domain (unit-tested); these files only draw state and forward events. Screenshot tests need the
// Compose runtime, whose desktop artifacts depend on Google Maven, so add them where that host is reachable.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":presentation"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
}
