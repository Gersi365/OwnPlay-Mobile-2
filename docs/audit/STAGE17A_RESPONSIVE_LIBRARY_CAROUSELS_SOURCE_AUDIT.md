# Stage 17A — Responsive Library Carousels Source Audit

## Decision trigger

After QA v22 showed that the Stage 15 media-first Library direction was materially improved, Stage 16 applied a source-only polish pass. The next requested improvement was to make Library shelves feel less fixed-width and more intentional across phone/tablet widths. The user explicitly authorized continuing with this source-only work and did not authorize an APK/AAB.

## Authoritative baseline

- Repository: `Gersi365/OwnPlay-Mobile-2`
- Base branch: `stage-16-library-visual-polish`
- Exact base SHA: `3efd1de4f0889c08891e7da7890785c58d9b74e4`
- Target branch: `stage-17a-responsive-library-carousels`
- Source change commit: `9902e6643dc4bb5e6fc5c6be88e0500cf65bc948`
- Source change parent: `3efd1de4f0889c08891e7da7890785c58d9b74e4`

## Scope

This pass is intentionally limited to Library browse/shelf interaction and geometry:

1. Continue Watching card width is responsive to the available shelf width:
   - `76%` of available width
   - clamped to `248dp..312dp`
2. Movie and Series poster width is responsive:
   - `39%` of available width
   - clamped to `126dp..148dp`
3. Continue Watching, Movies and Series use start-aligned Compose snap fling behavior so a fling settles on a deliberate card boundary rather than an arbitrary partial position.
4. Responsive widths preserve a visible next-card edge on phone widths, making horizontal scrolling discoverable without borders or extra chrome.
5. Movie/Series category strips keep their selected tab visible by auto-scrolling to the selected index with one prior tab retained as context where possible.

## Explicitly unchanged

This stage does **not** change:

- Library fullscreen player or player chrome
- `PlaybackController`, playback session ownership or surface ownership
- Live browse/Preview/fullscreen behavior
- provider/network parsing or catalog semantics
- Room entities, schemas, migrations or persistence behavior
- download state machine or offline integrity behavior
- Settings/source-management UI or logic
- package/version/signing/release/deployment configuration

## Source readback

Post-change source readback confirms:

- `LibraryCategoryStrip` owns a `LazyListState` and auto-scrolls selected categories.
- `ContinueWatchingRow` derives `cardWidth` from `BoxWithConstraints` and uses `rememberSnapFlingBehavior(..., SnapPosition.Start)`.
- `MovieRow` and `SeriesRow` derive poster widths from `BoxWithConstraints` and use the same start snap contract.
- `ContinueWatchingCard` and `PosterCard` receive width as `Dp`; their media-first artwork composition remains unchanged.

## Isolated validation

A first helper run (`34689825923`, job `103543011515`) failed only during the deterministic patch step because the helper script's indentation normalization did not match the Kotlin source block. No target-branch source was mutated, no Gradle validation ran, and no APK/AAB was produced by that failed attempt.

The corrected isolated helper completed successfully:

- Helper workflow: `Stage 17A responsive Library carousels helper v2`
- Run: `34689861087`
- Job: `103543105956`
- Exact checkout: `3efd1de4f0889c08891e7da7890785c58d9b74e4`
- `compileDebugKotlin`: PASS
- `compileDebugUnitTestKotlin`: PASS
- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- Build result: `BUILD SUCCESSFUL in 3m 21s`
- Gradle tasks: `34 actionable tasks: 34 executed`
- No APK/AAB guard: PASS

Existing non-blocking compiler warnings remained in `DownloadRepositoryImpl.kt` and two unnecessary-safe-call locations in `LibraryShell.kt`; this Stage 17A pass did not introduce a validation failure.

## Acceptance boundary

Stage 17A is a source-only stage. Source validation can establish compilation/test/lint integrity, but **cannot establish physical visual acceptance** of responsive card proportions, edge-peek amount, snap feel or category auto-scroll on a real device.

No APK/AAB is authorized or generated as part of this stage.
