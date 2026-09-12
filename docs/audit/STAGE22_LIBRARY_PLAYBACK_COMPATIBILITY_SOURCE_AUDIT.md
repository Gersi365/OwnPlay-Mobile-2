# Stage 22 — Library playback compatibility source audit

## Trigger and evidence

- Physical QA of QA v24 (Stage 21 exact source `f33d06003e0aaac5cc18cab96a0c7e3cd9729a3d`) reported that Movies and Series enter buffering and then show `Playback unavailable`.
- The report establishes a physical playback failure but does not expose a credential-safe Media3 transport error code, so this stage does not claim a single proven provider-side root cause.
- Source inspection verified a compatibility gap: Live already has a bounded alternate stream candidate, while Library Movies/Episodes resolved exactly one Xtream URI and stopped after a player error.

## Corrections

1. Movie/Episode playback now carries one runtime-only alternate Xtream candidate.
   - For progressive/unknown provider metadata, the alternate is the provider's HLS `.m3u8` route.
   - If the provider already reports HLS, the alternate is the extensionless route.
   - Only one alternate is retained; there is no unbounded extension guessing.
2. Library playback consumes the alternate only when the primary fails during startup. Once the primary reaches READY, fallback eligibility is disabled so a later network interruption cannot silently restart media from its initial start position.
3. Media3 keeps the existing single ExoPlayer/session/surface architecture but now uses an explicit HTTP data-source factory that permits cross-protocol redirects and sends an OwnPlay user agent.
4. Primary and fallback credential-bearing URIs remain runtime-only and redacted from `ResolvedLibraryPlayback.toString()`.
5. Offline playback is unchanged and receives no network fallback candidate.

## Boundaries

- No Room entity/schema/version change.
- No credential storage change.
- No Live reducer/session/surface ownership change.
- No download state-machine or public-download hierarchy change.
- No APK/AAB generation is authorized by this source stage.
- Physical playback PASS cannot be claimed until a later explicitly authorized QA APK is tested against the same provider.

## Validation contract

- Exact Stage 21 parent must be `f33d06003e0aaac5cc18cab96a0c7e3cd9729a3d`.
- Focused tests cover alternate URI selection, HLS/AUTO format classification, and redaction of both primary and fallback URIs.
- Standard source validation must pass compileDebugKotlin, compileDebugUnitTestKotlin/testDebugUnitTest, lintDebug, Room schema cleanliness, and the explicit no-APK/AAB guard.
