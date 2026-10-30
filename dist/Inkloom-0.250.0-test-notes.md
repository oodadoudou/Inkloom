# Inkloom 0.250.0 Debug

Source: `bf96d6b7`. Includes everything in 0.249.0.

## Changes

- Notebooks save in the background: a pen-up writes only the changed page to the recovery journal (4–8 ms instead of seconds for long PDF notebooks); the notebook file is rewritten a few seconds after the ink settles. Files are about four times smaller.
- Crash safety: a notebook save can no longer be undone by older journal entries after a crash; pages the checkpoint would refuse are rejected up front.
- Fewer repeated reads and writes per stroke (folder index, resume position); Pages panel thumbnails are cached.
- Fix: in a narrow window the selection bar's "…" menu and exit buttons stayed out of view.
- About 490 lines of unused code removed, including a setting that was saved but never read.

## Validation

JVM 1,266 (8 skipped); full Android 15 suite 976/977 (the frame-time benchmark that missed under load passed 3/3 on rerun); force-stop durability check; lint 0 errors / 114 warnings; v2 signature; clean install and cold start; crash buffer empty.

## Boundaries

DEVICE_PENDING on the MovinkPad. Not pushed.
