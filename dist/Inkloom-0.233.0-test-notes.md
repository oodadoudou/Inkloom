# Inkloom 0.233.0 Debug

Source: `3695d7ff60342199ac86ef498bfa2a4aede2502a`.

## Changes

- Library list layout shows compact rows (cover thumbnail, title, page count, tags, actions) instead of full-width covers. Grid cards reserve the same title height so covers line up; the compact grid uses smaller titles and actions.
- The card actions button sits inside its touch target instead of against the card corner. The selected layout toggle reads as selected and the compact grid has its own icon. Page counts use "1 page".
- New-notebook covers mark the selection with a gapped ring that stays visible on light covers; smaller tiles bring page templates into view.
- The editor top bar keeps document actions on the left and brush, eraser, layers and colors on the right. Narrow windows scroll the row and shorten the title first.
- The tool rail fades at the edge that has more tools to scroll to. Adding PDF or image pages uses the import icon.
- Untouched panel layouts open Pages beside the tool rail; Colors is wide enough for all four tabs. Brushes, colors and layers keep their right-hand positions. Saved or moved layouts are unchanged.

## Validation

- JVM: 1,029 tests, zero failures/errors, eight skipped.
- Android 15: final run of 51 tests in the panel-placement-sensitive classes passed with zero failures. Earlier targeted runs on this work: 100/100 for library, creation, panels and tool rail. A 40-class run exposed 8 failures from a rejected panel arrangement, which was reverted.
- Lint: zero errors, 114 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 233, install and cold start passed. Launch screenshot inspected; crash buffer empty.

## Boundaries

Not a full-suite or MovinkPad verification; real-device testing is pending. Source commits are local only and have not been pushed. Drive delivery checks the four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified. The previous 0.232.0 delivery is kept as a fallback until this build is approved.
