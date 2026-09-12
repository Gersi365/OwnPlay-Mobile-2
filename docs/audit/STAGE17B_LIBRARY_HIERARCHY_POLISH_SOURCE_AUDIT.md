# Stage 17B — Library Hierarchy & Interaction Polish Source Audit

## Status

Source-only implementation stage. No APK/AAB generation is authorized by this stage and no physical-device visual acceptance is claimed.

## Authority and baseline

- User authorization: `Vazhdo` after Stage 17A source completion.
- Repository: `Gersi365/OwnPlay-Mobile-2`.
- Baseline branch: `stage-17a-responsive-library-carousels`.
- Exact Stage 17A baseline HEAD: `3b9024e9df7a6d92f9595b72cd1d52f7fe32995d`.
- Stage 17B target branch: `stage-17b-library-hierarchy-polish`.
- Source implementation commit: `b0e7b3436ed100c2f99d64056664907d3501ff86`.
- Source implementation commit parent: exact Stage 17A baseline HEAD.

## Scope

Stage 17B is intentionally presentation-only and Library-only.

Included:

- strengthen Library shelf hierarchy without adding chrome,
- reduce home-shelf vertical density/noise,
- make Continue Watching the leading Library shelf in typography,
- replace home-shelf loading/empty cards with compact inline states,
- add explicit pressed feedback to Library primary/secondary actions,
- add explicit pressed feedback to Library icon actions,
- add explicit pressed feedback to category tabs,
- add restrained pressed overlays to Continue Watching and poster cards,
- preserve existing 48dp interaction targets and accessibility semantics.

Explicitly excluded and unchanged:

- Library fullscreen player composition and controls,
- `PlaybackController`, playback session and video-target ownership,
- Live UI/state machine and PiP ownership,
- Settings and source-management screens,
- provider/network/catalog semantics,
- Room schema/data persistence,
- download state machine and offline integrity rules,
- package/application ID,
- versionCode/versionName,
- signing configuration,
- release/publication/deployment configuration.

## Implementation

### `LibraryActions.kt`

- `LibraryActionButton` now uses an explicit `MutableInteractionSource` and animated container feedback. Primary actions move from `AccentStrong` to `Accent` while pressed; secondary actions receive a restrained elevated-surface press state. Generic ripple indication is suppressed to keep OwnPlay's custom visual language.
- `LibraryIconAction` retains the 48dp touch target and current visual size while adding restrained pressed container feedback.
- `LibraryShelfHeader` now supports `prominent`. Continue Watching uses the prominent 21sp/25sp hierarchy; regular shelves use 18sp/22sp. Count labels move to smaller muted metadata.
- `LibraryShelfState` is a compact, borderless home-shelf state using a 2dp rail rather than a full panel/card. Loading uses a restrained accent rail; empty states use the divider language.
- `LibraryFilterTab` keeps its 48dp target and selected underline, but selected text is white hierarchy with the blue accent reserved for the underline. Pressed unselected text receives restrained feedback.

### `LibraryShell.kt`

- Home shelf internal vertical spacing is reduced to 8dp while existing inter-shelf spacers remain, producing tighter header-to-content grouping without collapsing section separation.
- Continue Watching is marked prominent.
- Home shelf states for Library loading, no active source, empty Continue Watching, empty/filtered Movies, empty/filtered Series, and empty Downloaded Media now use `LibraryShelfState`.
- The existing top-level error panel remains unchanged.
- Continue Watching cards use an explicit interaction source/no generic ripple and a restrained black pressed overlay.
- Movie/Series poster cards use the same custom pressed-overlay language.
- Stage 17A responsive width, edge-peek, snapping and category auto-scroll behavior remains intact.

## Changed-file boundary

The validated source implementation commit differs from Stage 17A in exactly two source files:

1. `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryActions.kt`
2. `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt`

No player, Live, Settings, repository, schema, build, signing, version or release file is changed by the source implementation commit.

## Validation provenance

### Helper v1

- Run: `34690885701`
- Job: `103545778966`
- Result: failed during deterministic patch matching before JDK/SDK/build setup and before any target-branch push.
- Cause: incorrect indentation assumption for the nested Library home column.
- Target branch remained at the exact Stage 17A baseline.
- No APK/AAB was generated.

### Helper v2

- Run: `34690953887`
- Job: `103545963108`
- Result: failed during deterministic patch matching before JDK/SDK/build setup and before any target-branch push.
- Cause: incorrect indentation assumption in the loading-state insertion signature.
- Target branch remained at the exact Stage 17A baseline.
- No APK/AAB was generated.

### Corrected helper v3

- Run: `34691041976`
- Job: `103546204051`
- Baseline checkout verified exact SHA `3b9024e9df7a6d92f9595b72cd1d52f7fe32995d` and clean state.
- Deterministic patch step passed.
- Focused commands passed:
  - `:app:compileDebugKotlin`
  - `:app:compileDebugUnitTestKotlin`
  - `:app:testDebugUnitTest`
  - `:app:lintDebug`
- Result: `BUILD SUCCESSFUL in 3m 24s`.
- Tasks: `34 actionable tasks: 34 executed`.
- Explicit helper no-APK/AAB guard passed before commit/push.
- Exact changed-file guard passed for the two intended Library files only.
- Validated source commit pushed to target: `b0e7b3436ed100c2f99d64056664907d3501ff86`.

Existing non-blocking warnings remained:

- `DownloadRepositoryImpl.kt:363` — Elvis operator always returns the left operand.
- `LibraryShell.kt:510` — unnecessary safe call.
- `LibraryShell.kt:1044` — unnecessary safe call.

These warnings are not introduced as blocking failures by Stage 17B.

## Acceptance boundary

Stage 17B source validation can establish compilation/test/lint correctness and scope containment. It cannot establish real-device visual fidelity, spacing feel, pressed-state perception, contrast under actual display conditions, or physical touch behavior.

Therefore:

- Stage 17B focused source validation: **PASS**.
- Stage 17B standard exact-final-HEAD source validation: **PENDING at audit creation**.
- APK/AAB: **NOT AUTHORIZED / NOT GENERATED**.
- Physical Library hierarchy/spacing/pressed-state QA: **NOT_YET_VERIFIED**.
- Merge / ready-for-review / release / deployment: **NOT AUTHORIZED**.
