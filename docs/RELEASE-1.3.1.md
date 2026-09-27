# 1.3.1 release preparation

Release version: `versionCode = 13`, `versionName = "1.3.1"`.

This update unifies English display terminology as **Cover** (or **cover display** in
sentences) and detected opening and closing counts as **Opens** and **Closes**. The app,
widget, shared image, accessibility descriptions, and diagnostics use the same terms.
Japanese text and usage calculations are unchanged.

## Google Play release notes

### 日本語 (ja-JP)

```text
・英語表示の画面名と開閉回数の表記を統一しました。
```

### English (en-US)

```text
• Standardized the Cover and Opens labels across the app, widget, and shared image.
```

## Store images

The 1.3.1 capture fixture regenerated all six phone screenshots in each language.
The changed English output files are `01-display-time.png`, `02-inner-apps.png`,
`04-detected-opens.png`, `05-widget.png`, and `06-share-image.png`, plus the English
contact sheet. `03-per-opening.png` and all Japanese output PNGs are byte-identical
to the previous set. Upload-ready images are under
[`store-assets/google-play/en-US/phone/`](../store-assets/google-play/en-US/phone/).

## Verification

- `./gradlew testDebugUnitTest assembleDebug lintDebug bundleRelease` passed.
- The Release manifest contains version code 13 and version name 1.3.1.
- Seventeen focused instrumentation tests passed for localized resources, app usage,
  and shared-image rendering on the Pixel 9 Pro Fold AVD, Android 16 / API 36.
- Both `StoreScreenshotCaptureTest` language cases passed on the same AVD in the
  opened state at 1080 × 1920 and 390 dpi. The capture helper validated raw PNG sizes,
  then generated the twelve upload-ready screenshots and two contact sheets.
- The changed English screenshots were visually checked for readable Cover and Opens
  labels. The debug APK passed `scripts/check-forbidden-android-permissions.sh`.

The release bundle was built locally; no Play Console upload or release publication
was performed. Foldable hardware behavior was not retested for this copy-only update.
