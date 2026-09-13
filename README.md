# OwnPlay

OwnPlay is a clean-room Android media player and playlist organizer for user-provided media sources. OwnPlay does not provide, host, or sell media.

## Features

- Live TV browsing with channel categories, EPG where available, Preview, fullscreen playback, and Picture-in-Picture
- Library browsing for Movies/VOD, Series/Episodes, Continue Watching, and completed offline media
- Xtream-compatible providers and M3U/M3U8 playlists
- Favorites, local personalization, custom Live groups, search, and provider refresh
- Managed downloads with pause/resume/retry/remove and integrity-checked offline playback
- Personalization backup/restore without ordinary credential export
- Custom OwnPlay playback controls, subtitles, audio-track selection, picture modes, and stream information

## Platform and stack

- Application ID: `app.ownplay.mobile`
- Android: `minSdk 26`, compile/target SDK 36
- Kotlin + Jetpack Compose
- Android Media3 / ExoPlayer
- Room + DataStore
- WorkManager
- OkHttp
- Kotlin coroutines and Flow

The UI is intentionally dark, cinematic, media-first, and OwnPlay-specific rather than a generic IPTV utility interface.

## Build and source validation

Development validation uses JDK 17, Android SDK 36 / Build Tools 36.0.0, and Gradle 9.6.0.

Run the source-only validation gate with:

```bash
bash tools/validate-source-no-apk.sh
```

That gate compiles the debug source, runs unit tests and lint, verifies committed Room schemas, and fails if an APK or AAB is produced. APK generation is intentionally separate from routine source validation.

## Repository layout

```text
app/                         Android application, tests, Room schemas, bundled FFmpeg audio decoder
.github/workflows/           Source-only CI validation
docs/PROJECT_BASELINE.md     Durable product/engineering invariants
docs/third_party/            Third-party provenance and licensing notes
tools/                       Reproducible validation and FFmpeg build tooling
```

## Security

Provider credentials are treated as secrets and are stored through the app's credential-store boundary rather than plaintext Room fields. Credentials, signing keys, keystores, private certificates, and local secret-property files must never be committed to this repository.

## FFmpeg audio fallback

OwnPlay includes a pinned Media3 FFmpeg **audio-only** decoder AAR for formats that are not supported by the device MediaCodec stack. Provenance, rebuild inputs, and licensing notes are documented in [`docs/third_party/FFMPEG_AUDIO_DECODER.md`](docs/third_party/FFMPEG_AUDIO_DECODER.md).

## Project status

Repository and CI state are read directly from GitHub. Volatile HEADs, workflow run IDs, QA checkpoints, and stage-by-stage audit snapshots are intentionally not stored as durable documentation.
