# Stage 32 — Interaction & Orientation Contract

Source-only hardening from Stage 31 exact HEAD `5fab72511e11033778733f63b8bc8932e3e87565`.

## Corrected scope

- provider-category swipes keep the active category chip visible in the horizontal strip
- Live channel browse rows no longer show numeric channel indices
- Android system Auto-rotate OFF: physical rotation does not drive OwnPlay presentation; explicit playback actions remain authoritative
- Android system Auto-rotate ON + Live Preview: a stable physical landscape dwell enters fullscreen through the existing Live presentation reducer, preserving the same playback session
- Android system Auto-rotate ON + Live fullscreen: stable landscape confirmation followed by stable portrait returns to Preview without routing through generic Activity Back handling
- the orientation classifier uses 500 ms dwell plus dead-zone hysteresis to reject ordinary hand jitter
- Live and Library/VOD/offline no longer share one Boolean-only fullscreen orientation policy
- Library/VOD/episode/offline playback remains fullscreen when rotating to portrait; with Auto-rotate ON the Activity follows full-sensor orientation without closing playback
- PiP suspends rotation-driven presentation transitions and resets orientation latches across PiP ownership changes
- empty provider categories retain the designed OwnPlay empty state and now expose the same horizontal category-swipe gesture, with concise swipe guidance
- playback Options owns Back before its parent player, so Back dismisses Options rather than exiting playback
- the Options surface remains OwnPlay glass styling and is responsive (`86%` width, capped at `320dp`) for portrait and landscape layouts
- focused unit coverage now includes stable Live Preview landscape entry as well as fullscreen exit sequencing

## Presentation invariants

- Live Browse without Preview does not auto-enter fullscreen from rotation
- Live Preview -> fullscreen may be driven by stable landscape only when Android Auto-rotate is enabled
- Live fullscreen -> Preview may be driven by stable portrait only when Android Auto-rotate is enabled and the landscape latch has armed
- Library/VOD/episode/offline playback has no Preview transition; portrait rotation must not invoke Back or close playback
- one player / one active video target remains unchanged
- Live Preview retains no visible transport controls
- Live fullscreen retains the transient OwnPlay EPG/channel overlay instead of generic player chrome
- no stock Material dialog or generic preference-style surface was introduced by this correction

## Explicitly deferred

The following playback enhancements are intentionally not folded into this correction stage so physical QA can isolate the reported regressions:

- touch lock
- aspect-ratio selector
- stream diagnostics / stream-info panel
- Live transport-format preference
- adaptive quality selector
- preferred audio/subtitle language
- broader smart-retry policy

## Data / release invariants

- no Room entity/schema/version changes
- no backup-format changes
- no provider/source-model changes
- no signing, release, deployment, or merge action
- no APK/AAB generation is authorized by this source correction

## Validation evidence

Code checkpoint `72209d7105d02ba077ecca1fd911091146f81eed` passed GitHub Actions run `34764125016`, job `103741989189`, using `bash tools/validate-source-no-apk.sh`:

- debug source compile: PASS
- unit tests: PASS
- lint: PASS
- committed Room schema guard: PASS
- APK/AAB absence guard: PASS

The build emitted two pre-existing non-blocking Kotlin Elvis warnings in `DownloadRepositoryImpl.kt` and `CustomGroupManagementScreen.kt`; neither warning is introduced by the Stage 32 correction.

This audit-document commit must also receive the standard exact-head source-only validation before Stage 32 is reported as final SOURCE PASS. Physical orientation timing, OEM Auto-rotate behavior, gesture ergonomics, PiP transitions, portrait Library playback, and visual acceptance remain PHYSICAL QA / NOT_YET_VERIFIED until an explicitly authorized QA APK is tested on-device.

## Audit note

During connector inspection an empty technical probe file named `__never__` was accidentally created and immediately removed by a forward-only cleanup commit. It has no net PR diff and no product/source effect; no reset or force-push was used.
