# How Foldlytics measures usage

[日本語](MEASUREMENT-ja.md)

Foldlytics calculates its statistics from usage events available through Android. The results are detected values, not an absolute physical hinge count or a complete, lossless usage history.

## Data used for measurement

Foldlytics requires Android Usage Access. It reads events for app display state, whether the screen is interactive, lock state, display configuration, and device startup or shutdown.

Display configuration events and checkpoints provide evidence for the cover and inner states. When a usable display configuration is available, Foldlytics saves a checkpoint when the app starts, enters or leaves the foreground, is refreshed manually, or is calibrated. It stores the events and checkpoints in a local Room database. Daily summaries and inner-display sessions are derived caches that can be regenerated from this source data when calibration, the time zone, or aggregation logic changes.

## Device use time

Foldlytics counts device use only while the screen is interactive and the device is unlocked. It does not include time while the screen is off or the device is locked.

Classified time is the part of device use that Foldlytics can assign to the cover or inner display. If it cannot determine the display, it records that interval as excluded time and does not add it to either display total.

## Cover and inner display classification

Foldlytics compares the display dimensions in Android configuration events with the saved cover and inner calibration values. When both calibration values are available, the app assigns the current configuration to the closer one. Before calibration is complete, an effective smallest screen width of at least 600dp counts as the inner display; a smaller value counts as the cover display.

If Android does not provide usable display dimensions, the posture is unknown. After a device restart or collection interruption, Foldlytics also leaves the posture unknown until it receives new evidence instead of carrying the previous state forward.

The hinge angle sensor is used for current-state diagnostics, not continuous background measurement. Display time is based on configuration events and checkpoints. Detected transitions are based on configuration events.

## Detected opens and closes

For a display configuration event inside the selected range, a known cover state followed by an inner state counts as a detected open. A known inner state followed by a cover state counts as a detected close. These counts are separate from the screen-interactive and unlocked conditions used for device use time.

Foldlytics does not increment either count when it first learns the posture, when a checkpoint updates the posture, immediately after a restart or collection interruption, or when either side of the transition is unknown.

The counts include only configuration events that Android retained and Foldlytics retrieved. They are not a count of every physical hinge movement.

## Inner-display sessions

An inner-display session starts only when a display configuration event confirms a transition from the cover display to the inner display. It completes only when a later display configuration event confirms the reverse transition from the inner display to the cover display. Checkpoints can establish or reaffirm the current display state, but a checkpoint does not itself start or complete a session.

Foldlytics includes a session in the selected period only when both its detected open and detected close are inside that period. A session is invalidated if display configuration becomes unknown, a device startup or shutdown is observed, collection is interrupted, or other evidence moves the state away from the inner display without a confirmed close. Screen-off and locked intervals pause active-time accumulation but do not end the session.

Inner active time includes only intervals when the screen is interactive, the device is unlocked, and the inner display remains confirmed. A completely observed session with no such interval is retained with an active time of zero. Foldlytics shows the number of complete sessions relative to detected opens, the median active time, and the longest active time. For an even number of sessions, the median is the midpoint of the two middle durations.

The session-start app is resolved once, at the first positive-duration inner active interval. Foldlytics assigns an app only when the `ACTIVITY_RESUMED` activities at that point identify exactly one package. If no app or multiple apps are resumed, the session remains unclassified; it does not guess from a later interval. The ranking shows launchable apps only and orders them by complete-session count, then total inner active time, then app name. Unclassified and non-launchable starts still remain part of the complete-session statistics.

## Inner display share and data coverage

Inner display share is `inner display time / (cover display time + inner display time)`. Excluded time is not part of the denominator.

Data coverage is `classified time / (classified time + excluded time)`. Both values include only intervals when the screen was interactive and the device was unlocked. Screen-off and locked time is not part of the denominator.

Data coverage reports how much of the observed device use Foldlytics could classify as cover or inner. It cannot detect every event that Android failed to retain, so it does not guarantee that the overall history is complete.

## Per-app display time

Foldlytics counts an app's display time while its activity is `ACTIVITY_RESUMED`, the screen is interactive, and the device is unlocked. An interval with an unknown display does not contribute to the app's cover or inner display time.

In split screen and other multi-resume situations, more than one app can be `ACTIVITY_RESUMED` at the same time. The sum of per-app display time can therefore exceed device use time. The ranking on the home screen includes only apps that can be launched from the device launcher.

## Privacy impact of session analysis

Session analysis uses only the usage events and checkpoints already described above. It does not read a new event category, request a new permission, add analytics or telemetry, or automatically transmit data off the device. The derived session cache remains in the local Room database and is removed when the app's data is cleared or the app is uninstalled. This feature therefore does not add a new collection, sharing, or transfer purpose to the Google Play Data safety disclosure.

## Daily and long-term values

Daily summaries use the device time zone at the time of aggregation. If the time zone changes, Foldlytics rebuilds the daily summaries from the saved source data.

An observed day is a day with classified or excluded time, a detected transition, or a recorded evidence gap. Average detected opens per observed day is the total detected open count divided by the number of observed days. A day counts as an inner-use day when it has at least one millisecond of inner display time.

The comparison between the first 30 days and the latest 30 days appears only after at least 60 calendar days have passed since the first day with observed device use, and only when both 30-day periods contain classified time.

## Synchronization and collection interruptions

Foldlytics synchronizes usage events when the app starts, returns to the foreground, or is refreshed manually. While the app is closed, WorkManager schedules synchronization approximately every six hours. Android power management can delay the actual run time.

Android retains usage events for a limited time. Events from before the first synchronization, or from a long interval without successful synchronization, may no longer be available. If more than 24 hours pass between successful synchronizations, Foldlytics marks a collection interruption and does not carry the cover or inner state across it.

If Usage Access is unavailable, the user is locked, Android cannot provide events, or reading or saving fails, Foldlytics does not advance the successful synchronization endpoint. The displayed analysis ends at the latest successful synchronization time.

## Device support and other limitations

- Foldlytics requires Android 10 (API 29) or later.
- It requires a compatible foldable device that exposes a hinge angle sensor to Android.
- Development and device testing have primarily used a Pixel 9 Pro Fold. Available events and configuration values can differ by device manufacturer and Android version.
- If automatic classification does not match the actual cover and inner states, both states can be registered from the calibration screen.
- Doze, manufacturer power management, force-stop, long power-off periods, and revoked permission can delay synchronization or leave gaps.
- The full-history CSV contains daily aggregates. It is not a complete raw-event backup and restore format.
