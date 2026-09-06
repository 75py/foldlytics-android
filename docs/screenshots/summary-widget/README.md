# Summary widget and chart consistency review

Device: Foldlytics Pixel 9 Pro Fold AVD, Android 16 / API 36, opened display at
1080 × 1920 pixels, 390 dpi. All usage values in these captures are synthetic.
The widget fixtures render real native `RemoteViews`; they are not design mockups.

## Home and shared image

Home before images are the checked-in 1.2.1 store captures. The share before image
was captured with the pre-change debug APK. After images use 1.3.0 (version code 10).

| Surface | Before | After |
| --- | --- | --- |
| Home, Japanese | ![Home before](before-home-ja.png) | ![Home after](after-home-ja.png) |
| Home, English | ![Home before](before-home-en.png) | ![Home after](after-home-en.png) |
| Shared image, Japanese | ![Shared image before](before-share-ja.png) | ![Shared image after](after-share-ja.png) |

The inner segment now starts at 12 o'clock and runs clockwise on all three surfaces.
Home legends and metrics, and shared-image metrics, list inner before cover.
The share image uses the same light palette as the home chart.

## Widget layouts

Fixtures cover the minimum 140 × 180 dp small layout and 280 × 180 dp wide layout.
Actual launcher cell counts depend on grid and display settings. Tapping the period
opens widget configuration; the refresh icon requests a sync; the body opens the app.
Larger system text settings use a text summary to preserve legibility.

| Locale | Small, light | Wide, light | Wide, dark |
| --- | --- | --- | --- |
| Japanese | ![Small Japanese](ja-light-small-1.0-ready.png) | ![Wide Japanese](ja-light-wide-1.0-ready.png) | ![Dark Japanese](ja-dark-wide-1.0-ready.png) |
| English | ![Small English](en-light-small-1.0-ready.png) | ![Wide English](en-light-wide-1.0-ready.png) | ![Dark English](en-dark-wide-1.0-ready.png) |

| Edge case | Capture |
| --- | --- |
| No classified data | ![No data](ja-light-wide-1.0-no_data.png) |
| Usage Access required | ![Permission required](en-light-small-1.0-permission_required.png) |
| Failed update, larger text | ![Failed update](en-dark-wide-1.3-update_failed.png) |
| 2× system text, Japanese | ![Large text](ja-light-small-2.0-ready.png) |
| 2× system text, permission required | ![Large permission state](en-dark-wide-2.0-permission_required.png) |

## Reproduction

Use JDK 17, SDK 36, and a disposable emulator. Do not run connected tests on a phone
holding real usage history. Run `SummaryWidgetScreenshotTest` through
`connectedDebugAndroidTest` or the installed AndroidJUnitRunner; it creates 96 PNGs
in the target app's external-files `summary-widget-review/` directory, combining
Japanese/English, light/dark, small/wide, font scales 1.0/1.3/2.0, and four data states.
Only representative images are committed here.

`SummaryWidgetRenderingTest` additionally verifies permission-to-ready reapplication
and re-inflation of the same light-created payload in a dark host, including actual
arc pixels. `SummaryWidgetBindingTest` uses two real AppWidgetHost IDs to verify
independent settings and refresh without an Activity, then deletes only its test host.

The store image workflow is documented in
[Google Play assets](../../../store-assets/google-play/README.md).
Physical fold/unfold sensing and manufacturer-specific background restrictions
remain outside this emulator review.
