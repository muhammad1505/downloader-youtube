#!/usr/bin/env bash
# Rebuild only the five upstream WebP 1.6.0 libraries requiring 16 KB alignment.
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
task_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
task_ndk="${ANDROID_NDK_HOME:-$task_sdk/ndk/27.1.12297006}"
task_cmake="${CMAKE_BIN:-$task_sdk/cmake/3.22.1/bin/cmake}"
task_ninja="${NINJA_BIN:-$task_sdk/cmake/3.22.1/bin/ninja}"
task_work="${WEBP_BUILD_DIR:-$task_root/build/native-webp}"
task_source_archive="$task_root/third_party/libwebp/libwebp-1.6.0.tar.gz"
task_upstream="$task_work/ffmpeg-0.18.1.aar"
task_output="$task_root/app/libs/ffmpeg-0.18.1-webp16k.aar"
task_toolchain="$task_ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$task_cmake"
test -x "$task_ninja"
test -x "$task_toolchain/llvm-readelf"
mkdir -p "$task_work"
python3 - "$task_source_archive" <<'PY'
import hashlib, pathlib, sys
p = pathlib.Path(sys.argv[1])
assert hashlib.sha256(p.read_bytes()).hexdigest() == "e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564", "libwebp source checksum mismatch"
PY
if [[ ! -f "$task_upstream" ]]; then
  curl -fsSL --retry 3 --connect-timeout 30 --max-time 900 \
    'https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/ffmpeg/0.18.1/ffmpeg-0.18.1.aar' \
    -o "$task_upstream"
fi
python3 - "$task_upstream" <<'PY'
import hashlib, pathlib, sys
p = pathlib.Path(sys.argv[1])
assert hashlib.sha256(p.read_bytes()).hexdigest() == "0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49", "upstream AAR checksum mismatch"
PY
tar -xzf "$task_source_archive" -C "$task_work"
export SOURCE_DATE_EPOCH=1750118400
for task_abi in arm64-v8a x86_64; do
  "$task_cmake" -S "$task_work/libwebp-1.6.0" -B "$task_work/$task_abi" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$task_ninja" \
    -DCMAKE_TOOLCHAIN_FILE="$task_ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$task_abi" -DANDROID_PLATFORM=android-24 \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384,--as-needed \
    -DCMAKE_C_FLAGS="-ffile-prefix-map=$task_work=." \
    -DWEBP_BUILD_CWEBP=OFF -DWEBP_BUILD_DWEBP=OFF -DWEBP_BUILD_GIF2WEBP=OFF \
    -DWEBP_BUILD_IMG2WEBP=OFF -DWEBP_BUILD_VWEBP=OFF -DWEBP_BUILD_WEBPINFO=OFF \
    -DWEBP_BUILD_ANIM_UTILS=OFF -DWEBP_BUILD_EXTRAS=OFF \
    -DWEBP_BUILD_LIBWEBPMUX=ON -DWEBP_BUILD_WEBPMUX=OFF
  "$task_cmake" --build "$task_work/$task_abi" --parallel "${WEBP_BUILD_JOBS:-2}"
done
python3 "$task_root/scripts/patch_ffmpeg_webp.py" "$task_upstream" "$task_work" "$task_output" \
  --toolchain "$task_toolchain"
sha256sum "$task_output"
