# Stage 20 — Performance and Download Robustness Source Audit

- Parent/source authority: `c537c38907371df369a15549d62e8bd5bcacc50a` (`stage-19-search-downloads-seasons`).
- Scope is source-only. No APK/AAB, signing, version, release, deployment, merge, or ready-for-review action is authorized.
- Shared remote-image loading now serves both Library artwork and Live logos with a bounded in-memory LRU, in-flight request deduplication, independent caller cancellation, byte ceilings, and sampled decoding.
- Download observation is device-wide rather than tied to the active provider. Playback progress joins include `sourceId` to prevent cross-provider key collisions.
- Public Download naming keeps the requested human-readable hierarchy. Existing files are never overwritten merely because another item has the same title. A stable opaque suffix derived from source/content identity is added only when a collision is detected, followed by an ordinal for repeated collisions.
- Room entities/schema/version are unchanged. Only DAO queries were added.
- Stage 18 `Download/OwnPlay Downloads/...` hierarchy and integrity-before-publish behavior remain intact.
- Library fullscreen player implementation and Live fullscreen implementation are guarded byte-for-byte by the Stage 20 apply script.
- Physical/device behavior remains NOT_YET_VERIFIED until a separately authorized QA build is installed.
