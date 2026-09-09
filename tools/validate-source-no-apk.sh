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
  --stacktrace

# Stage 3 bootstrap evidence only: emit the generated Room schema so the exact
# schema can be committed, then this dump is removed and replaced by a clean-tree check.
echo "ROOM_SCHEMA_DUMP_BEGIN"
if [[ -d app/schemas ]]; then
  find app/schemas -type f -name '*.json' -print -exec cat {} \;
else
  echo "ERROR: Room schema directory was not generated." >&2
  exit 5
fi
echo "ROOM_SCHEMA_DUMP_END"

created_artifacts="$(find_packaged_artifacts)"
if [[ -n "$created_artifacts" ]]; then
  echo "ERROR: Source-only validation produced packaged Android artifacts:" >&2
  printf '%s\n' "$created_artifacts" >&2
  exit 4
fi

echo "PASS: source-only validation completed and no APK/AAB was produced."
