#!/usr/bin/env bash
set -euo pipefail

# Source-only validation for OwnPlay Mobile 2.
# This script deliberately avoids assemble, bundle, package, install, and connected-device tasks.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

find_packaged_artifacts() {
  find . -type f \( -name '*.apk' -o -name '*.aab' \) -not -path './.git/*' -print
}

existing_artifacts="$(find_packaged_artifacts)"
if [[ -n "$existing_artifacts" ]]; then
  echo "ERROR: Packaged Android artifacts already exist before validation:" >&2
  printf '%s\n' "$existing_artifacts" >&2
  exit 2
fi

# Require the baseline in HEAD, then reject staged, unstaged, untracked, and
# ignored schema changes both before and after Room generation.
schema_file="app/schemas/app.ownplay.mobile.data.db.OwnPlayDatabase/1.json"
verify_room_schema_tree() {
  if [[ ! -f "$schema_file" ]] || ! git cat-file -e "HEAD:$schema_file"; then
    echo "ERROR: Room schema v1 must exist and be committed in HEAD." >&2
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
  echo "ERROR: Gradle is not available. Install/provision the pinned Gradle version before validation." >&2
  exit 3
fi

"${GRADLE_CMD[@]}" \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest \
  :app:lintDebug \
  --rerun-tasks \
  --no-build-cache \
  --stacktrace

verify_room_schema_tree

echo "PASS: generated Room schemas match committed HEAD; schema tree is clean."

created_artifacts="$(find_packaged_artifacts)"
if [[ -n "$created_artifacts" ]]; then
  echo "ERROR: Source-only validation produced packaged Android artifacts:" >&2
  printf '%s\n' "$created_artifacts" >&2
  exit 4
fi

echo "PASS: source-only validation completed and no APK/AAB was produced."
