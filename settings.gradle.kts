pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "PhotoPrintPro"

// Pure Kotlin/JVM: models, Layout Engine, geometry. Builds and tests anywhere (no Android SDK).
include(":domain")

// Pure Kotlin UI logic (navigation, session state, gesture and viewport maths). Fully unit-tested.
include(":presentation")

// Compose UI (theme, components, screens) written against the common androidx.compose API. It is type-checked
// on the JVM with Compose Desktop; :app compiles the same sources against androidx Compose.
include(":ui")

// Android application. Only included when an Android SDK is present, so the domain module can
// be built and tested on machines/CI images without one. Android Studio creates local.properties
// with sdk.dir, which enables this automatically.
val localProps = file("local.properties")
val hasAndroidSdk =
    !System.getenv("ANDROID_HOME").isNullOrBlank() ||
        !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
        (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("Android SDK not found: building :domain only (set ANDROID_HOME to include :app).")
}
