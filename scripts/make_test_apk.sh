#!/usr/bin/env bash
# Sign the optimized release candidate with the local Android TEST key only.
# Never install, publish, or use this key for a production release.
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
task_abi="${1:-arm64-v8a}"
case "$task_abi" in arm64-v8a|armeabi-v7a|x86_64) ;; *) printf 'Unsupported ABI\n' >&2; exit 1 ;; esac
task_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
task_tools="${ANDROID_BUILD_TOOLS_DIR:-$task_sdk/build-tools/36.0.0}"
task_input="$task_root/app/build/outputs/apk/release/app-$task_abi-release-unsigned.apk"
task_output_dir="$task_root/app/build/outputs/apk/rc"
task_output="$task_output_dir/TubeFlow-$task_abi-testing.apk"
task_key="${ANDROID_TEST_KEYSTORE:-$HOME/.android/debug.keystore}"
test -f "$task_input"
test -f "$task_key"
mkdir -p "$task_output_dir"
"$task_tools/apksigner" sign --ks "$task_key" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$task_output" "$task_input"
rm -f "$task_output.idsig"
"$task_tools/apksigner" verify --verbose "$task_output"
"$task_tools/zipalign" -c -P 16 4 "$task_output"
python3 - "$task_output" "$task_abi" <<'PYMETA'
import hashlib, json, sys, zipfile
from pathlib import Path
p=Path(sys.argv[1])
with zipfile.ZipFile(p) as archive:
    engine=json.loads(archive.read('assets/engine.json'))
    assert hashlib.sha256(archive.read('assets/yt-dlp')).hexdigest()==engine['sha256']
record={'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size,'abi':sys.argv[2],'signing':'Android local test key only','engine':engine['version'],'physicalTesting':'see docs/DEVICE_TEST.md'}
p.with_suffix('.json').write_text(json.dumps(record,indent=2)+'\n')
PYMETA
printf 'TEST key only; APK ready for confirmed device testing: %s\n' "$task_output"
