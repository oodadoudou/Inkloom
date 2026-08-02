# Android 15 smoke-test record

Tested on 2026-08-02 with the project-local `inkloom_api35_tablet` AVD:

- Android API 35 / Android 15, ARM64, 2560×1600 Pixel Tablet profile.
- Inkloom `0.29.0` (`versionCode 30`, `targetSdk 35`).
- Four instrumentation tests passed: library launch, Settings/input-controls access, Android bitmap verification of extracted ABR mask orientation, and alpha-aware CSP texture-mask decoding.
- Cold launch reported `Status: ok` and the Inkloom process remained alive.
- App-scoped log inspection found no fatal exception, Android runtime crash, or ANR.
- The final 91 JVM tests passed with zero failures, errors, or skips; Android Lint and debug/debug-test APK assembly also passed. This includes both authorized real ABR files and a 5,000-stroke deterministic sampling regression (63 ms on the build host).
- The Library, Editor, Brush Library and both Settings sections were visually inspected at 2560×1600 after installation. App chrome and Material container surfaces are monochrome; drawing-color swatches remain intentionally available as content colors.

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
