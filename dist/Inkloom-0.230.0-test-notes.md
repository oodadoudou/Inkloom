# Inkloom 0.230.0 Debug verification

Source: `4b2b3b8b254e2d6a5cd7959613f71f6b94ddbf95`. APK SHA-256: `3efae1adb9fa147efae15bef9fa1f6077d055b8033d32b741c32d06d6320d1d1`.

Fixes from the 0.229.0 review (each test failed before its fix):

- Layered PNG keeps draft and draft-folder flags whether or not "Include draft layers" is checked. The export dialog no longer offers the option for that format, and doesn't carry it over after a format switch. The include row toggles from its label.
- Excluding drafts from PSD/ORA/flat export removes the whole subtree of a folder clipped to an omitted draft. An adjustment inside such a folder no longer blocks the PSD export.
- "Exclude editing layer" is no longer bypassed by the reference-layer fallback.
- Fill/selection references keep the layer tree and every clipping base. Excluded or non-referenced layers act as hidden layers, so layers clipped to them disappear instead of re-clipping to a lower layer.

Intermittent failures:

- Selection-stroke window test: root cause found and fixed in the product. The stroke and colour-picker dialogs read the focus manager from the activity window, so their text fields stayed focused. After the nested picker closed, the keyboard could reappear and move the dialog under the tap. Under CPU load: 3/20 failures before, 0/20 after. No retries or longer timeouts.
- Colour-history window test: a test race (click injected while the keyboard slid the dialog). The test now closes the keyboard and taps the settled button: ~1/10 before, 0/20 after.
- Masked-adjustment undo test: one unexplained timeout in a consolidated run, not reproduced since. The test now reports editor state if it recurs.

1028 JVM tests: 0 failures, 8 skipped. 227 Android 15 tests passed in one run on the release source.

No full Android-suite success is claimed. MovinkPad touch, pressure, latency, 120 Hz dragging and long sessions remain DEVICE_PENDING. Drive Desktop local four-file hashes are checked during delivery; cloud redownload is unverified. Known approximations unchanged (clipped adjustments by base coverage; original sharpen kernels).
