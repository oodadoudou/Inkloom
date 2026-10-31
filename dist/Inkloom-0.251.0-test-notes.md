# Inkloom 0.251.0 Debug

Source: `33992af3`. Includes everything in 0.250.0.

## Changes

- New UI rule: no unnecessary secondary labels or descriptions; the layout does the explaining.
- Library: the title is where you are (Library, Recents, a folder…); one heading instead of three; view toggles beside search; cards show the page count; empty states are one line.
- Settings: each row is a label and a switch; explanatory paragraphs and info toggles removed.
- Tool property, tool dock and the selection bar show options only; the selection bar is one row for lasso, rectangle and ellipse.
- Dialogs keep values, warnings and short consequences; explanations removed.

## Validation

JVM 1,266 (8 skipped); full Android 15 suite 973/977 with the 3 stale shortcut-editor tests fixed (affected classes 88/88) and the load-sensitive benchmark 2/2 on rerun; lint 0 errors / 113 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad. Not pushed.
