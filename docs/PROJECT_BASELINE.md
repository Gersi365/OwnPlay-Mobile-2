# OwnPlay — Durable Project Baseline

This document contains only stable decisions required to continue development safely. Current branch names, commit SHAs, CI run IDs, QA candidate numbers, and temporary defect status must be read live from GitHub.

## Product identity

- Product: `OwnPlay`
- Repository: `Gersi365/OwnPlay-Mobile-2`
- Application ID: `app.ownplay.mobile`
- Platform: Android phones and tablets, `minSdk 26`
- Primary destinations: `Live`, `Library`, `Settings`
- Downloads are contextual to Library/Settings, not a fourth primary destination.
- OwnPlay plays user-provided legitimate sources and does not provide media.

## Clean-room boundary

OwnPlay Mobile 2 is implemented independently from zero. Previous OwnPlay repositories are not implementation sources for code, architecture, resources, database schemas, migrations, build scripts, tests, CI, signing, backup formats, or UI geometry.

## Playback and presentation invariants

- One playback controller/player owns playback.
- Exactly one active video target is bound at a time.
- Presentation changes transfer surface ownership; they do not create a second playback session.
- Same-channel Live Preview ↔ fullscreen preserves playback continuity.
- Live Preview has no visible transport controls.
- Live is the only playback area with a distinct Preview state that may remain portrait outside fullscreen.
- **All fullscreen/fullview playback is landscape-only**, including Live, Movie/VOD, Series/Episode, and offline playback.
- With Android Auto-rotate enabled, fullscreen may follow normal/reverse landscape but must never render portrait fullscreen.
- Library and offline playback have no Preview state.
- PiP is a presentation destination, not a second player/session.
- No duplicate audio, stale surface, black-video ownership leak, or audio-only PiP is acceptable.

## Data and source invariants

- Supported source families: Xtream-compatible providers and M3U/M3U8 playlists.
- Provider refresh must preserve supported local personalization and stable identities.
- Continue Watching and playback progress are first-class persisted behavior.
- Play from Beginning starts at zero without destroying saved Resume progress before playback successfully starts.
- Completed offline playback requires integrity-verified download data.
- Paused downloads remain paused until explicit Resume.
- Room migration history belongs only to this clean-room application; future schema changes require explicit migrations rather than destructive-reset shortcuts.

## Security invariants

- Credentials are secrets.
- Plaintext provider credentials must not be persisted in Room.
- Credentials must not appear in ordinary logs, personalization backups, analytics, screenshots, or debug dumps.
- Signing keys, keystores, and signing passwords remain outside the public repository.
- Network/playback diagnostics must avoid exposing media URLs, credentials, or provider tokens.

## UI invariants

OwnPlay is dark, cinematic, media-first, artwork-led, low-noise, and touch-friendly. Material 3 is infrastructure, not the visual identity. Primary screens and playback chrome should remain OwnPlay-specific rather than generic Material or legacy IPTV utility UI.

## Engineering method

- Audit before mutation: verify authoritative branch/ref, exact HEAD, execution path, scope, and restricted-action boundaries.
- Prefer small forward-only commits and Draft PRs.
- Keep business rules in testable policies/reducers where practical rather than burying them in Composables.
- Avoid unrelated refactors and premature abstraction.
- Label defect conclusions as verified cause, strong evidence, hypothesis, or unknown physical behavior.

## Validation contract

After meaningful source changes:

- compile source;
- run focused/unit tests;
- run lint;
- verify committed Room schemas;
- run the no-APK validation gate;
- explicitly verify that no APK/AAB was produced;
- report validation only for the exact final HEAD.

Source/CI PASS is not physical-device PASS.

## Restricted actions

Explicit user approval is required before merge, ready-for-review transition, release/publication/deployment, QA APK generation, signing changes, force-push/reset/rebase/history rewrite, destructive database migration, user-data deletion, authentication cutover, breaking backup-format change, or replacement of an approved architecture baseline.
