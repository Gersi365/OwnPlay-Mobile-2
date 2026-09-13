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

## Included fullscreen playback enhancements

- fullscreen Touch Lock is shared across Live and Library playback through the player interaction environment
- activating `Lock touch` dismisses Options and blocks the fullscreen interaction layer from accidental taps/gestures while leaving a compact OwnPlay glass unlock affordance available
- Back while Touch Lock is active unlocks touch first instead of leaving playback
- picture presentation modes are available as `Fit`, `Fill`, and `Zoom`
- picture-mode changes alter only video presentation; they do not reload media, create another player, or transfer ownership to another playback session
- Stream Info is informational and reads from the active Media3 playback state rather than provider labels or guessed metadata
- Stream Info exposes the detected/requested stream format plus available video resolution, frame rate, video codec/bitrate, audio codec, channel count, and sample rate
- Stream Info does not display media URLs, usernames, passwords, tokens, or other provider credentials
- the shared `PlaybackOptionsPanel` obtains the active playback controller and interaction state from the centralized player composition environment, so Live and Library use the same behavior without duplicate player/control plumbing

## Presentation invariants

- Live Browse without Preview does not auto-enter fullscreen from rotation
- Live Preview -> fullscreen may be driven by stable landscape only when Android Auto-rotate is enabled
- Live fullscreen -> Preview may be driven by stable portrait only when Android Auto-rotate is enabled and the landscape latch has armed
- Library/VOD/episode/offline playback has no Preview transition; portrait rotation must not invoke Back or close playback
- one player / one active video target remains unchanged
- Touch Lock must not create a second player, media session, or video surface
- Touch Lock changes interaction ownership only; media playback continues in the same session
- Fit / Fill / Zoom change the active video surface presentation only
- Live Preview retains no visible transport controls
- Live fullscreen retains the transient OwnPlay EPG/channel overlay instead of generic player chrome
- player options retain the OwnPlay dark-glass / blue-accent visual language; no stock Material dialog or generic preference-style surface is introduced

## Explicitly deferred

The following playback enhancements remain separate work so they do not expand this physical-QA boundary:

- Live transport-format preference (`Prefer MPEG-TS` / `Prefer HLS`)
- adaptive video-quality selection
- preferred audio/subtitle language
- broader smart-retry policy

## Data / release invariants

- no Room entity/schema/version changes
- no backup-format changes
- no provider/source-model changes
- no signing, release, deployment, or merge action
- no APK/AAB generation is authorized by this source stage

## Validation evidence

Code checkpoint `114c364d48a2e40d33fd39fddfb4f2f5527e7318` passed GitHub Actions run `34765789891`, job `103746392510`, using `bash tools/validate-source-no-apk.sh`:

- debug source compile: PASS
- unit tests: PASS
- lint: PASS
- committed Room schema guard: PASS
- APK/AAB absence guard: PASS

The build emitted the same two pre-existing non-blocking Kotlin Elvis warnings in `DownloadRepositoryImpl.kt` and `CustomGroupManagementScreen.kt`; neither warning is introduced by the Stage 32 interaction/orientation/playback-control work.

This audit-document commit must also receive the standard exact-head source-only validation before Stage 32 is reported as final SOURCE PASS.

Physical QA remains `NOT_YET_VERIFIED` for orientation timing and OEM Auto-rotate behavior, empty-category gesture ergonomics, PiP transitions, portrait Library playback, Touch Lock input leakage/ergonomics, Fit/Fill/Zoom crop and scaling behavior, Stream Info accuracy against real provider streams, and final visual acceptance. Those checks require an explicitly authorized QA APK and on-device testing.

## Audit note

During connector inspection an empty technical probe file named `__never__` was accidentally created and immediately removed by a forward-only cleanup commit. It has no net PR diff and no product/source effect; no reset or force-push was used.
