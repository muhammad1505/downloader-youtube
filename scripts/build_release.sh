#!/usr/bin/env bash
# Build production-signed APKs and AAB using private local/CI credentials.
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
task_signing_env="${TUBEFLOW_SIGNING_ENV:-$HOME/.local/share/tubeflow/signing/release-signing.env}"

if [[ -z "${TUBEFLOW_KEYSTORE_PATH:-}" || -z "${TUBEFLOW_KEYSTORE_PASSWORD:-}" ||
      -z "${TUBEFLOW_KEY_ALIAS:-}" || -z "${TUBEFLOW_KEY_PASSWORD:-}" ]]; then
    if [[ ! -r "$task_signing_env" ]]; then
        printf 'Missing private signing configuration: %s\n' "$task_signing_env" >&2
        exit 1
    fi
    # Only source a trusted local file; it contains credentials and shell code.
    source "$task_signing_env"
fi

for task_name in TUBEFLOW_KEYSTORE_PATH TUBEFLOW_KEYSTORE_PASSWORD TUBEFLOW_KEY_ALIAS TUBEFLOW_KEY_PASSWORD; do
    if [[ -z "${!task_name:-}" ]]; then
        printf 'Missing required signing variable: %s\n' "$task_name" >&2
        exit 1
    fi
    export "$task_name"
done
if [[ ! -f "$TUBEFLOW_KEYSTORE_PATH" ]]; then
    printf 'Signing keystore does not exist.\n' >&2
    exit 1
fi

cd "$task_root"
./gradlew "$@" testDebugUnitTest lintDebug lintRelease assembleRelease bundleRelease
