# WebP runtime replacement

The bundled FFmpeg 0.18.1 AAR contains WebP 1.6.0 shared libraries with 4 KB
ELF alignment. The two 64-bit ABI payloads require rebuilding five libraries
for devices using 16 KB memory pages.

Original `WebPGetDecoderVersion` and `WebPGetEncoderVersion` return `0x010600`
in both arm64-v8a and x86_64 binaries, confirming WebP 1.6.0. The source archive
is preserved here, unmodified, with its BSD license, patent grant, and authors.

Source: https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.6.0.tar.gz

Source SHA-256: `e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564`

Original Maven artifact:
`io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`

Original AAR SHA-256:
`0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49`

Run `scripts/rebuild_ffmpeg_webp.sh` from the repository. The recipe uses Android
NDK 27.1.12297006, SDK CMake 3.22.1, Android API 24, and a 16 KB linker page size.
`ANDROID_NDK_HOME`, `CMAKE_BIN`, `NINJA_BIN`, and `WEBP_BUILD_DIR` may override
tool locations. Using different tool versions may change binary hashes.

The replacement preserves all other AAR entries and the entire 32-bit payloads.
Before packaging, it verifies matching SONAMEs, preservation of every originally
exported symbol, identical direct dependencies, and correct ELF PT_LOAD
alignment and offset/address congruence for every nested 64-bit shared library.
The resulting AAR and audit are written to `app/libs/`.

These static checks do not replace an Android 16 KB device/emulator smoke test:
initialize the packaged runtime and run an actual FFmpeg remux and audio encode.
Upstream FFmpeg and wrapper licenses continue to apply; this directory supplies
the exact source for the WebP changes only.
