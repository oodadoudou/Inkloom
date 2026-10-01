# Inkloom 0.220.0 Debug

Source: `30d865a2037b12e9663fa58b4f7ede7c61a46d3e`.

- Editor actions use Notebook / Canvas / Tools / Guides / Views / Share categories. Application preferences use General / Gestures / Pressure / Shortcuts / Backup. Duplicate selection settings and global configuration shortcuts are removed; shortcut profile import/export stays visible above its list.
- Selection modes scroll separately from fixed confirm/cancel controls. Selecting the tool again exits; changing to a brush also leaves quick-mask editing. The compact launcher keeps basic operations, with secondary commands and parameters in Selection actions.
- Pages / Saved / Tags share page-management commands. The movable footer picker opens near its navigator and reveals the current page. Deletion preserves the displayed page, or selects the next surviving page when the current page is removed; stored notebook resume positions are updated.
- Four neutral gray interface themes use a lighter medium-gray default, update without restarting the Activity, and persist through settings backup. Paint, paper, and exports retain their own colors.
- Native color-number entry retains typed digits during live preview. Filter previews process native-resolution artwork before scaling, and soft proof previews include paper without changing canonical sampled colors.
- Validation: 976 JVM tests (0 failures, 8 skipped), final 37 Android 15 window tests (0 failures/skips), lint (0 errors, 114 warnings), v2 APK signature, installation and cold startup passed. Delivery results are recorded in the adjacent JSON. Full Android-suite completion is not claimed. MovinkPad performance/touch behavior, external interchange-file applications, printing/wide-gamut hardware, and cloud re-download remain pending.
