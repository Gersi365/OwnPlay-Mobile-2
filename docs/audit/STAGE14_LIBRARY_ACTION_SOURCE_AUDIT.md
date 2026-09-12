# Stage 14 Library action source audit

Base source: `4c2494b233b8718df30e60f630a3236cef51d24b` (`stage-13-visual-system-refactor`)

## User-directed scope

Refine button style and size outside the player in Library without changing player geometry or playback behavior.

## Source changes

- Added Library-local action primitives with 48dp interactive targets and 40dp visual chrome.
- Resume/Play remains the dominant blue action; Start Over is visually secondary and narrower in Continue Watching.
- Added small decorative play/restart glyphs excluded from accessibility semantics so spoken labels remain clean.
- Replaced Library category chip presentation with text-led 48dp targets and a restrained 2dp selected underline.
- Applied the same Library-local Play/Resume/Start Over hierarchy to movie and episode details.
- Existing download controls remain behaviorally and structurally unchanged.

## Explicitly unchanged

- fullscreen player and player glass chrome
- PlaybackController/session/surface ownership
- Live UI and Live Preview/fullscreen state machine
- provider parsing/refresh logic
- Room/schema/data migration
- downloads state machine and storage
- Settings and source-management screens
- signing, packaging, version metadata, release/deploy configuration

## Acceptance boundary

Source validation is necessary but not sufficient. Physical-device comparison is still required before visual acceptance.
No APK/AAB generation is authorized by this source-only stage.
