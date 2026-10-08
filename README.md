# PHOTOPrint Pro

*Perfect Photos. Perfect Prints.*

Android app for creating physically accurate photo sheets (passport, ID, 2×2 …) on real paper
sizes (4×6, A4 …) and printing them at actual size.

## Modules

| Module    | Kind              | Contents                                                                 |
|-----------|-------------------|--------------------------------------------------------------------------|
| `:domain` | pure Kotlin / JVM | models, **Layout Engine**, mm → preview / PDF / print geometry, unit tests |
| `:app`    | Android (Compose) | design system, app shell (UI phases build on this)                       |

`:domain` has no Android or Compose dependency, so the part that determines print accuracy can be
tested anywhere. `:app` is included automatically when an Android SDK is found
(`ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties` — Android Studio sets this).

## Commands

```
./gradlew :domain:test        # layout engine + geometry tests (no Android SDK needed)
./gradlew :app:assembleDebug  # needs Android SDK
```

## Status

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design, the single-layout-model rule and
known limits.

| Phase | Status |
|-------|--------|
| 1 Architecture + design system | done — `:app` **not yet compiled** (no Android SDK in the authoring environment) |
| 2 Measurement models + Layout Engine | done |
| 3 Layout Engine tests | done |
| 7 PDF generation (pure Kotlin, in `:domain`) | done — verified by rendering the PDF and measuring pixels |
| 9 (domain half) projects, templates, repositories | done as pure Kotlin; Room implementation still to do in `:app` |
| 8 (domain half) printing models, safety check, test print | done; `PrintDocumentAdapter` / printer discovery still to do in `:app` |
| 11 (domain half) calibration page + scale diagnosis | done; the physical measurement itself needs a real printer |
| 4–6 UI (home, picker, editor, sizes, preview) | not started — needs Android Studio to compile |
| Android print framework, Room, DataStore | not started — needs Android Studio |
