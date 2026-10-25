# Inkloom 0.247.0 Debug

Source: `a6dd5ba3`. Includes everything in 0.246.0.

## Changes

- Fix: pen taps on buttons (tool rail, top bar, selection bar) were often lost. Resting the pen over a button showed its name in a popup that took the next touch; the name can no longer take any touch.
- A button's name shows after the pen rests on it briefly, beside the tools of the vertical rail; it hides when the pen leaves or touches down, and does not come back right after a tap.
- A finger hold on a top-bar button still names it without pressing it.

## Validation

JVM 1,241 (8 skipped); targeted Android suites 158/158 (see JSON); lint 0 errors / 114 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad: the emulator's injected pen confirms the fix, the real Wacom pen still needs checking. Not pushed.
