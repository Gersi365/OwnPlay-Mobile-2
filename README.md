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

The approved implementation baseline is:

- Kotlin
- Jetpack Compose
- Material 3 as Android UI infrastructure, with an OwnPlay design system above Material defaults
- Android Media3 / ExoPlayer for playback
- Room for structured persistence
- DataStore for preferences
- WorkManager for background work
- OkHttp for networking
- Kotlin coroutines and Flow
- `minSdk 26`

Exact dependency and compile/target SDK versions are resolved during repository bootstrap and validated against the project build.

## Visual direction

OwnPlay is dark, cinematic, media-first, artwork-led, low-noise, and touch-friendly. The user-approved project images are the primary visual source of truth. The implementation must not silently fall back to generic Material or legacy IPTV utility layouts.

## Engineering guardrails

- One playback controller/player and one active video target.
- Provider refresh must preserve supported local personalization.
- Credentials must not be persisted in plaintext Room data or emitted to logs/backups.
- Room starts at schema version 1 for this new application.
- Routine engineering is source-only / NO APK.
- QA APK generation, merge, release, deployment, signing changes, destructive migrations, history rewrites, and other restricted operations require explicit user approval.
- Source validation is valid only for the exact final Git HEAD being reported.

## Repository structure

The Android project and validation workflow are added in the repository bootstrap stage. Feature code is organized around the OwnPlay product domains rather than inherited legacy package structures.
