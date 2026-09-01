# Google Play preview assets

Store listing copy is in [LISTING.md](LISTING.md).

`ja-JP/` and `en-US/` contain the upload-ready Japanese and English assets. The current
dimensions follow the
[Google Play preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
checked on 2026-08-30.

## Upload-ready files

### Japanese (`ja-JP`)

- `ja-JP/feature-graphic.png`: 1024 x 500, 24-bit PNG without alpha.
- `ja-JP/phone/01-display-time.png`: 1080 x 1920 phone screenshot.
- `ja-JP/phone/02-inner-sessions.png`: 1080 x 1920 phone screenshot.
- `ja-JP/phone/03-long-term-trends.png`: 1080 x 1920 phone screenshot.
- `ja-JP/phone/04-open-count.png`: 1080 x 1920 phone screenshot.
- `ja-JP/phone/05-app-ranking.png`: 1080 x 1920 phone screenshot.
- `ja-JP/phone/06-on-device.png`: 1080 x 1920 phone screenshot.

### English (`en-US`)

- `en-US/feature-graphic.png`: 1024 x 500, 24-bit PNG without alpha.
- `en-US/phone/01-display-time.png`: 1080 x 1920 phone screenshot.
- `en-US/phone/02-inner-sessions.png`: 1080 x 1920 phone screenshot.
- `en-US/phone/03-long-term-trends.png`: 1080 x 1920 phone screenshot.
- `en-US/phone/04-open-count.png`: 1080 x 1920 phone screenshot.
- `en-US/phone/05-app-ranking.png`: 1080 x 1920 phone screenshot.
- `en-US/phone/06-on-device.png`: 1080 x 1920 phone screenshot.

`generate-phone-screenshots.sh` also rebuilds `previews/ja-JP-phone-contact-sheet.png` and
`previews/en-US-phone-contact-sheet.png` after producing the six upload-ready images. Each
review-only contact sheet uses ImageMagick `montage` with a 6x1 tile, `216x384+0+0` geometry,
and 8-bit stripped `PNG24` output. Do not upload the contact sheets to Play Console.

For each locale, the first four phone screenshots satisfy Google's recommendation to provide at
least four portrait app screenshots at 1080 px or higher. The added headline area is less than
20% of each image, and the captured app UI remains the main content.

## Suggested alt text

### Japanese (`ja-JP`)

- Feature graphic: `橙色の外側と青色の内側が折り重なる抽象図と、Foldlyticsの利用目的を示すコピー。`
- 01: `90日間の外側・内側の利用時間、内側割合、データ充足率、検出した開閉回数を表示した利用サマリー。`
- 02: `開いてから閉じるまでの内側画面利用について、中央値・平均値・最長時間と長く使った回のアプリ内訳を表示した画面。`
- 03: `90日間の内側利用割合の推移を折れ線グラフで表示した利用傾向。`
- 04: `検出した開いた回数の推移と期間合計を表示した利用傾向。`
- 05: `内側ディスプレイでの表示時間を基準に、読書やブラウザなどを並べたアプリ利用詳細。`
- 06: `CSV保存、診断共有、利用状況設定、プライバシーポリシーと端末内保存の説明を表示したドロワー。`

### English (`en-US`)

- Feature graphic: `An abstract orange outer surface folds over a blue inner surface beside the Foldlytics name and tagline.`
- 01: `A 90-day Foldlytics usage summary showing cover and inner display time, a 64% inner share, 98% data coverage, and 945 detected opens and closes.`
- 02: `The opening-to-closing inner-display use card showing median, average, longest time, and app breakdowns for the three longest uses.`
- 03: `A Foldlytics line chart showing inner-display share across a 90-day period.`
- 04: `The detected-open trend chart with a total of 945 and no app ranking on the home screen.`
- 05: `The inner-display app usage detail led by Reading, Browser, Photos, and Messages.`
- 06: `The Foldlytics drawer with CSV export, diagnostic sharing, Usage Access settings, privacy policy, and an on-device data notice.`

## Representative data

Both localizations use the same deterministic representative data from
`StoreScreenshotCaptureTest`. The fixture is compiled only into `androidTest`; it is not included
in the release APK and never changes a user's database.

- Record range: 365 calendar days; selected period: 90 days.
- Classified time: 404 hours 27 minutes.
- Cover display: 145 hours 26 minutes.
- Inner display: 259 hours 1 minute (64%).
- Data coverage: 98%.
- Detected opens and closes: 945 each; openings summarized through closing: 930.
- Recent 30-day inner-display share: 7.8 points above the first 30 days.
- Inner-display uses: three long uses of 42, 34, and 27 minutes, with app breakdowns and remaining time grouped as Other.
- App names and package names are generic fixtures, so no user data or third-party app marks are
  present.

The daily values are aggregated with the production `LongTermAnalyzer`, so the totals, ratios,
trend buckets, open counts, and rankings agree with one another.

## Regenerating phone screenshots

1. Start the foldable API 36 test emulator in its opened state and set the display to 1080 x 1920.
   The Compose test host requires the active display; the capture fixture does not use device usage
   history or hinge readings.
2. Build and install `app-debug.apk` and `app-debug-androidTest.apk`.

   ```shell
   ./gradlew --no-daemon installDebug installDebugAndroidTest
   ```

3. Run:

   ```shell
   ./gradlew --no-daemon :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.nagopy.android.foldlytics.ui.StoreScreenshotCaptureTest
   ```

   Use the Gradle connected-test command because direct `am instrument` does not initialize the
   Compose UI test v2 host used by this fixture.

4. Pull the Japanese and English PNG files from:

   ```text
   /sdcard/Android/data/com.nagopy.android.foldlytics.debug/files/store-screenshots/
   /sdcard/Android/data/com.nagopy.android.foldlytics.debug/files/store-screenshots-en/
   ```

   into `raw-ja/` and `raw-en/`, respectively.
5. Run `./generate-phone-screenshots.sh` to generate the six upload-ready images and both
   localized review contact sheets. Set `FOLDLYTICS_STORE_FONT` when the default macOS Hiragino
   font is unavailable.

The script accepts the new capture names (`01-home-summary.png` through `06-drawer.png`) and
temporarily falls back to the checked-in legacy raw names until fresh emulator captures are
available.

`FoldlyticsScreen` accepts an optional `appName` only so the screenshot fixture can render the
public title `Foldlytics` instead of the debug application label. Normal application calls keep
using the localized resource. The test renders `Locale.JAPANESE` and `Locale.US` with generic,
localized app labels and the same calculated values. The capture flow navigates through stable
test tags and semantics: home summary, session details, the two trend modes, inner app usage
details, and the drawer. The app theme also passes the active locale to Compose typography so
`ja-JP` captures use Japanese CJK glyph forms.

## Feature graphic source and prompt

`generated/feature-graphic-background-source.png` was created with the built-in image generation
tool. `generate-feature-graphic.sh` adds exact Japanese and English typography and produces both
upload-ready 1024 x 500 PNG files. The final generation prompt was:

```text
Use case: ads-marketing
Asset type: Google Play feature graphic background, designed for a final 1024 x 500 landscape crop
Primary request: Create an abstract visual for Foldlytics, an on-device analytics app for foldable phone usage.
Scene/backdrop: luminous soft periwinkle-to-blue gradient with subtle depth; avoid pure white, black, and dark gray.
Subject: one elegant folded ribbon or layered surface that transitions from a warm orange outer plane to a cool blue inner plane, plus a few extremely subtle chart-like arcs and dots suggesting analytics without showing readable data.
Style/medium: premium minimal 3D illustration, crisp and contemporary, compatible with a polished Material Design app.
Composition/framing: ultra-wide 2.048:1 composition. Keep the folded form centered-right but fully inside the central safe area. Preserve clean negative space at left-center for exact typography that will be added later. Keep all focal details away from the outer 15 percent so cropping remains safe.
Lighting/mood: bright, clean, trustworthy, quietly optimistic.
Color palette: deep blue #0067A5, warm orange #C44E00, pale periwinkle #D7E3FF, near-white lavender #F9F9FF.
Text: none.
Constraints: no text, no letters, no logos, no app icon, no phone or device imagery, no UI screenshot, no people, no watermark. Keep details simple enough to remain clear at small mobile sizes.
Avoid: busy data dashboards, photorealistic phones, dark backgrounds, neon cyberpunk styling, tiny details, edge-heavy composition.
```
