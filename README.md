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
| 7 PDF generation (pure Kotlin, in `:domain`) | done — verified by rendering the PDF and measuring pixels (55 tests total) |
| 4–6 UI (home, picker, editor, sizes, preview) | not started — needs Android Studio to compile |
| 8+ Android print framework, projects, templates … | not started |
