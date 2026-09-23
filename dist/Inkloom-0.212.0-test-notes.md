# Inkloom 0.212.0 Debug

Source: `efdbfb4a9b412a7ed5514961e9bd170983d2c2d3`

- R11 in progress: eyedropper source selection, paper/transparent sampling, tagged-image conversion into the sRGB working space, per-notebook recent ARGB colors, and numeric Alpha in the active main/sub color picker.
- PNG encoding on Android 15 was checked for an sRGB declaration.
- Validation: JVM tests and lint passed; focused Android 15 sampling, persistence, color-window, image-profile and PNG tests passed; v2 signature, installation and cold start passed. Full Android instrumentation was not completed.
- Optional document ICC assignment/conversion, end-to-end wide-gamut painting, CMYK output and soft proof remain open. Wacom MovinkPad Pro behavior remains `DEVICE_PENDING`.
- Google Drive Desktop local sync is checked separately; remote cloud download is not verified.
