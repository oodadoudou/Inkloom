# Inkloom Android

Offline-first Android handwriting, note-taking, PDF annotation, and brush-import application for Android 10 and later.

## Requirements

- JDK 17
- Android SDK 35
- Android Studio or the Android command-line tools

Set `sdk.dir` in the ignored `local.properties`, then run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

To include the authorized local brush samples in parser tests:

```bash
./gradlew testDebugUnitTest -PbrushSamplesDir=/absolute/path/to/brush/samples
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Android 15 emulator

The helper scripts keep their downloaded SDK, JDK, and AVD data in ignored project-local directories:

```bash
./scripts/setup_android15_emulator.sh
./scripts/start_android15_emulator.sh
./scripts/run_android15_smoke_test.sh
```

Use `./scripts/start_android15_emulator.sh --headless` for CI-style local testing.

Product/UI source material is kept locally under the ignored `design/` directory. Agent task records and generated validation artifacts are kept under the ignored `tasks/` directory; neither belongs in source control.
