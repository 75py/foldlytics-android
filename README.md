# Foldlytics

[日本語](README-ja.md)

How often do you actually unfold your phone?

Foldlytics is an Android app for compatible foldable phones. It shows how much time you spend on the cover and inner displays, how many opens and closes it detects, and which apps you use on each display.

Download: [https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics](https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics)

[Why I built Foldlytics and the design choices behind it](docs/CONCEPT.md)

[How Foldlytics measures usage](docs/MEASUREMENT.md)

[Privacy policy](https://www.nagopy.com/privacy-policy/)

## What you can see

- Cover and inner display time, plus the inner-display ratio.
- Detected open and close counts.
- Average detected opens per observed day.
- Days with recorded data and days on which the inner display was used.
- Inner-display ratio and detected-open trends over longer periods.
- A comparison between the first 30 days and the latest 30 days when enough
  history is available.
- App rankings for the cover or inner display, limited to launchable apps and
  shown with their icons.
- Data coverage and collection status.
- Daily CSV export for all saved history, to a destination you choose.

## Build and test

Use JDK 17 and Android SDK 36.

```shell
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

To run the unit tests and build together:

```shell
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at
`app/build/outputs/apk/debug/app-debug.apk` with application ID
`com.nagopy.android.foldlytics.debug`. The production application ID is
`com.nagopy.android.foldlytics`.

`connectedDebugAndroidTest` can reinstall the target app and clear its data.
Do not run it on a device that contains Foldlytics history you want to keep;
use an emulator or a dedicated test device.

## Project structure

- `app/src/main/java/com/nagopy/android/foldlytics/`: activity, application,
  and view model.
- `data/`: UsageStats reading, synchronization, Room storage, aggregation, and
  CSV generation.
- `model/`: immutable domain values and period rules.
- `ui/`: Jetpack Compose home, charts, calibration, and diagnostics.
- `app/src/test/`: deterministic JVM tests.
- `app/src/androidTest/`: database and Compose device tests.
- `docs/`: product documentation.
- `store-assets/google-play/`: Google Play listing copy and source assets.

## Technology

- Kotlin and Jetpack Compose
- Android Gradle Plugin 9.3.0
- `UsageStatsManager.queryEvents()`
- Jetpack WindowManager
- `TYPE_HINGE_ANGLE` diagnostics
- Room
- WorkManager
