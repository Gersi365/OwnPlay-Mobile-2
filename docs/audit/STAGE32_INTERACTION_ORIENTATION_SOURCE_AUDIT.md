# Stage 32 — Interaction, Orientation & Playback Controls Contract

Source-only hardening from Stage 31 exact HEAD `5fab72511e11033778733f63b8bc8932e3e87565`.

## Corrected interaction and orientation scope

- provider-category swipes keep the active category chip visible in the horizontal strip
- Live channel browse rows no longer show numeric channel indices
- Android system Auto-rotate OFF: physical rotation does not drive OwnPlay presentation; explicit playback actions remain authoritative
- Android system Auto-rotate ON + Live Preview: a stable physical landscape dwell enters fullscreen through the existing Live presentation reducer, preserving the same playback session
- Android system Auto-rotate ON + Live fullscreen: stable landscape confirmation followed by stable portrait returns to Preview without routing through generic Activity Back handling
- the orientation classifier uses 500 ms dwell plus dead-zone hysteresis to reject ordinary hand jitter
- Live and Library/VOD/offline do not share one Boolean-only fullscreen orientation policy
- Library/VOD/episode/offline playback remains fullscreen when rotating to portrait; with Auto-rotate ON the Activity follows physical orientation without closing playback
- PiP suspends rotation-driven presentation transitions and resets orientation latches across PiP ownership changes
- empty provider categories retain the designed OwnPlay empty state and expose the same horizontal category-swipe gesture, with concise swipe guidance
- playback Options owns Back before its parent player, so Back dismisses Options rather than exiting playback
- the Options surface remains OwnPlay glass styling and is responsive (`86%` width, capped at `320dp`) for portrait and landscape layouts
- focused unit coverage includes stable Live Preview landscape entry as well as fullscreen exit sequencing

## Fullscreen playback controls and interaction ownership

- fullscreen Touch Lock is shared across Live and Library playback through the player interaction environment
- activating `Lock touch` dismisses Options and blocks the fullscreen interaction layer from accidental taps/gestures while leaving a compact OwnPlay glass unlock affordance available
- Back while Touch Lock is active unlocks touch first instead of leaving playback
- Touch Lock is cleared when playback leaves fullscreen and when the Activity enters PiP; the lock overlay is never rendered over PiP
- Live `Options` is always reachable in fullscreen, including streams that expose no selectable audio or subtitle tracks, because Picture, Touch Lock, and Stream Info do not depend on track availability
- Live and Library suspend overlay auto-hide while Options is open, so the panel cannot disappear on the normal four-second player-control timer while the user is interacting with it
- Live disables underlying tap, brightness/volume, and horizontal channel gestures while Options owns interaction
- picture presentation modes are available as `Fit`, `Fill`, and `Zoom`
- picture-mode changes alter only video presentation; they do not reload media, create another player, or transfer ownership to another playback session
- the shared `PlaybackOptionsPanel` obtains the active playback controller and interaction state from the centralized player composition environment, so Live and Library use the same Picture / Touch Lock / Stream Info behavior without duplicate player/control plumbing

## Stream diagnostics and format handling

- Stream Info reads from the active Media3 playback state rather than provider display labels
- Stream Info exposes available video resolution, frame rate, video codec/bitrate, audio codec, channel count, and sample rate
- an explicitly known or safely inferred HLS source is displayed as `HLS`; unresolved `AUTO` is displayed as `Auto`, not the misleading `Auto-detected`
- Stream Info does not display media URLs, usernames, passwords, tokens, or other provider credentials
- `.m3u8` paths and explicit HLS query hints are conservatively inferred as HLS and passed to Media3 with `MimeTypes.APPLICATION_M3U8`
- a truly extensionless HTTP(S) `AUTO` stream gets one controlled format-recovery attempt as HLS only when Media3 reports `UnrecognizedInputFormatException`
- that opaque-stream recovery is one-shot format disambiguation, not a general retry policy; known `.ts`, `.mp4`, local/offline URIs, explicit HLS, and non-format playback failures are not reclassified
- the recovery remains inside the same Media3 player/session and does not create another video surface or expose the stream URI in logs

## PiP and lifecycle hardening

- PiP aspect ratio now follows the active renderer video dimensions when they are valid and within Android platform ratio bounds; `16:9` remains the safe fallback before dimensions are known or for invalid/extreme values
- renderer `VideoSize` is retained separately from track-format metadata so PiP geometry prefers the actual active output dimensions
- entering PiP clears Touch Lock presentation state before the normal fullscreen surface is restored later
- destination changes away from an active playback destination await `stop(clearMedia = true)` before mounting the next destination, removing the old-stop/new-load race where a delayed stop could clear freshly loaded media

## Presentation invariants

- Live Browse without Preview does not auto-enter fullscreen from rotation
- Live Preview -> fullscreen may be driven by stable landscape only when Android Auto-rotate is enabled
- Live fullscreen -> Preview may be driven by stable portrait only when Android Auto-rotate is enabled and the landscape latch has armed
- Library/VOD/episode/offline playback has no Preview transition; portrait rotation must not invoke Back or close playback
- one player / one active video target remains unchanged
- Touch Lock must not create a second player, media session, or video surface
- Touch Lock changes interaction ownership only; media playback continues in the same session
- Fit / Fill / Zoom change the active video surface presentation only
- opaque HLS format recovery reuses the same player/session and is limited to one attempt per load
- Live Preview retains no visible transport controls
- Live fullscreen retains the transient OwnPlay EPG/channel overlay instead of generic player chrome
- player options retain the OwnPlay dark-glass / blue-accent visual language; no stock Material dialog or generic preference-style surface is introduced

## Explicitly deferred

The following playback enhancements remain separate work and are not required for the Stage 32 correction contract:

- user-selectable Live transport preference (`Prefer MPEG-TS` / `Prefer HLS`)
- adaptive video-quality selection
- preferred audio/subtitle language
- broader smart-retry / reconnect policy beyond the one-shot opaque-format disambiguation above

## Data / release invariants

- no Room entity/schema/version changes
- no backup-format changes
- no provider/source-model changes
- no signing, release, deployment, or merge action
- no APK/AAB generation is authorized by this source stage

## Validation evidence

Final code checkpoint `0df9fc68fb5e964251a4079a1dd6977b53874895` passed GitHub Actions run `34767945269`, job `103752224247`, using `bash tools/validate-source-no-apk.sh`:

- exact source checkout: PASS
- debug source compile: PASS
- unit tests: PASS
- lint: PASS
- committed Room schema guard: PASS
- APK/AAB absence guard: PASS

The build emitted the same two pre-existing non-blocking Kotlin Elvis warnings in `DownloadRepositoryImpl.kt` and `CustomGroupManagementScreen.kt`; neither warning is introduced by the Stage 32 interaction/orientation/playback-control work.

This audit-document commit must also receive the standard exact-head source-only validation before Stage 32 is reported as final SOURCE PASS.

Physical QA remains `NOT_YET_VERIFIED` for orientation timing and OEM Auto-rotate behavior, empty-category gesture ergonomics, PiP transitions and dynamic PiP geometry, portrait Library playback, Touch Lock input leakage/ergonomics, Options interaction behavior, Fit/Fill/Zoom crop and scaling behavior, Stream Info accuracy against real provider streams, one-shot opaque-HLS recovery against a real provider endpoint, and final visual acceptance. Those checks require an explicitly authorized QA APK and on-device testing.

## Audit note

During connector inspection an empty technical probe file named `__never__` was accidentally created and immediately removed by a forward-only cleanup commit. It has no net PR diff and no product/source effect; no reset or force-push was used.
