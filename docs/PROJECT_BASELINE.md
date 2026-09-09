# OwnPlay Mobile 2 — Project Baseline

## Purpose

This document records stable implementation decisions for the clean-room OwnPlay Mobile 2 project. Volatile repository state such as current HEAD, CI run IDs, branch status, and temporary defect queues must be read live from GitHub rather than stored here.

## Product identity

- Product: `OwnPlay`
- Repository: `Gersi365/OwnPlay-Mobile-2`
- Application ID baseline: `app.ownplay.mobile`
- Platform scope: Android phones and tablets
- Minimum Android baseline: `minSdk 26`
- Primary destinations: `Live`, `Library`, `Settings`
- Downloads remain contextual to Library/Settings and are not a fourth primary destination.

## Clean-room boundary

The implementation is designed and written from zero.

Previous OwnPlay repositories must not be used as implementation sources for code, architecture, package structures, resources, database schemas, migrations, build scripts, tests, CI, signing configuration, backup formats, or UI geometry.

Historical behavior may be implemented only when it is independently defined by the current OwnPlay Mobile 2 product authority or explicitly re-approved by the user.

## Authority precedence

When requirements conflict, use this order:

1. Latest explicit user decision.
2. User-approved visual reference images in the OwnPlay Mobile 2 Project.
3. `02_PRODUCT_SOURCE_AUTHORITY.md` from the Project source pack.
4. `03_VISUAL_REFERENCE_CONTRACT.md` from the Project source pack.
5. Verified current state of `Gersi365/OwnPlay-Mobile-2`.
6. Focused current QA evidence.
7. `04_ENGINEERING_WORKFLOW.md` for process.

Conflicts must not be silently reconciled.

## Technology baseline

- Language: Kotlin
- UI: Jetpack Compose
- Android UI infrastructure: Material 3 with an OwnPlay-specific design system above defaults
- Playback: Android Media3 / ExoPlayer with HLS support
- Structured persistence: Room, starting at schema version 1
- Preferences: DataStore
- Background work: WorkManager
- Networking: OkHttp
- Concurrency/state streams: Kotlin coroutines and Flow

Dependency versions and exact compile/target SDK values are resolved during repository bootstrap and remain authoritative in source/build configuration.

## Package direction

Root package:

```text
app.ownplay.mobile
```

Feature-oriented boundaries should cover, as needed:

```text
app/
design/
core/
data/
sources/
live/
library/
playback/
downloads/
settings/
```

Layers are introduced only when they add concrete separation or testability. Do not create ceremonial abstractions.

## Playback invariants

OwnPlay playback architecture must preserve:

- one playback controller/player;
- one active video target;
- explicit release/transfer of the previous target;
- Preview ↔ fullscreen continuity for the same Live channel;
- no duplicate audio;
- no stale video surface;
- no audio-only PiP;
- presentation transitions that do not create new playback sessions by default.

## Persistence and security invariants

- Room starts at version 1 for this new application.
- Provider-owned metadata and local personalization remain separate data layers.
- Provider refresh must preserve supported personalization and stable media identities.
- Credentials are secrets and must not be persisted as plaintext Room fields.
- Credentials must not appear in logs, ordinary personalization backups, analytics, screenshots, or debug dumps.
- Destructive migration is not a normal recovery shortcut.

## Visual baseline

The approved Project images are implementation targets for hierarchy, density, spacing relationships, dominant geometry, content-to-chrome ratio, navigation placement, and visual emphasis.

OwnPlay should be dark, cinematic, artwork-led, low-noise, spacious, touch-friendly, and visually original. Generic Material layouts, legacy IPTV utility styling, and legacy OwnPlay visual language are not acceptable substitutions when an approved reference exists.

## Validation baseline

After every meaningful source stage:

- run focused unit tests;
- compile Kotlin/source targets;
- run lint;
- compile Android tests when materially applicable;
- run source-only/no-APK validation;
- explicitly verify that no APK was produced;
- verify exported Room schema when Room exists;
- report validation only for the exact final HEAD.

Source/CI PASS never implies physical-device PASS.

## Restricted actions

Explicit user approval is required before merge, ready-for-review transition, release, publication, deployment, QA APK generation, signing changes, force-push, reset, rebase, history rewrite, destructive database migration, user-data deletion, authentication/account cutover, breaking backup-format change, or replacement of an approved architecture baseline.

## Physical QA boundary

Physical-device validation is mandatory where materially relevant for video rendering, Preview/fullscreen transfer, PiP, background/foreground transitions, rotation, provider/network behavior, Resume position, downloads, touch ergonomics, perceived performance, and visual comparison with approved references.
