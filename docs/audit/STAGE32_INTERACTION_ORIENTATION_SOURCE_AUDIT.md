# Stage 32 — Interaction & Orientation Contract

Source-only hardening from Stage 31 exact HEAD `5fab72511e11033778733f63b8bc8932e3e87565`.

## Scope

- provider category swipe keeps the active category chip visible in the horizontal strip
- Live channel browse rows no longer show numeric channel indices
- fullscreen presentation never switches to unrestricted `FULL_SENSOR`
- explicit fullscreen always requests landscape
- when Android Auto-rotate is OFF, OwnPlay ignores physical orientation changes and exits fullscreen only through explicit Back/controls
- when Android Auto-rotate is ON, physical orientation may auto-exit fullscreen only after a stable landscape latch followed by a stable portrait dwell
- 500 ms dwell + dead-zone hysteresis prevents small hand movements from triggering transitions
- the shared fullscreen orientation rule lives at Activity/playback level and therefore applies to Live, VOD/episodes, and offline playback
- PiP suspends the fullscreen orientation listener and resets its latch on PiP transitions
- Library/offline request landscape before presenting the fullscreen player

## Invariants

- no portrait fullscreen presentation
- no automatic Preview -> fullscreen transition from merely rotating the phone
- Live Preview -> fullscreen remains explicit (same-channel second activation)
- fullscreen -> Preview/details may be automatic only when system Auto-rotate is enabled and the stable orientation sequence is satisfied
- one player / one active video target remains unchanged
- no Room/schema, backup, provider, signing, release, or deployment changes

## Validation boundary

SOURCE PASS requires exact-head compile, unit tests, lint, Room schema guard, and no APK/AAB in standard CI. Physical orientation timing, OEM Auto-rotate behavior, gesture feel, PiP transitions and visual acceptance remain PHYSICAL QA until the explicitly authorized QA APK is tested on-device.
