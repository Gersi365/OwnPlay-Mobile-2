#!/usr/bin/env bash
set -euo pipefail

# Source-only validation for OwnPlay Mobile 2.
# This script deliberately avoids assemble, bundle, package, install, and connected-device tasks.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PINNED_GRADLE_VERSION="9.6.0"
FFMPEG_AAR="app/libs/media3-decoder-ffmpeg-1.11.0-ffmpeg6.0.aar"
FFMPEG_PROVENANCE="docs/third_party/FFMPEG_AUDIO_DECODER.md"
PINNED_FFMPEG_AAR_SHA256="3997eab5910483a4b7ab2928def2894379b2d9664ac54291c488669380f98734"
FFMPEG_LICENSE_FILES=(
  "app/src/main/assets/licenses/ffmpeg/LICENSE.md"
  "app/src/main/assets/licenses/ffmpeg/COPYING.LGPLv2.1"
  "app/src/main/assets/licenses/ffmpeg/COPYING.LGPLv3"
)
cd "$ROOT_DIR"

find_packaged_artifacts() {
  find . -type f \( -name '*.apk' -o -name '*.aab' \) -not -path './.git/*' -print
}

verify_public_repo_hygiene() {
  local forbidden_paths
  forbidden_paths="$(
    git ls-files | grep -E \
      '(^|/)(\.env([.].*)?|secrets\.properties|keystore\.properties|qa-signing-secret\.txt)$|[.](jks|keystore|p12|pfx|key)$' \
      || true
  )"
  if [[ -n "$forbidden_paths" ]]; then
    echo "ERROR: Secret/signing material must not be tracked in the public repository:" >&2
    printf '%s\n' "$forbidden_paths" >&2
    exit 6
  fi

  local private_key_markers
  private_key_markers="$(
    git grep -I -n -E \
      -e '-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----' \
      -- . \
      || true
  )"
  if [[ -n "$private_key_markers" ]]; then
    echo "ERROR: Private-key material was detected in tracked text files:" >&2
    printf '%s\n' "$private_key_markers" >&2
    exit 6
  fi
}

verify_ffmpeg_aar_integrity() {
  if [[ ! -f "$FFMPEG_AAR" ]] || ! git cat-file -e "HEAD:$FFMPEG_AAR"; then
    echo "ERROR: Pinned FFmpeg decoder AAR is missing from committed HEAD: $FFMPEG_AAR" >&2
    exit 7
  fi
  if [[ ! -f "$FFMPEG_PROVENANCE" ]] || ! git cat-file -e "HEAD:$FFMPEG_PROVENANCE"; then
    echo "ERROR: FFmpeg provenance document is missing from committed HEAD: $FFMPEG_PROVENANCE" >&2
    exit 7
  fi

  local license_file
  for license_file in "${FFMPEG_LICENSE_FILES[@]}"; do
    if [[ ! -f "$license_file" ]] || ! git cat-file -e "HEAD:$license_file"; then
      echo "ERROR: Required FFmpeg license asset is missing from committed HEAD: $license_file" >&2
      exit 7
    fi
  done

  local documented_sha actual_sha
  documented_sha="$(
    sed -nE 's/^- AAR SHA-256: `([0-9a-f]{64})`$/\1/p' "$FFMPEG_PROVENANCE" \
      | head -n 1
  )"
  if [[ "$documented_sha" != "$PINNED_FFMPEG_AAR_SHA256" ]]; then
    echo "ERROR: FFmpeg provenance checksum does not match the pinned source-validation checksum." >&2
    exit 7
  fi

  actual_sha="$(sha256sum "$FFMPEG_AAR" | awk '{ print $1 }')"
  if [[ "$actual_sha" != "$PINNED_FFMPEG_AAR_SHA256" ]]; then
    echo "ERROR: FFmpeg decoder AAR checksum mismatch." >&2
    echo "Expected: $PINNED_FFMPEG_AAR_SHA256" >&2
    echo "Actual:   $actual_sha" >&2
    exit 7
  fi

  echo "Validated FFmpeg decoder AAR SHA-256: $actual_sha"
  echo "Validated FFmpeg provenance and license assets."
}

verify_public_repo_hygiene
verify_ffmpeg_aar_integrity

existing_artifacts="$(find_packaged_artifacts)"
if [[ -n "$existing_artifacts" ]]; then
  echo "ERROR: Packaged Android artifacts already exist before validation:" >&2
  printf '%s\n' "$existing_artifacts" >&2
  exit 2
fi

# Keep the committed Room schema set aligned with the current @Database version.
database_source="app/src/main/java/app/ownplay/mobile/data/db/OwnPlayDatabase.kt"
schema_dir="app/schemas/app.ownplay.mobile.data.db.OwnPlayDatabase"
database_version="$(
  sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*([0-9]+),[[:space:]]*$/\1/p' "$database_source" \
    | head -n 1
)"
if [[ -z "$database_version" ]]; then
  echo "ERROR: Could not determine OwnPlayDatabase version from $database_source." >&2
  exit 5
fi
schema_file="$schema_dir/$database_version.json"

verify_room_schema_tree() {
  if [[ ! -f "$schema_file" ]] || ! git cat-file -e "HEAD:$schema_file"; then
    echo "ERROR: Room schema v$database_version must exist and be committed in HEAD: $schema_file" >&2
    exit 5
  fi
  local schema_status
  schema_status="$(git status --porcelain=v1 --untracked-files=all --ignored -- app/schemas)"
  if [[ -n "$schema_status" ]]; then
    echo "ERROR: Room schema tree differs from committed HEAD:" >&2
    printf '%s\n' "$schema_status" >&2
    exit 5
  fi
}
verify_room_schema_tree

if [[ -x "./gradlew" ]]; then
  GRADLE_CMD=("./gradlew")
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=("gradle")
else
  echo "ERROR: Gradle is not available. Install/provision Gradle $PINNED_GRADLE_VERSION before validation." >&2
  exit 3
fi

gradle_version="$("${GRADLE_CMD[@]}" --version | awk '/^Gradle / { print $2; exit }')"
if [[ "$gradle_version" != "$PINNED_GRADLE_VERSION" ]]; then
  echo "ERROR: Source validation requires Gradle $PINNED_GRADLE_VERSION; found ${gradle_version:-unknown}." >&2
  exit 3
fi

echo "Validated Gradle version: $gradle_version"

"${GRADLE_CMD[@]}" \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest \
  :app:lintDebug \
  --rerun-tasks \
  --no-build-cache \
  --stacktrace

verify_room_schema_tree

echo "PASS: Room schema v$database_version matches committed HEAD; schema tree is clean."

created_artifacts="$(find_packaged_artifacts)"
if [[ -n "$created_artifacts" ]]; then
  echo "ERROR: Source-only validation produced packaged Android artifacts:" >&2
  printf '%s\n' "$created_artifacts" >&2
  exit 4
fi

echo "PASS: source-only validation completed and no APK/AAB was produced."
