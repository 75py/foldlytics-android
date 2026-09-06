# 1.2.1 release review

Release version: `versionCode = 9`, `versionName = "1.2.1"`.
The public release before this version was 1.1.1; 1.2.0 was not released.

## Google Play release notes

### 日本語 (ja-JP)

```text
・端末を開いてから閉じるまでの内側画面の利用時間を確認できるようになりました。平均・最長時間や、長く使った回のアプリ内訳を振り返れます。
・同時に使ったアプリは、組み合わせごとに利用時間を表示します。
・アプリごとの外側・内側の利用割合や、各画面でよく使うアプリを比較できるようになりました。
・ホーム画面を整理し、大きな文字でも見やすくしました。
・利用時間の集計、長期間の比較、保存済み全期間のCSV出力を改善しました。
```

### English (en-US)

```text
• See inner-display use from opening to closing, including average and longest times and app breakdowns for your longest sessions.
• See time spent using apps together, grouped by app combination.
• Compare each app's cover and inner display share and find your most-used apps on each display.
• Enjoy a simpler home screen and better layouts with larger text.
• Improved usage calculations, long-term comparisons, and CSV export of all saved history.
```

## Store listing and screenshots

Compared with the repository's prepared 1.2.0 assets:

- Keep the Japanese and English name, short description and full description in
  [LISTING.md](../store-assets/google-play/LISTING.md). Their feature descriptions
  still match 1.2.1; describing simultaneous-app combinations is optional.
- The previous `ja-JP/phone/02-inner-sessions.png` and
  `en-US/phone/02-inner-sessions.png` showed the old Other
  explanation, which omitted app combinations. The current
  UI also supports simultaneous-app rows with vertically stacked icons. A
  single-app example remains valid, but its explanation should be current.
- Recapture also updates the summary's classification explanation (01) and the
  app-ranking view selectors, percentages and guidance (05). All six phone
  screenshots in each language, their raw captures and contact sheets are
  refreshed together. Feature graphics, screenshot headlines, and app icon can
  stay unchanged. This compares committed assets with the code; it does not
  verify what is currently in Play Console.
- This PR uses the existing
  [capture procedure](../store-assets/google-play/README.md#regenerating-phone-screenshots).
  Use the updated assets when preparing the upload.

## Review coverage and outcome

The review used `cf6d83f` (main) and compared the 1.1.1 and 1.2.0 tags with the
current implementation. No release-blocking defect was identified.

- Event aggregation, configuration-delta reconstruction, posture classification,
  screen/lock state, session boundaries, concurrent-app allocation and trends.
- Schema migrations from 1.1.1, raw-event retention, synchronization, cache
  invalidation, snapshot consistency, calibration persistence and CSV export.
- Activity/ViewModel state, all Compose screens, Japanese/English resources,
  diagnostic and image sharing, manifest, backup exclusions and FileProvider.
- Corrected the README descriptions of simultaneous-app session breakdowns.

One low-priority issue remains: if Usage Access has been revoked and loading
saved analysis fails temporarily, the error banner's Refresh button is disabled
because it requires that permission. Selecting a different analysis period retries the
saved-data load. This does not affect normal operation with permission granted;
an analysis-only retry can be addressed separately.

Independent review agents: `gpt-6-astra / xhigh` for aggregation and replay;
`gpt-6-astra / high` for storage/sync; `gpt-6-astra / high` for UI/state/privacy.
The parent integrated findings and ran validation.

## Validation

Using JDK 17 and Android SDK 36:

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease`
  passed with the final version values; 213 JVM tests passed.
- Lint: zero errors and nine warnings (SDK/dependency update suggestions and
  existing bitmap/Canvas KTX suggestions).
- `connectedDebugAndroidTest` passed all 116 non-capture Android tests on the
  Foldlytics Pixel 9 Pro Fold AVD, Android 16 / API 36. This includes Room
  migrations/concurrency, dense histories, state transitions, UI and sharing.
  The four screenshot-capture classes were excluded from that run; the store
  capture methods run separately through the capture helper.
- The two store-capture tests passed with version 1.2.1 on the same AVD, opened
  at 1080 × 1920 pixels, 390 dpi, font scale 1.0. All twelve generated phone
  images were checked at 1080 × 1920; Japanese/English contact sheets were
  visually reviewed. The fixture uses synthetic data, not personal history.
- Packaged-permission checks passed for Debug and Release APKs. The Release APK
  manifest reports version code 9 and version name 1.2.1.
- Release APK/AAB build verification does not perform Play signing or upload.

A physical foldable was not
available for this review: Usage Access, real configuration events, calibration,
hinge sensing and background scheduling still require hardware validation.
Emulator checks cannot establish behavior across manufacturers.
