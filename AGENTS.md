# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. Root Gradle files define shared plugin and repository settings; application configuration and dependencies live in `app/build.gradle.kts`.

- `app/src/main/java/com/nagopy/android/foldlytics/` contains the activity and `MainViewModel`.
- `data/` reads and analyzes usage events; `model/` holds domain types; `ui/` contains Jetpack Compose screens and theming.
- `app/src/main/res/` stores strings, themes, drawables, and backup rules. Declare platform capabilities in `AndroidManifest.xml`.
- `app/src/test/` contains local JVM tests. Add device tests under `app/src/androidTest/`.

## Build, Test, and Development Commands

Use JDK 17 with Android SDK 36.

- `./gradlew testDebugUnitTest` runs local JUnit tests.
- `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew lintDebug` runs Android lint checks.
- `./gradlew testDebugUnitTest assembleDebug` performs the repository's documented build verification.
- `./gradlew installDebug` installs the debug build on a connected device or emulator.

## Coding Style & Naming Conventions

Follow standard Kotlin style: four-space indentation, trailing commas in multiline declarations, and explicit imports. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` for constants. Keep packages lowercase. Place event-processing logic in `data/`, immutable domain values in `model/`, and UI state transitions in the view model. Keep composables focused and pass callbacks instead of embedding data access. No standalone formatter is configured, so use Android Studio's Kotlin formatter and run lint before submitting.

## Testing Guidelines

Tests use JUnit 4 and should use behavior-focused names such as `excludesScreenOffAndLockedIntervals`. Add deterministic unit tests for every aggregation, timing-boundary, calibration, or posture-classification change. Manually verify usage-access permission, configuration events, fold calibration, and hinge sensing on a foldable device; report the device and Android version in the PR.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout, so use short, imperative commit subjects and keep each commit focused. PRs should explain behavior changes, list verification commands, link relevant issues, and include before/after screenshots for Compose UI changes. Call out any device-only checks that remain unverified.

## Security & Privacy

Usage-event data is sensitive and is intentionally processed on-device without network permission. Do not add telemetry, networking, exported components, or broader permissions without explicit review. Never commit `local.properties`, device reports, or user-derived usage data.
