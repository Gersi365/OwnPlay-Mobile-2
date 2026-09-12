# OwnPlay Mobile 2

OwnPlay Mobile 2 is a clean-room Android media player and playlist organizer for phones and tablets.

## Product identity

- Product name: `OwnPlay`
- Application ID baseline: `app.ownplay.mobile`
- Primary navigation: `Live`, `Library`, `Settings`
- Supported source families: Xtream-compatible providers and M3U/M3U8 playlists
- Media domains: Live, Movies/VOD, Series/Episodes, EPG where available, Continue Watching, managed offline downloads, Picture-in-Picture, and personalization backup/restore

## Clean-room rule

This repository is the implementation authority for OwnPlay Mobile 2 and starts from zero.

Do not copy, port, reconstruct, mechanically adapt, or use implementation material from previous OwnPlay repositories. Product behavior must be implemented independently from the approved OwnPlay Mobile 2 product contracts and visual references.

## Engineering baseline

The initial repository bootstrap uses:

- Kotlin 2.4.20
- Jetpack Compose
- Material 3 as Android UI infrastructure, with an OwnPlay design system to be implemented above Material defaults
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0 in CI
- JDK 17
- `minSdk 26`
- `compileSdk 36`
- `targetSdk 36`
- Compose BOM `2026.08.00`

Android 16 / API level 36 is the current stable Stage 0 SDK baseline. API level 37 remains within AGP 9.4's supported maximum, but real CI validation showed that `platforms;android-37` was not available from the configured stable SDK repository, so it is not used as the build baseline.

Later product stages add Media3 / ExoPlayer, Room, DataStore, WorkManager, OkHttp, coroutines, Flow, source integrations, playback, downloads, and persistence according to the approved project contracts.

## Bootstrap validation

Routine engineering is source-only / NO APK.

The repository validation workflow compiles Kotlin, runs unit tests, runs Android lint, compiles Android-test sources, and fails if an APK is produced.

CI pins Gradle 9.6.0 directly. The standard Gradle wrapper binary remains a Stage 0 tooling item and is not synthesized or copied from an unverified source.

## Visual direction

OwnPlay is dark, cinematic, media-first, artwork-led, low-noise, and touch-friendly. The user-approved project images are the primary visual source of truth. The current bootstrap activity is only a compilation/runtime baseline and is not a visually accepted screen.

## Engineering guardrails

- One playback controller/player and one active video target when playback is introduced.
- Provider refresh must preserve supported local personalization.
- Credentials must not be persisted in plaintext Room data or emitted to logs/backups.
- Room starts at schema version 1 for this new application.
- QA APK generation, merge, release, deployment, signing changes, destructive migrations, history rewrites, and other restricted operations require explicit user approval.
- Source validation is valid only for the exact final Git HEAD being reported.

## Repository structure

```text
.
├── .github/workflows/android-validation-no-apk.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       └── test/
├── docs/BUILD_BASELINE.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```
