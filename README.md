# OwnPlay Mobile 2

OwnPlay Mobile 2 is the forward-only rebuild of the Android OwnPlay application.

## Product boundary

OwnPlay is a media player and organizer for user-configured sources. It does not provide media, subscriptions, channels, movies, series, credentials, or provider access.

Supported source families:

- Xtream-compatible sources
- M3U / M3U8 playlists
- EPG where available

Primary application navigation is exactly:

- Live
- Library
- Settings

OwnPlay-generated visible UI text is English.

## Rebuild status

The active application source is being recreated on the `rebuild-foundation` branch and Draft PR #44. Historical implementation remains available through Git history and committed Room schema evidence, but is not the active execution model of the rebuild.

The rebuild preserves:

- application ID `app.ownplay.mobile`
- JDK 17 / Android SDK 36 build baseline
- pinned FFmpeg decoder provenance and licenses
- historical Room schemas for migration evidence
- source-only / no-APK validation

## Live organization contract

OwnPlay automatic Live organization is exactly two levels:

```text
Country
├── General
├── News
├── Sports
├── Movies
├── Series
├── Kids
├── Music
└── Documentaries
```

Provider category IDs remain provider identity and are never semantic ordering keys.

Automatic placement is the default. A manual `Move channel` placement overrides automatic classification until `Reset to automatic` is selected.

## Security boundary

Provider credentials and secret-bearing remote playlist URLs are not ordinary catalog metadata. They belong behind the secure credential-store boundary and must not appear in logs, Room rows, WorkManager input data, repository diagnostics, or backup payloads by default.

## Validation

Canonical source validation:

```bash
bash tools/validate-source-no-apk.sh
```

The validation workflow compiles the application and Android tests, runs unit tests and lint, verifies FFmpeg provenance/integrity, verifies the applicable Room schema boundary, and fails if APK/AAB artifacts are present or produced.

Routine rebuild work is source-only. APK generation, merge, release/publication, signing changes, destructive migrations, force-push/reset/rebase, and user-data deletion require separate explicit authorization.

## Authoritative documentation

Durable product and engineering authority is maintained in the OwnPlay-Mobile-2 Google Drive workspace. Live GitHub state is authoritative for current branch, HEAD, PR and CI status.
