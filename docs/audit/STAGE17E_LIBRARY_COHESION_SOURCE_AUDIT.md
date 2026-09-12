# Stage 17E — Library Cohesion Source Audit

## Status

Stage 17E is a source-only final Library cohesion pass. It does not establish physical-device visual acceptance.

- Base Stage 17D HEAD: `ff0d9f5dadaf566abe9b0756f157edad25ba8205`
- Stage 17E implementation commit: `335eebc4e68ae7eb4b1d9022fe6b3ec099ba72c1`
- Target branch: `stage-17e-library-cohesion-polish`
- Scope: Library presentation only
- APK/AAB authorization: **not granted**

## User-directed objective

Perform a final horizontal Library polish pass for:

- shelf spacing and alignment,
- responsive hierarchy,
- empty/loading/error presentation,
- action-chrome consistency,
- without changing fullscreen player behavior or functional playback/data semantics.

## Source changes

The implementation commit changes exactly two source files relative to Stage 17D:

1. `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryActions.kt`
2. `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt`

### `LibraryActions.kt`

- Added Library-local `LibraryStateTone` values for `LOADING`, `EMPTY`, `WARNING`, and `ERROR`.
- Added `LibraryShelfSection` to standardize shelf header/content spacing without introducing another boxed/card layer.
- Refined `LibraryShelfState` into a flat state row with a restrained 2dp semantic rail instead of a generic panel treatment.
- Preserved the existing Library-local action and category-filter behavior.

### `LibraryShell.kt`

- Rebuilt the Library home composition around consistent shelf sections and spacing.
- Prevented empty shelf/header noise while the catalog is loading or when no source is active.
- Added explicit, visually consistent loading, empty, warning, and error states.
- Added compact shelf counts only when useful.
- Preserved provider-category selection and shelf content behavior.
- Normalized Continue Watching progress geometry from 3dp to 2dp to align with the offline-media progress language.
- Refined `PlaybackChoiceButtons` to the same 92%-width primary/secondary action hierarchy used across the Library.
- Kept Resume/Start Over callbacks and preference semantics unchanged.

## Explicit non-goals / unchanged behavior

Stage 17E does **not** change:

- `LibraryFullscreenPlayer` or any downstream player code,
- player chrome or transport geometry,
- `PlaybackController`, session ownership, or video-surface ownership,
- Live UI/state machine or PiP behavior,
- provider/network/catalog semantics,
- Room schema or persistence behavior,
- download repository/state machine/storage/integrity/offline-resume semantics,
- Settings/source management,
- package/version/signing/release/deployment configuration.

The helper validation performed a direct invariant check that the `LibraryShell.kt` text from `LibraryFullscreenPlayer` through the end of the file remained byte-for-byte identical to Stage 17D.

## Helper validation evidence

Authoritative corrected helper run:

- Workflow: `Stage 17E Library cohesion helper`
- Run: `34694084671`
- Job: `103554433355`
- Exact base checkout verified: `ff0d9f5dadaf566abe9b0756f157edad25ba8205`
- Exact source diff guard: PASS — only `LibraryActions.kt` and `LibraryShell.kt`
- Fullscreen-player/downstream invariant: PASS
- `compileDebugKotlin`: PASS
- `compileDebugUnitTestKotlin`: PASS
- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `BUILD SUCCESSFUL in 3m 32s`
- 34 actionable tasks: 34 executed
- Generated Room schemas match committed HEAD; schema tree clean
- Explicit no-APK/AAB guard: PASS
- Source commit pushed: `335eebc4e68ae7eb4b1d9022fe6b3ec099ba72c1`

A first helper validation attempt failed at Kotlin compile because the generated Continue Watching section was missing one composable call delimiter. No source commit was pushed from that failed attempt. A diagnostic-only run exposed the exact malformed lines, after which the helper applied a bounded syntax correction and the authoritative run above passed. No history rewrite/reset/rebase was used.

## Non-blocking warnings

The authoritative helper run retained the existing warning in:

- `DownloadRepositoryImpl.kt:363` — Elvis operator always returns the left operand.

GitHub Actions also reports tooling deprecations for `setup-java@v4` and Node-20-targeted actions being forced onto Node 24. These are not application-validation failures.

## Acceptance boundary

Stage 17E helper validation establishes **source PASS only**. It does not prove physical-device Library density, shelf alignment, responsive geometry, state hierarchy, or action-chrome visual acceptance.

Physical Library QA remains `NOT_YET_VERIFIED` until an explicitly authorized QA APK is installed and compared on-device against the approved visual contract.
