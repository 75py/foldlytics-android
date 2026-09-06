# 1.3.0 release review

Release version: `versionCode = 10`, `versionName = "1.3.0"`.

## Google Play release notes

### 日本語 (ja-JP)

```text
・ホーム画面ウィジェットを追加しました。内側の利用割合をひと目で確認でき、大きく配置すると内側・外側の利用時間と検出した「開いた」回数も表示します。
・ウィジェットでは期間を選択でき、手動で記録を更新できます。
・アプリと共有画像の円グラフを、内側から始まる並び順と共通の配色に揃えました。
```

### English (en-US)

```text
• Added a home-screen widget for your inner display share. Larger layouts also show inner and cover display time and detected opens.
• Choose a widget period and refresh your records manually.
• Unified chart colors across the app and shared images, with the inner display segment first.
```

## Screenshots

See [widget and chart review](screenshots/summary-widget/README.md) for synthetic-data
before/after captures and widget layouts. Store screenshots are refreshed where the
summary layout changes. Historical PR screenshots remain historical evidence.

## Validation

JDK 17 and Android SDK 36:

- `testDebugUnitTest`: 222 JVM tests passed.
- `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` passed. Lint has no
  errors; remaining warnings include SDK/dependency notices and widget layout/text
  suggestions. Native RemoteViews tint is explicitly excluded from the AppCompat-only
  tint check because the launcher inflates framework ImageViews.
- The full non-capture Android suite covered 123 tests on the Pixel 9 Pro Fold AVD,
  Android 16 / API 36. The initial run passed 121; two new test synchronization/selector
  issues were corrected, and all five tests in the affected classes then passed.
  Production behavior was unchanged by those test corrections.
- Widget checks cover calendar boundaries, absent evidence versus measured zero,
  separate settings for two real AppWidgetHost IDs, background refresh without an
  Activity, permission restoration through RemoteViews reapply, light-to-dark host
  reinflation, and returning to Home with the requested period after launch sync.
- Follow-up review added scrolling configuration with safe drawing insets, and
  host-side text autosizing for font changes between widget updates. Focused
  configuration tests cover short English and large-text Japanese windows.
  All 3 focused rendering tests passed, including 48 host-font/state/size/locale
  combinations and 348 visible text fields from the same standard-text payload
  reinflated at 0.85×, 1×, and 2× host text. Bounds and donut-center glyph containment
  are checked directly.
- Store capture: both Japanese and English capture tests passed. Only changed summary
  images and their contact sheets differ from the previous assets.
- Shared-image and chart/accessibility checks: 13 tests passed, including the share
  capture fixture. The new 25% pixel test verifies inner-first order and palette.
- Widget screenshots cover 96 combinations of locale, theme, size, text scale, and
  data state; representative images are included in the review document.
- `check-forbidden-android-permissions.sh` passed for the Debug APK.
- Pixel Launcher was manually used to add and configure the widget on the test AVD.

Independent agents: `gpt-6-astra / high` for widget implementation and a separate
`gpt-6-astra / high` agent for review; `gpt-5.6-luna / high` for chart consistency and
additional rendering assertions. The parent integrated the changes and ran device
validation, screenshot capture, and packaging checks.

No physical foldable was used. Usage Access behavior across manufacturers, physical
fold events, calibration, hinge sensing, and manufacturer-specific background
scheduling remain device-only checks. No Play upload or release publication is performed.
