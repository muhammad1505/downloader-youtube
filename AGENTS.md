# Repository Guidelines

## Project Structure & Module Organization
TubeFlow is an on-device Android downloader built with Kotlin and Jetpack Compose; it has no backend. Source lives in `app/src/main/java/id/local/tubeflow/`: `core/` contains the engine, Room models, settings, and WorkManager jobs; `ui/` contains screens, components, and themes. Android resources are in `app/src/main/res/`, and bundled engine assets in `app/src/main/assets/`. Room schema snapshots live in `app/schemas/`. Kotlin tests are under `app/src/test/`; Python tests and build/audit utilities are in `scripts/`. See `docs/` for validation, device testing, privacy, and release requirements; `third_party/libwebp/` holds pinned source and notices.

## Build, Test, and Development Commands
Use JDK 17 and Android SDK 35. Native rebuilding requires NDK 27.1.12297006 and CMake 3.22.1.

- `bash scripts/rebuild_ffmpeg_webp.sh`: generate the local FFmpeg AAR required for fresh checkouts.
- `./gradlew assembleDebug`: build debug APKs; open the project in Android Studio to run on a device.
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`: run the Gradle checks used by CI.
- `python3 -m unittest discover -s scripts -p "test_*.py"`: run host regression tests.
- `python3 scripts/audit_native.py <APK>`: check native ELF alignment.
- `python3 scripts/audit_release_zip_fields.py <release-APK>`: verify reflective ZIP constructors survive release optimization.

APKs are generated under `app/build/outputs/apk/`.

## Coding Style & Naming Conventions
Follow official Kotlin style (`kotlin.code.style=official`), using four-space indentation for new code. Use PascalCase for classes and composables, camelCase for functions/properties, and snake_case for Android resources. Match surrounding code and avoid unrelated reformatting. Python uses four-space indentation and snake_case names. Android Lint is configured; no standalone formatter is configured.

## Testing Guidelines
Use JUnit 4 with `*Test.kt` classes and descriptive method names; Python uses `unittest` with `test_*.py` files. No numerical coverage threshold is configured. Add regression coverage for behavior changes, especially database migrations, cancellation, selection validation, and native audits. Run relevant checks before submitting. Follow `docs/RELEASE.md` for physical-device validation; host tests do not establish runtime compatibility.

## Commit & Pull Request Guidelines
Git history is unavailable in this checkout. Use concise imperative commits, such as `Fix cancellation during conversion`. PRs should describe the change, link relevant issues, report checks run, and include screenshots for UI changes. Update affected documentation and schema snapshots.

## Security & Configuration
Keep keystores, signing passwords, and `local.properties` out of commits. Production signing uses `TUBEFLOW_KEYSTORE_*` and `TUBEFLOW_KEY_*` environment variables documented in `README.md`. Update bundled yt-dlp through `scripts/update_bundled_engine.py` with an explicit version and verified checksum. Preserve third-party notices.
