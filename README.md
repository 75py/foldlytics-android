# Foldlytics

[日本語](README-ja.md)

How often do you actually unfold your phone?

Foldlytics is an Android app for compatible foldable phones. It shows how much time you spend on the cover and inner displays, how many opens it detects, and which apps you use on each display.

Download: [https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics](https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics)

[Why I built Foldlytics and the design choices behind it](docs/CONCEPT.md)

[How Foldlytics measures usage](docs/MEASUREMENT.md)

[Privacy policy](https://www.nagopy.com/privacy-policy/)

## What you can see

- A home-screen widget showing your inner display share and usage summary.
- Cover and inner display time, plus the inner-display ratio.
- Detected open counts.
- Inner-display use for each opening, with median, average, and longest time,
  plus the three longest uses and their top app usage.
- Average detected opens per observed day.
- Days with recorded data and days on which the inner display was used.
- Inner-display ratio and detected-open trends over longer periods.
- A comparison between the first 30 days and the latest 30 days when enough
  history is available.
- App rankings by total display time, cover display, or inner display, limited
  to launchable apps and shown with their icons.
- Per-app cover/inner shares, grouped by the display with the higher share and
  ordered by measured time on that display.
- Data coverage and collection status.
- Daily CSV export for all saved history, to a destination you choose.

Inner-display use treats each detected opening followed by a detected close as
one use. The statistics include known zero-time uses. If any positive-length
part of a use has screen or lock evidence that cannot establish either active or
inactive use, that whole use is left out, even if later evidence is available.
Confirmed screen-off or locked periods count as zero use, and an opening and
closing at the same time can remain a valid zero-time use. For the three longest
uses, the breakdown shows at most three apps or simultaneous-app combinations.
Simultaneous use is grouped by the definitely resumed apps and each interval
is counted once. Entries beyond the three shown, time involving non-launchable
apps, and intervals that cannot be matched to an app are shown as Other.
When exactly one app is definitely resumed, its session time is
attributed even if older activity events leave another app only possibly resumed;
this can approximate genuinely split use when Android's evidence is ambiguous.
The same usage events already stored on the device are used,
with no new permission or automatic usage-history transfer; the derived cache can be
regenerated from the saved source events.

This branch adds automatic Home explanations with ML Kit Prompt API when Gemini Nano
is already available. Usage inputs and generated text are processed on-device; ML Kit
sends API performance and utilization metrics to Google. No model download is initiated
by Foldlytics. Device validation and publication of updated privacy/Data safety
disclosures are pending. See [ML Kit implementation and release gates](docs/MLKIT-INSIGHTS.md).

App usage starts with apps sorted by total usage time. You can also sort by time
on the outer or inner display. In the By display view, each app appears under the
display where it was used longer. Each list is sorted by time on that display,
longest first. Apps with equal time on both displays appear in neither list.
Time when the display is unknown is shown separately and excluded from totals
and percentages.

## Home-screen widget

Add Foldlytics from your launcher's widget picker. The donut shows your inner display share
from a 2×2 size, including with larger system text. Widen the widget to also see inner and
cover display time and opens, grouped beside the chart as in the shared image.
Increasing the height keeps these rows together.
Choose a one-day, seven-day, or thirty-day period; seven days is the default.

Values reflect the last successfully collected records. Check the displayed dates and last sync
time. Use Refresh to sync, or tap the widget to open analysis for the corresponding period.
The widget distinguishes unavailable records and required Usage Access from a measured zero.

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

## License

Foldlytics is licensed under the [Apache License 2.0](LICENSE).
