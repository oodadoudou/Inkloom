# Inkloom 0.234.0 Debug

Source: `3072e7421a86496b5d8b00571bee4f55ed45100e`. Includes the 0.233.0 layout changes.

## Changes

- Page navigation: following a link, search hit, bookmark, outline entry, the page list or a page thumbnail is a jump. Back on the page navigator (Alt+Left) returns to where the jump started, Forward (Alt+Right) repeats it, and each names its target page. Page turns are not recorded; history follows page IDs, so reordering keeps it and deleted pages are skipped.
- Go to page: type a page number or a custom page label and press Enter; a page that does not exist is reported. Rows show the labels used by the pages panel and mark bookmarked pages. Opening the panel does not raise the keyboard, and a jump closes it.
- Insert link: choosing Web link or Notebook page hides the keyboard so the page list stays in view.
- Library: a new or emptied Library stays empty instead of gaining two sample notebooks, and every empty section explains itself (no notebooks yet, no matches, empty folder, recents, favorites, archive, PDF, Trash).

## Validation

- JVM: 1,042 tests, zero failures/errors, eight skipped.
- Android 15: one full run of 890 tests passed 883; the 7 failures came from a duplicate New Note button in the empty state, which was removed, and that class then passed. Navigation suite 10/10 twice; 217 related tests passed.
- Lint: zero errors, 114 existing warnings. Diff whitespace check passed.
- APK v2 signature, version 234, install and cold start passed; launch screenshot inspected; crash buffer empty.

## Boundaries

Real-device testing is pending. Source commits are local and have not been pushed. Upgrading keeps existing notebooks; only a new or emptied Library is affected by the sample change. Drive delivery checks the four files in the local Google Drive Desktop sync folder; a cloud re-download is not verified. Earlier 0.232.0 and 0.233.0 deliveries are kept until this build is approved.
