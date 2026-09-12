# Stage 17C — Library Detail Polish Source Audit

## Scope

Stage 17C is a source-only presentation pass over the Movie and Series detail surfaces in Library. It follows the Stage 17B Library hierarchy/interaction pass and keeps playback, data, provider and navigation architecture unchanged.

This stage does not authorize or generate an APK/AAB. Source validation is not physical-device visual acceptance.

## Provenance

- Repository: `Gersi365/OwnPlay-Mobile-2`
- Base branch: `stage-17b-library-hierarchy-polish`
- Exact Stage 17B base HEAD: `48b9f8b60c7d61962232463f8826eaa74cc663c6`
- Stage 17C branch: `stage-17c-library-detail-polish`
- Stage 17C source implementation commit: `c8ec9ec7fef238e0c7f3b37a7f9a505426e8f797`
- Source commit parent: `48b9f8b60c7d61962232463f8826eaa74cc663c6`
- Source commit message: `Polish Library detail hierarchy`

The implementation commit is a direct forward-only child of the exact Stage 17B final HEAD.

## Read-only findings before mutation

The Stage 17B detail surfaces still had several presentation patterns that were heavier than the Library home direction:

- Movie and Series detail bodies used larger vertical padding than needed beneath the artwork hero.
- Detail error/warning/loading states still used the heavier generic state-panel treatment instead of the borderless Library-local state language introduced in Stage 17B.
- The detail hero was taller than needed for a cinematic backdrop-first composition.
- Series episode rows still read as repeated translucent cards with a dedicated season/episode block, creating visual weight and card-inside-card pressure.
- Episode-list metadata did not expose a restrained total count in the section header.

The player, playback ownership, offline/download behavior, source/provider semantics and persistent data contracts were outside this pass.

## Source changes

Only this implementation file changed before this audit document:

- `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt`

### Movie detail

- Reduced detail-body vertical padding from `OwnPlaySpacing.Lg` to `OwnPlaySpacing.Md`.
- Replaced the heavier `OwnPlayStatePanel` action-error treatment with Library-local borderless `LibraryShelfState`.
- Increased the standalone no-resume Play CTA width from 44% to 52% of available width for a clearer primary action without making it full-width.
- Reduced the trailing detail spacer from `Xl` to `Lg`.

### Series detail

- Reduced body vertical padding from `Lg` to `Md`.
- Capped the series description at four lines to keep episode content above the fold more consistently.
- Replaced cached-warning and action-error panels with `LibraryShelfState`.
- Added quiet episode-count metadata to the Episodes section header when episodes are available.
- Replaced episode loading/empty panels with Library-local borderless state rows; loading uses the existing restrained accent rail.
- Reduced episode-stack spacing from 8dp to 4dp.
- Removed an unnecessary nullable safe-call in the populated episode branch by using the already-established non-null smart-cast.
- Reduced the trailing detail spacer from `Xl` to `Lg`.

### Detail hero

- Changed the artwork hero aspect ratio from `1.55f` to `1.72f` so the hero is shorter and more cinematic while remaining artwork-led.
- Kept the back action's 48dp interaction target while reducing its visual chrome to 36dp.

### Episode hierarchy

- Removed the repeated translucent episode-card fill; episode rows now sit on a transparent media-list surface.
- Replaced the old fixed-width season/episode block with a restrained 2dp x 36dp vertical state rail.
- Progress-bearing episodes use a subdued accent rail; untouched episodes use the divider language.
- Combined season/episode metadata into one compact `Sx • Ex` line and placed duration inline.
- Kept the episode title directly below the compact metadata line.
- Reduced the emphasized Play/Resume visual control to 36dp while preserving the existing 48dp hit target through `LibraryIconAction`.
- Preserved Resume/Start Over semantics and download controls; the secondary progress action remains present with a slightly rebalanced row weight.

## Explicitly unchanged

Stage 17C does not change:

- `PlaybackController`, player session ownership, video-target ownership, seek/pause/audio behavior or fullscreen player chrome
- Live UI, Live reducer/state machine, Preview behavior, fullscreen transitions or PiP ownership
- Settings, source-management or backup UI
- Xtream/M3U/M3U8 parsing, provider/network/catalog semantics or refresh behavior
- Room entities, migrations, schemas or persistence contracts
- download state machine, WorkManager behavior, file-integrity rules or offline playback resolution
- package name, version code/name, signing configuration or signing identity
- release, publication or deployment configuration

## Helper validation

An isolated helper workflow applied the deterministic patch from the exact Stage 17B base and validated it before pushing the source commit.

- Helper branch: `stage-17c-library-detail-helper`
- Helper workflow commit: `e8b3935a5b075ac58581e0c595d9f648e8898d97`
- Helper Run: `34691745668`
- Helper Job: `103548097790`
- Result: `SUCCESS`
- Exact baseline checkout: `48b9f8b60c7d61962232463f8826eaa74cc663c6`
- Scope guard: only `LibraryShell.kt` changed before commit
- `compileDebugKotlin`: PASS
- `compileDebugUnitTestKotlin`: PASS
- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- Build: `BUILD SUCCESSFUL in 3m 48s`
- Gradle tasks: `34 actionable tasks: 34 executed`
- Room schema verification: PASS; generated schemas match committed HEAD and schema tree is clean
- APK/AAB guard: PASS; no APK/AAB produced

Non-blocking compiler warnings observed in the helper validation:

- `DownloadRepositoryImpl.kt:363`: Elvis operator always returns the left operand
- `LibraryShell.kt:510`: unnecessary safe call on a non-null `LibraryCatalog`

The prior detail-branch nullable warning is no longer present after the populated episode branch was made non-null through control-flow smart-casting.

## Acceptance boundary

Stage 17C is a source-level PASS only after the repository's standard exact-final-HEAD source-validation workflow also succeeds on the audit-inclusive final commit.

Physical-device acceptance remains `NOT_YET_VERIFIED` for:

- Movie detail hero proportion and CTA hierarchy
- Series description density and Episodes header hierarchy
- episode-row density, state rail readability and action balance
- warning/loading/empty-state perception on real devices
- portrait and landscape behavior across representative phone widths

No APK/AAB is authorized or generated by this stage. A future QA APK requires separate explicit user authorization under the project workflow.
