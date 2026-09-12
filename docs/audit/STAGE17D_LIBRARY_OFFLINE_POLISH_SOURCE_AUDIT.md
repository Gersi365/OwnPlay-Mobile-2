# Stage 17D — Library Offline Polish Source Audit

## Purpose

Stage 17D is a source-only Library presentation pass authorized after Stage 17C. The remaining visible Library gap was the `Downloaded Media` shelf, which still used a comparatively card-heavy treatment and shared generic compact download controls while the surrounding Library had moved to flatter, media-first geometry.

This pass is deliberately presentation-scoped. It does not alter download persistence, integrity, playback ownership, provider behavior, or release configuration.

## Provenance

- Repository: `Gersi365/OwnPlay-Mobile-2`
- Base branch: `stage-17c-library-detail-polish`
- Exact Stage 17C base SHA: `39d690caaa7880592a266f405d8044c3a3fc88a5`
- Stage 17D branch: `stage-17d-library-offline-polish`
- Stage 17D implementation commit: `23a409d33d1b7316bc9556c9966533fb6ff8f084`
- Implementation commit parent: `39d690caaa7880592a266f405d8044c3a3fc88a5`
- Implementation commit message: `Polish downloaded media hierarchy`

The implementation diff from the exact Stage 17C base contains only:

1. `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt`
2. `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryOfflineControls.kt`

No unrelated source file is part of the implementation commit.

## Source audit before mutation

The Stage 17C `DownloadedRow` used:

- a fixed-width translucent `Surface`
- a square accent checkmark tile
- title plus static `Available offline` copy
- shared `DownloadControls` rendered inside each card

The surrounding Library hierarchy had already moved toward flatter media-led shelves, restrained rails, responsive carousel widths, and Library-local action primitives. `DownloadControls` is shared outside this shelf, so changing it globally would have expanded the pass beyond the approved Library offline visual scope.

`DownloadStatePolicy` was therefore retained as the behavioral authority while the shelf received Library-local presentation controls.

## Stage 17D changes

### Downloaded Media shelf

`LibraryShell.kt` now:

- shows a quiet item-count label such as `3 offline` in the `Downloaded Media` shelf header when items exist
- removes the enclosing translucent card surface from each downloaded-media item
- removes the decorative square checkmark tile
- uses a restrained 2dp × 44dp accent rail for offline identity
- distinguishes media with compact eyebrow text:
  - `MOVIE • OFFLINE`
  - `EPISODE • OFFLINE`
- keeps the media title as the primary textual hierarchy
- makes item width responsive with `(maxWidth * 0.84f).coerceIn(244.dp, 292.dp)`
- uses snap-to-start carousel behavior for deterministic browsing geometry
- delegates matching download state/action presentation to the new Library-local `LibraryOfflineControls`

### Library-local offline controls

`LibraryOfflineControls.kt` was added so the downloaded shelf can match the Library action language without changing the shared global download control component.

It:

- derives the primary action from the existing `DownloadStatePolicy.primaryAction(item)`
- preserves the existing `DownloadAction` values and dispatch path
- presents compact state copy for queued, downloading, paused, failed, and completed states
- shows download progress on a restrained 2dp line when progress exists
- renders the policy-selected primary action using `LibraryPrimaryAction`
- renders `Remove` as a secondary Library action
- uses the existing Library play glyph for offline Play/Resume actions
- keeps `DownloadAction.REMOVE` explicit and unchanged

The visual labels are shortened only within this clearly offline shelf context; the underlying actions remain unchanged.

## Explicitly unchanged

Stage 17D does **not** change:

- Library fullscreen player or player chrome
- `PlaybackController`, media session, video-surface ownership, or one-player continuity
- Live UI, Live reducer/state machine, Preview/fullscreen behavior, or PiP ownership
- Settings or source-management UI/behavior
- provider/network/catalog parsing or refresh semantics
- Room schema or persisted application data model
- download repository/state machine
- download storage paths, integrity validation, recovery semantics, or offline file ownership
- offline resume-position semantics
- package name, version code/name, signing identity, or release/deployment configuration

## Validation

A bounded helper workflow was used only to apply and validate the two-file source patch from the exact Stage 17C base before pushing the isolated Stage 17D branch.

Authoritative successful helper validation:

- Workflow: `Stage 17D offline polish helper v2`
- Run: `34692749112`
- Job: `103550801070`
- Exact checked-out base: `39d690caaa7880592a266f405d8044c3a3fc88a5`
- Exact two-file source-scope guard: PASS
- `compileDebugKotlin`: PASS
- `compileDebugUnitTestKotlin`: PASS
- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- Build result: `BUILD SUCCESSFUL in 3m 53s`
- Gradle tasks: `34 actionable tasks: 34 executed`
- Room schema verification: PASS; generated schemas match committed HEAD and schema tree is clean
- APK/AAB guard: PASS; no APK/AAB produced

Non-blocking compiler/tooling warnings observed:

- `DownloadRepositoryImpl.kt:363`: Elvis operator always returns the left operand
- `LibraryShell.kt:513`: unnecessary safe call on a non-null `LibraryCatalog`
- GitHub Actions runtime/setup deprecation warnings (`setup-java@v4`, Node 20-targeted actions forced to Node 24)

These warnings did not fail source validation and are outside the Stage 17D visual scope.

Earlier helper attempts were tooling-only failures before a source branch mutation: one helper definition failed before useful execution, and a later guard omitted untracked files. The corrected v2 helper included both tracked modifications and untracked additions in its exact source-scope guard. Neither failed helper attempt changed the Stage 17D source branch.

## Acceptance boundary

Stage 17D is source-only. Source validation demonstrates compile/test/lint/schema integrity, not real-device visual acceptance.

Physical-device validation is still required for:

- downloaded shelf density and responsive width
- snap behavior on supported phone widths
- offline state copy hierarchy
- primary/secondary offline action balance
- progress-line visibility
- consistency with the approved Library visual language

Physical visual QA status: `NOT_YET_VERIFIED`.

No APK/AAB was authorized or generated as part of Stage 17D.
