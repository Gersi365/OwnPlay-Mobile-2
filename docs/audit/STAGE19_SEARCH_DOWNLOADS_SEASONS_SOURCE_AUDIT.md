# Stage 19 — Search, Downloads and Episode Navigation Source Audit

Base authority: `stage-18-public-download-storage` at `67c52776261ba6f431006b3d3734ab4ad7a6a903`.

Scope:
- make top-bar Search a real action and remove dead header affordances where no callback exists;
- add local Library search across movies and series;
- add local Live search across channels while preserving the Live reducer and Preview/fullscreen behavior;
- redesign Settings > Manage downloads into compact progress rows;
- route completed Manage Downloads items into the existing Library offline playback owner rather than creating a second player session;
- require confirmation before deleting a managed download;
- add season filtering for series detail and reduce redundant episode-title text;
- make compact download controls and shared action targets respect a 48dp interaction target.

Explicit boundaries:
- no APK/AAB generation is authorized by this stage;
- no Room schema/database version change;
- no Stage 18 public Downloads storage behavior change;
- no provider/auth/backup change;
- no playback-controller/session architecture replacement;
- `LibraryFullscreenPlayer` is preserved byte-for-byte by the deterministic patch guard;
- `FullscreenLive` and everything after it are preserved byte-for-byte by the deterministic patch guard;
- no merge, ready-for-review, release, deployment, signing or version change.

Acceptance:
- exact final HEAD must pass compileDebugKotlin, compileDebugUnitTestKotlin, testDebugUnitTest, lintDebug and Room schema cleanliness through the standard no-APK validation workflow;
- workflow artifacts must remain zero;
- physical visual/interaction acceptance remains NOT_YET_VERIFIED until a later explicitly authorized QA APK is tested on device.
