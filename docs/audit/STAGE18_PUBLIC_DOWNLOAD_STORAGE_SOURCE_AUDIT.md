# Stage 18 — Public Download Storage Source Audit

Status: SOURCE_IMPLEMENTATION_PENDING_VALIDATION

Authorized requirement: downloaded media must be visible under the device public Download folder in `OwnPlay Downloads`, with deterministic hierarchy. If the folder hierarchy does not exist, OwnPlay creates it.

Source base: `9c3439ae82d6313df7227e327ee490cef4b64d18` (Stage 17E).

## Implemented hierarchy

- Movies: `Download/OwnPlay Downloads/Movies/<Movie Title>/<Movie Title>.<ext>`
- Series episodes: `Download/OwnPlay Downloads/Series/<Series Title>/Season NN/SNNE NN - <Episode Title>.<ext>` where the concrete filename form is `S01E03 - Episode Title.ext`.

Path components are sanitized, whitespace is normalized, traversal/path separators are removed, and extension handling is deterministic.

## Storage strategy

- Android 10+ (API 29+): `MediaStore.Downloads` with `RELATIVE_PATH = Download/OwnPlay Downloads/...`. Creating the MediaStore item creates the missing hierarchy without broad-storage access.
- Android 8–9 (API 26–28): public Downloads filesystem with `WRITE_EXTERNAL_STORAGE`, manifest-limited to `maxSdkVersion=28` and requested only on those legacy Android versions.
- In-progress transfer staging remains app-private so `.part`/incomplete transfers are not exposed as completed media.
- Completed media is integrity-verified after publication before the database is transitioned to `COMPLETED`.
- Existing private `ownplay-downloads/...` references remain readable/removable for update compatibility. No Room schema change or destructive migration is introduced.
- New public references are stored in the existing `localReference` field as `content://...` on API 29+ or `file://...` on API 26–28.

## State-machine preservation

Queue, pause, resume, retry, remove, WorkManager ownership, byte progress reporting, HTTP range resume, integrity metadata, and offline playback start policy remain in place. If a transfer loses the race with a pause/remove before the final database transition, the newly published public item is deleted instead of being left as an accepted completed download.

## Compatibility / boundaries

Unchanged:

- Room schema and database version
- source/provider credentials and source refresh behavior
- playback controller/session/video target ownership
- Live
- Settings behavior apart from the existing Manage Downloads surface consuming the same download state
- signing and versioning

Restricted actions not performed: APK/AAB generation, merge, ready-for-review, release/deploy, signing changes, destructive database operations, reset/rebase/force-push.

Physical verification required after a future explicitly authorized QA APK:

1. `Download/OwnPlay Downloads` is created when absent.
2. Movie and series hierarchy is visible in the device file manager.
3. Pause/resume completes into the same public hierarchy.
4. Offline playback works from the stored `content://` or legacy `file://` reference.
5. Remove deletes the corresponding public file.
6. Updating from a build with existing private downloads does not break those existing offline entries.
