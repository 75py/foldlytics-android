# Diagnostic database export

This branch provides an explicit export action for investigating recorded usage on a device. The export flag defaults to **enabled on this diagnostic branch**, including release builds, so an internal-test update can read the existing production application's data.

Build using the existing signing and version-code workflow for Google Play internal testing:

```sh
./gradlew bundleRelease
```

To build with the action disabled:

```sh
./gradlew bundleRelease -PenableDiagnosticExport=false
```

The debug variant has a different application ID and therefore cannot export the internal-test application's database. Use an update with the same application ID and compatible signing through the existing internal-test track.

## Export on the device

1. Display the period containing an affected session and wait for analysis to finish.
2. Open the navigation drawer and choose **Save full diagnostic data** (Japanese: **診断データ一式を保存**).
3. Choose the ZIP destination in Android's document picker and wait for the saved message.

Export is also available before analysis finishes or without Usage Access, so already saved data can still be inspected. In that case, the report can contain an older displayed snapshot or no analysis. The database always contains all saved tables at snapshot time, regardless of the selected display period.

## Archive contents

- `foldlytics.db`: a standalone SQLite database with the saved source events, posture checkpoints, sync history, and derived summaries/session app allocations. A transactionally consistent logical copy includes the schema, typed rows, indexes and database version; it is not a raw copy of live WAL files.
- `metadata.json`: export format and app/device versions, time zone, calibration, and labels/launcher eligibility for packages represented in the database.
- `diagnostic-report.txt`: the screen's diagnostic snapshot, including the displayed longest sessions, their exact start timestamps and sequence keys, app allocations, and Other durations in milliseconds.

The screen report is captured before the database copy. Its sync cursor can lag behind the database if collection or analysis is running; use the reported period and session keys when comparing them. Package metadata describes export-time eligibility, which can change when apps are installed or removed.

No network permission or automatic upload is added. The user chooses the export destination. Temporary snapshot files are held in private app cache and cleaned up after success, failure, or cancellation. Exported archives contain actual app usage history and must be kept outside the repository.

## Initial inspection

Extract the archive outside the checkout and open the database read-only:

```sh
sqlite3 -readonly /path/to/foldlytics.db 'PRAGMA integrity_check;'
sqlite3 -readonly /path/to/foldlytics.db '.tables'
```

Join `inner_display_sessions` to `inner_display_session_app_usage` using both `opened_at_millis` and `opened_sequence_at_timestamp`. An empty app allocation already present in the database points to event analysis; allocations present there but absent from the screen require checking launcher eligibility and presentation. Replay `usage_events` with timestamp/sequence ordering and the matching checkpoints, sync gaps, and exported calibration to investigate candidate app states.
