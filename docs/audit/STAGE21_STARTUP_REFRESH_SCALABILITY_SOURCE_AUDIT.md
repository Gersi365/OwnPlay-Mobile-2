# Stage 21 — startup, refresh, and large-catalog scalability source audit

## Authority and scope

- Parent/source authority: Stage 20 exact HEAD `ca365683fe2a167ca435533e14a45bb6800f35be`.
- Scope: startup/loading audit, provider refresh latency, and large-catalog browse cost.
- Source-only stage. APK/AAB generation is not authorized.

## Audit findings

1. `OwnPlayApplication` already observes refresh scheduling off the main thread, while the initial Live destination immediately needs the shared playback controller. Deferring Media3 construction would require a playback architecture/lifecycle redesign, so Stage 21 intentionally does not change that boundary.
2. Xtream refresh loaded six independent top-level catalog endpoints sequentially even though the shared OkHttp dispatcher already bounds total and per-host concurrency.
3. Library home observed every available episode for the active source even though `LibraryCatalog` only needs episode metadata for incomplete Continue Watching rows; full episode lists are already loaded on demand for a selected series.
4. Library and Live browse filtering was recomputed during unrelated Compose recompositions. This is especially expensive with provider catalogs containing thousands of movies, series, or channels and with playback state updating while Live is visible.
5. ProviderRefreshWorker remains sequential across providers by design in this stage. Parallelizing whole-provider refreshes would multiply catalog traffic and persistence pressure beyond the existing per-host network bound.

## Implemented corrections

- Launch the six independent Xtream top-level catalog requests concurrently. Existing OkHttp dispatcher limits remain the transport authority, and existing partial-refresh/category-recovery behavior remains unchanged.
- Library home no longer subscribes to the complete episode catalog. It resolves episode metadata only for valid incomplete episode-progress rows; `getEpisodesForSeries` remains the full detail path.
- Memoize Library category visibility and movie/series search/category filtering against only their relevant inputs.
- Memoize Live category visibility, channel search/category filtering, selected-channel lookup, and fullscreen channel-number lookup against only their relevant inputs.
- Guarded the `LibraryFullscreenPlayer` and `FullscreenLive` suffixes byte-for-byte while applying the browse-only UI edits.

## Explicitly unchanged

- Room entities, schema files, and database version.
- Playback controller/session/surface ownership architecture.
- Live presentation reducer semantics and Back/Preview/Fullscreen behavior.
- Player/fullscreen implementations.
- Source credentials, backup format, downloads state machine/storage policy, signing, versioning, release, and deployment.

## Acceptance contract

Stage 21 is source-PASS only after exact-final-HEAD validation completes `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest`, `lintDebug`, verifies committed Room schemas remain clean, and confirms no APK/AAB artifact was produced. Physical/device performance remains separately unverified until exercised on representative large provider catalogs.
