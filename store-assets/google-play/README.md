# Google Play preview assets

Store listing copy is in [LISTING.md](LISTING.md).

`ja-JP/` and `en-US/` contain the upload-ready Japanese and English assets. The current
dimensions follow the
[Google Play preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
checked on 2026-08-30.

The phone screenshots were redesigned on 2026-09-27: six Japanese and six English images,
built by `generate-phone-screenshots.sh` from the raw captures. Each image has a two-line
question headline with a one-line subtitle, and crops only the relevant app card at close to
its captured size instead of showing the full screen. Explanatory notes, the status bar and the
drawer are left out. The background and the folded ribbon come from the feature graphic.

The inputs in `raw-ja/` and `raw-en/` were captured for 1.3.1 on 2026-09-27 with
`StoreScreenshotCaptureTest`: Pixel 9 Pro Fold AVD, Android 16 / API 36, opened at
1080 × 1920, 390 dpi, font scale 1.0. The same fixture supplies the app screens,
current widgets, and share images in both languages. English captures use "Cover" and
"Opens" consistently; the refreshed Japanese PNGs are byte-identical to the previous set.

## Upload-ready files

### Japanese (`ja-JP`)

- `ja-JP/feature-graphic.png`: 1024 x 500, 24-bit PNG without alpha.
- `ja-JP/phone/01-display-time.png`: 外側と内側、どっちが多い？ (period and usage summary)
- `ja-JP/phone/02-inner-apps.png`: 内側で使うアプリは？ (app usage selectors and top two apps)
- `ja-JP/phone/03-per-opening.png`: 開いたら、何分使う？ (two longest inner-display uses)
- `ja-JP/phone/04-detected-opens.png`: 1日に何回開いてる？ (detected-open trend)
- `ja-JP/phone/05-widget.png`: ホーム画面でさっと確認 (wide and small widgets)
- `ja-JP/phone/06-share-image.png`: 結果を画像でシェア (the generated share image)

### English (`en-US`)

- `en-US/feature-graphic.png`: 1024 x 500, 24-bit PNG without alpha.
- `en-US/phone/01-display-time.png`: Cover or inner display?
- `en-US/phone/02-inner-apps.png`: Which apps get the inner display?
- `en-US/phone/03-per-opening.png`: How long do you stay unfolded?
- `en-US/phone/04-detected-opens.png`: How often do you unfold?
- `en-US/phone/05-widget.png`: Check it from your home screen
- `en-US/phone/06-share-image.png`: Share your summary image

All phone screenshots are 1080 x 1920, 24-bit PNG without alpha. `generate-phone-screenshots.sh`
also rebuilds `previews/ja-JP-phone-contact-sheet.png` and `previews/en-US-phone-contact-sheet.png`
by resizing each image to 216 x 384 and joining one row as 8-bit `PNG24`. Check headline and
key-number legibility on these sheets; do not upload them to Play Console.

For each locale, the first four phone screenshots satisfy Google's recommendation to provide at
least four portrait app screenshots at 1080 px or higher. The headline area is less than 20% of
each image, and the captured app UI remains the main content.

## Capture inputs

The app screenshots use the 90-day representative period. Screenshot 02 selects the inner-time
sort. The two widgets use the latest 30 days from the same deterministic daily data (67% inner),
so their visible period differs from the app's 90-day summary (64% inner). Their update label
uses a fixed Asia/Tokyo reference date, and they are captured at the exact size used by the
template so text is not enlarged. Both share images use the 90-day summary. The store capture
omits the emulator device name when drawing the share image.

## Suggested alt text

### Japanese (`ja-JP`)

- Feature graphic: `橙色の外側と青色の内側が折り重なる抽象図と、Foldlyticsの利用目的を示すコピー。`
- 01: `90日間の外側・内側の利用時間、内側64%の円グラフ、検出した「開いた」回数を表示した利用サマリー。`
- 02: `内側で長く使ったアプリの上位2件と、それぞれの外側・内側の利用時間と割合。`
- 03: `開いてから閉じるまでに内側画面を長く使った回と、そのアプリ別の内訳。`
- 04: `検出した「開いた」回数の推移グラフと、期間合計・観測日あたりの回数。`
- 05: `内側の割合と利用時間を表示するホーム画面ウィジェット（横長と正方形）。`
- 06: `内側・外側の利用時間と開いた回数をまとめた、共有用のサマリー画像。`

### English (`en-US`)

- Feature graphic: `An abstract orange outer surface folds over a blue inner surface beside the Foldlytics name and tagline.`
- 01: `A 90-day Foldlytics usage summary showing cover and inner display time, a 64% inner share, and 945 detected opens.`
- 02: `The top two apps by inner display time, with cover and inner time and percentages for each.`
- 03: `The longest inner-display uses between opening and closing, with app breakdowns.`
- 04: `The detected-open trend chart with the period total and opens per observed day.`
- 05: `Home-screen widgets in wide and square sizes showing the inner display share and time.`
- 06: `A shareable summary image with inner and cover display time and detected opens.`

## Representative data

Both localizations use the same deterministic representative data from
`StoreScreenshotCaptureTest`. The fixture is compiled only into `androidTest`; it is not included
in the release APK and never changes a user's database.

- Record range: 365 calendar days; selected period: 90 days.
- Classified time: 404 hours 27 minutes.
- Cover display: 145 hours 26 minutes.
- Inner display: 259 hours 1 minute (64%).
- Data coverage: 98%.
- Detected opens: 945; openings summarized through closing: 930.
- Recent 30-day inner-display share: 7.8 points above the first 30 days.
- Inner-display uses: three long uses of 42, 34, and 27 minutes, with app breakdowns and remaining time grouped as Other.
- App names and package names are generic fixtures, so no user data or third-party app marks are
  present.

The daily values are aggregated with the production `LongTermAnalyzer`, so the totals, ratios,
trend buckets, open counts, and rankings agree with one another.

## Regenerating phone screenshots

1. Start the foldable API 36 test emulator in its opened state and set the display to 1080 x 1920.
   The helper expects the AVD name `Foldlytics_Pixel_9_Pro_Fold_API_36` by default. For an
   equivalently configured AVD with another name, set `FOLDLYTICS_STORE_AVD` when running the
   helper. The Compose test host requires the active display; the capture fixture does not use
   device usage history or hinge readings.
2. From the repository root, run the capture helper:

   ```shell
   ./store-assets/google-play/capture-store-screenshots.sh
   ```

   For example, to use an AVD named `Pixel_9_Pro_Fold_API_36`:

   ```shell
   FOLDLYTICS_STORE_AVD=Pixel_9_Pro_Fold_API_36 \
     ./store-assets/google-play/capture-store-screenshots.sh
   ```

   The helper refuses physical or unknown devices, verifies the selected API 36 emulator and
   `ro.kernel.qemu=1`, sets `OPENED` and 1080 x 1920, then runs the Gradle connected test. The
   fixture writes PNGs to its dedicated shared Downloads directories so the host can pull all
   eighteen files after the test and before any unrelated cleanup. Each file is checked as a PNG
   with the expected dimensions: 1080 x 1920 for app screens, 956 x 478 or 478 x 478 for widgets,
   and 1200 x 675 for share images. They are copied into `raw-ja/` or `raw-en/` and passed to
   `generate-phone-screenshots.sh` for the upload-ready images and contact sheets. The helper
   removes only its fixture directories from the test emulator when it exits.

   Use this Gradle connected-test helper to automate building and installing the test APKs.
   Keep the emulator screen awake and unlocked during capture. Direct `am instrument` can also
   run the installed fixture; captures were verified after waking and unlocking the emulator.

3. The generator needs ImageMagick 7 (`magick`) and Python Playwright with Chromium:

   ```shell
   python3 -m pip install playwright
   python3 -m playwright install chromium
   ```

   With `uv` on PATH, the script runs with its inline dependencies instead. Headlines use
   Noto Sans CJK JP when installed and fall back to Hiragino Sans on macOS.

4. To change headlines, slot order or crop regions, edit `SLOTS` in
   `render-phone-screenshots.py`; the layout and styling live in `phone-template/template.html`.
   Crop regions are in raw-capture pixels, so recheck them after UI changes that move the cards.
   Then run `./store-assets/google-play/generate-phone-screenshots.sh` on its own. The generator
   checks each referenced raw PNG's dimensions before replacing any upload-ready image.

The app-screen capture names describe the rendered screen (`01-home-summary.png` through
`06-drawer.png`), while the helper stores them under the stable raw filenames. In particular,
`05-inner-app-ranking.png` (captured with inner-time sorting) is saved as `05-app-ranking.png`.
The generator reads only the stable raw filenames (`03-trends.png` and `06-on-device.png` are
kept but no longer used). The capture helper removes stale `05-total-app-ranking.png` and
`05-inner-app-ranking.png` raw aliases so new captures do not accumulate extra PNGs.

`FoldlyticsScreen` accepts an optional `appName` only so the screenshot fixture can render the
public title `Foldlytics` instead of the debug application label. Normal application calls keep
using the localized resource. The test renders `Locale.JAPANESE` and `Locale.US` with generic,
localized app labels and the same calculated values. The capture flow navigates through stable
test tags and semantics: home summary, session details, the two trend modes, inner-sorted app usage
details, and the drawer. It also renders the current small and wide widgets and the summary share
image from the same fixture. The app theme passes the active locale to Compose typography so
`ja-JP` captures use Japanese CJK glyph forms.

## Preview video

`generate-preview-video.sh` renders a 30-second portrait preview video for each locale into
`preview-video/output/foldlytics-preview-{ja,en}.mp4` (1080 x 1920, 30 fps, H.264, no audio).
The output directory is ignored by Git; upload the file to YouTube and enter its URL in Play
Console.

- 0-4 s: a folding phone opens from the cover display to the inner display with the listing
  question as the headline.
- 4-26 s: the six phone screenshot slots, 3.7 s each, with the same headlines, subtitles and crop
  regions as the upload-ready screenshots. The summary scene redraws its donut.
- 26-30 s: the ribbon, app icon, name, tagline and on-device/no-ads chips.

The scenes read `SLOTS` from `render-phone-screenshots.py`, so screenshot changes carry over to the
video. The layout and timeline live in `preview-video/template.html`. The donut redraw uses the
donut position measured on the current `01-summary.png` captures (`DONUT` in
`render-preview-video.py`); recheck it when the summary card moves.

```shell
./store-assets/google-play/generate-preview-video.sh          # both locales
./store-assets/google-play/generate-preview-video.sh ja       # one locale
./store-assets/google-play/generate-preview-video.sh ja --frames 2.5,5.2,28
```

`--frames` writes PNG stills for quick checks instead of encoding a video. Rendering takes a few
minutes per locale. The script needs ffmpeg in addition to the screenshot generator's
requirements (ImageMagick 7 and Python Playwright with Chromium). Google Play may autoplay the
preview muted, so every scene carries its message in on-screen text.

## Display-share review screenshots

`StoreScreenshotCaptureTest` also has two independent review scenarios:
`captureJapaneseDisplayShareScreenshots` and `captureEnglishDisplayShareScreenshots`.
The store capture helper selects only the original phone screenshot methods, so these extra
captures do not change the six-image listing workflow.

On the coordinator's API 36 emulator, configured with the same opened 1080 x 1920 display as
above, run the following from the repository root with JDK 17 and SDK 36 configured:

```shell
capture_class=com.nagopy.android.foldlytics.ui.StoreScreenshotCaptureTest
./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$capture_class#captureJapaneseDisplayShareScreenshots,$capture_class#captureEnglishDisplayShareScreenshots"
adb pull /sdcard/Download/Foldlytics/display-share-ja /private/tmp/pr16-display-share-ja
adb pull /sdcard/Download/Foldlytics/display-share-en /private/tmp/pr16-display-share-en
```

Use `ANDROID_SERIAL` to select the intended emulator if needed. Gradle automates building and
installing the test APKs; direct `am instrument` also works with the fixture installed. Keep the
emulator screen awake and unlocked during capture. Each locale produces
`inner-overview.png`, `inner-apps.png`, `outer-overview.png`, and `outer-apps.png`: the overview
starts at the period and selectors; the app capture scrolls the leading card into view. Each run
replaces only its own `display-share-ja` or `display-share-en` MediaStore directory.

Prefer a fresh disposable AVD for review captures. Reinstalling the app can leave MediaStore
files from the previous installation, and new captures may receive a suffix such as `(1)`.
Check the actual output filenames and image dimensions before pulling files, particularly when
switching between closed and opened displays; an unsuffixed file may be an older capture.

These scenarios reuse the store fixture's fixed 90-day period and generic localized labels,
with a separate synthetic app dataset:

| App | Outer | Inner | Display undetermined | Expected group/rank |
| --- | --- | --- | --- | --- |
| Reading | 40 min | 60 min | 10 min | Inner, #1 (60% inner) |
| Photos | 0 min | 5 min | 0 min | Inner, #2 (100% inner) |
| Messages | 60 min | 40 min | 10 min | Outer, #1 (60% outer) |
| Maps | 5 min | 0 min | 0 min | Outer, #2 (100% outer) |
| Browser | 10 min | 10 min | 0 min | Neither (even split) |

Review both locales for selector and card clipping, complementary orange/blue bars, separate
undetermined time, and longer measured use ranking ahead of brief 100% use. The fixture uses
only in-memory data and does not read or modify usage history. These review PNGs are not
automatically copied into the upload-ready store assets.

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
