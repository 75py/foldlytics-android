# Fold-state split-screen validation

This directory records the manual validation of inner-display session attribution when two apps are visible at once.

## Test environment

- A clean, isolated Pixel 9 Pro Fold AVD; no account or personal usage history.
- Android 16 / API 36, `google_apis` ARM64 system image.
- Foldlytics debug build with Usage Access granted.
- Android device states: `OPENED` (`2`, 852 × 883 dp) and `CLOSED` (`0`, 443 × 994 dp).

The emulator validates the Android configuration and usage-event path. A physical foldable still needs separate hardware validation for its display and hinge behavior.

## Procedure and result

1. In `OPENED`, create an actual Android split screen from Overview with Settings above Clock, keep it visible, collapse the divider, then switch to `CLOSED`.
2. Refresh Foldlytics and open the saved inner-display-session detail.
3. In `CLOSED`, repeat the Settings + Clock split-screen procedure and refresh Foldlytics.

The inner run produced one saved opening and an inner-active duration of 85,563 ms (shown by the UI as 1 min 25 sec). Its detail reported the simultaneous Settings + Clock interval as 45 sec. The outer run did not add an inner opening: after its refresh, the saved inner duration and opening count were unchanged.

The local, test-only event record matches that result: the inner configuration opened at `2026-09-06 10:29:42.914` (852 × 883 dp) and closed at `10:31:08.512` (443 × 994 dp). Settings and Clock were both resumed from `10:30:21.182`/`.184` until Clock paused at `10:31:06.964`, which supplies the displayed 45-second simultaneous interval after whole-second formatting. The short non-app portions of the session are expected setup and navigation activity. The raw database was inspected only for this reconciliation and is not included here.

Attribution reconciles in milliseconds. Existing duration formatting floors each displayed row to whole seconds, so adding the displayed seconds can differ slightly from the displayed session total.

## Automated verification and performance

213 JVM tests and 34 targeted Android tests passed, covering replay, Room snapshots, configuration history, session UI, and localization. Debug and Android-test APK builds passed; lint reported no errors and nine warnings in unchanged files. The 320dp English/Japanese fixtures assert that complete combination labels and start timestamps are not clipped.

The vertical-icon layout also passed 22 targeted session, app-list, and localization tests, plus the JVM suite, build, and lint checks. Simultaneous entries use two 32dp icons stacked with a 4dp gap in the same 44dp-wide column as singleton icons. The combination name and a separate localized subtitle sit beside the stack; the duration stays right-aligned and vertically centered. The fixture checks icon dimensions, non-overlap, column placement, text overflow, and the complete accessibility description.

On this emulator, the file-backed Room benchmark used 365 synthetic days and 77,380 events. After closing and reopening the database, the initial load took 2,095 ms; three warm loads took 769, 771, and 772 ms. Each load includes diagnostic analysis and the selected-session replay. The OS page cache was not flushed. See [the repeatable measurement procedure](../../MEASUREMENT.md#repeatable-session-replay-performance-check).

## Screenshots

| Evidence | Screenshot |
| --- | --- |
| Actual inner-display Settings + Clock split screen | [manual-inner-app-split.png](manual-inner-app-split.png) |
| Actual outer-display Settings + Clock split screen | [manual-outer-app-split.png](manual-outer-app-split.png) |
| Actual saved session detail, including the simultaneous interval | [manual-session-detail.png](manual-session-detail.png) |
| Current English session-detail fixture | [after-en.png](after-en.png) |
| Current Japanese session-detail fixture | [after-ja.png](after-ja.png) |
| Previous English layout, before vertically stacking icons | [before-vertical-en.png](before-vertical-en.png) |
| Previous Japanese layout, before vertically stacking icons | [before-vertical-ja.png](before-vertical-ja.png) |
| Legacy English attribution fixture | [before-en.png](before-en.png) |
| Legacy Japanese attribution fixture | [before-ja.png](before-ja.png) |

The six English/Japanese detail images are synthetic UI fixtures with fixed example data; they demonstrate the before/after presentation and contain no captured usage history. Legacy attribution is rendered in the current card, not an older APK. The `before-vertical-` images preserve the prior layout of the same exclusive entries. The three `manual-` images are controlled emulator evidence; the saved-session detail was recaptured with the vertical-icon layout.
