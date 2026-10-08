# PHOTOPrint Pro

*Perfect Photos. Perfect Prints.*

Android app for creating physically accurate photo sheets (passport, ID, 2×2 …) on real paper sizes
(4×6, A4 …) and printing them at actual size.

## Modules

| Module          | Kind                        | Contents                                                                                   | Verified here |
|-----------------|-----------------------------|--------------------------------------------------------------------------------------------|---------------|
| `:domain`       | pure Kotlin / JVM           | models, **Layout Engine**, PDF writer, crop maths, printing rules, calibration, projects, storage codecs | 103 unit tests |
| `:presentation` | pure Kotlin / JVM           | navigation, session state, `AppController` ("ViewModel"), gesture & viewport maths, settings codec | 99 unit tests |
| `:ui`           | Compose (type-checked only) | theme, components, 15 screens, `PhotoPrintRoot`                                            | compiles against the real Compose API; no screenshots/tests |
| `:app`          | Android                     | Activity, Hilt ViewModel, Room + DataStore adapters, `AndroidPlatformGateway` (picker, camera, export, share, print) | **not compiled** (no Android SDK in the authoring sandbox) |

`:app` is included automatically when an Android SDK is found (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, or
`sdk.dir` in `local.properties` — Android Studio sets this). It compiles the `:ui` sources directly
(`sourceSets.main.java.srcDir("../ui/src/main/kotlin")`) against androidx Compose, so the UI code exists once.

## Commands

```
./gradlew test                 # 202 tests, no Android SDK needed
./gradlew :ui:compileKotlin    # type-checks all Compose code (Compose Desktop, no SDK needed)
./gradlew :app:assembleDebug   # needs the Android SDK
```

## Status

| Area | Status |
|------|--------|
| Layout Engine, PDF export, crop maths, calibration page, readiness check | done, tested (rendered PDFs are measured pixel-for-pixel) |
| Home, photo selection, editor, photo/paper size, layout, preview, printer, print settings, final check, projects, templates, settings, calibration screens | written and type-checked; **never run or looked at on a device** |
| Android glue (photo picker, camera, PDF save/share/image, system print dialog) | written, **never compiled or run** |
| Room (projects, templates) and DataStore (settings) | adapters written, **never compiled or run**; the mapping they rely on (`StorageCodec`, `SettingsCodec`) is tested |
| Languages other than English, localisation | strings centralised in `Strings.kt`; no translations |
| Sharpness slider, background options, AI features | not started |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design and the known limits.
