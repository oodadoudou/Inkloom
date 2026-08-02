# Android 15 smoke-test record

Tested on 2026-08-02 with the project-local `inkloom_api35_tablet` AVD:

- Android API 35 / Android 15, ARM64, 2560×1600 Pixel Tablet profile.
- Inkloom `0.28.0` (`versionCode 29`, `targetSdk 35`).
- Three instrumentation tests passed: library launch, Settings/input-controls access, and Android bitmap verification of extracted ABR mask orientation.
- Cold launch reported `Status: ok` and the Inkloom process remained alive.
- App-scoped log inspection found no fatal exception, Android runtime crash, or ANR.
- The final 87 JVM tests passed with zero failures, errors, or skips; Android Lint and debug/debug-test APK assembly also passed. This includes both authorized real ABR files and a 5,000-stroke deterministic sampling regression (93 ms on the build host).

Visual evidence:

- [Library](screenshots/android15-library.png)
- [Editor](screenshots/android15-editor.png)
- [Input settings](screenshots/android15-settings-input.png)
- [Tool and pen-button settings](screenshots/android15-settings-tools.png)

Re-run the same path with:

```bash
./scripts/start_android15_emulator.sh --headless
./scripts/run_android15_smoke_test.sh
```

The emulator cannot establish Wacom hardware behavior. Pressure range, hover, palm rejection, pen-tail/side-button reporting and perceived latency still require the Android 15 host connected to the Movink 14.
