#!/usr/bin/env bash
set -euo pipefail

# Source-only validation for OwnPlay Mobile 2.
# This script deliberately avoids assemble, bundle, package, install, and connected-device tasks.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PINNED_GRADLE_VERSION="9.6.0"
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

verify_public_repo_hygiene

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
