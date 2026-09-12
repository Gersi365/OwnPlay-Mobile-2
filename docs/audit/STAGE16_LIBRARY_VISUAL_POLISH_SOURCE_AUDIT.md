# Stage 16 — Library Visual Polish Source Audit

## Baseline and trigger

- Baseline: Stage 15 final HEAD `d98d92710f76b6466f93071df8184d8909c2e98b`.
- Physical evidence: QA v22 Library Home screenshot supplied by the user.
- Physical assessment of the Stage 15 Library Home: materially improved / directionally PASS. The user explicitly reported that it looked better.
- Remaining work was therefore constrained to polish, not another structural redesign.

## Scope

This stage is Library-only presentation polish. It does not change playback behavior, the Library fullscreen player, Live, providers/networking, Room/schema, downloads state, Settings, signing, versioning, release, or deployment behavior.

### Continue Watching

- Card width reduced from 292dp to 284dp.
- Artwork ratio changed from 1.72 to 1.78, lowering the hero height by roughly six percent at the new width.
- Inter-card spacing normalized to 12dp so the next item can peek without the hero consuming the shelf.
- Resume control keeps a 48dp interaction target while using a 36dp visual surface.
- Restart keeps a 48dp interaction target while using a 32dp visual surface.
- Metadata padding was tightened to match the smaller controls.

### Shelf hierarchy and counts

- Library shelf titles use a restrained 20sp / 24sp line-height treatment rather than the full global titleLarge size.
- Provider title counts are compacted for large catalogs, for example `13984` -> `13.9K` and `5329` -> `5.3K`.
- Count labels are intentionally quieter than shelf titles.

### Category navigation

- Category tabs preserve a 48dp touch target.
- Horizontal label padding is reduced from 8dp to 6dp.
- Selected underline is 20dp x 2dp.
- Inter-tab spacing is reduced to 2dp.

### Poster metadata

- Poster readability gradient begins earlier and finishes darker: transparent through 52% and 94% black at the bottom.
- Rating eyebrow moves to labelSmall with restrained accent opacity.
- Poster title remains capped at two lines.
- Poster geometry remains borderless and artwork-first.

## Source files changed

- `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryShell.kt`
- `app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryActions.kt`
- this audit document

## Validation

An isolated helper checked out the exact Stage 15 baseline, applied only the two Library UI source changes, and ran source-only validation before pushing the source commit.

- Helper run: `34688944018`
- Helper job: `103540716036`
- Result: SUCCESS
- Validation tasks: `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest`, `lintDebug`
- Explicit guard: no `.apk` or `.aab` files were permitted.
- Source polish commit: `3f4bcb7ad92d86f2afa63d110ea353264aa2d7da`

## Acceptance boundary

Stage 16 is source-only. No APK/AAB is authorized or generated in this stage. Source validation does not establish Stage 16 physical visual acceptance. A future physical build would require a separate explicit APK authorization.
