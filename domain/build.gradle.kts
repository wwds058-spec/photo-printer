plugins {
    alias(libs.plugins.kotlin.jvm)
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
    // Flow for repository interfaces; still pure JVM.
    api(libs.kotlinx.coroutines.core)
    // JSON tree API only (no compiler plugin): used by the storage codecs.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    // Independent PDF reader/renderer used only to verify the writer's physical output.
    testImplementation(libs.pdfbox)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
