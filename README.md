# Inkloom Android

An offline professional painting engine inside a lightweight Android notebook, with PDF annotation and brush import. Android 10 and later; the primary device is Wacom MovinkPad Pro 14 on Android 15.

## Product notes

Detailed product requirements, priorities, interaction contracts, and local development instructions live in the ignored `docs/` directory and are not included in this repository. Historical plans are not current completion evidence.

## Build prerequisites

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

Private brush manifests and scoped local records are kept under the ignored `tasks/` directory; generated validation evidence is kept under `.codex-artifacts/`. Product requirements stay local. Superseded plans and design documents have been archived outside the project.
