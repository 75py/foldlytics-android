# Compact widget layout

Native RemoteViews captures on the Pixel 9 Pro Fold emulator, Android 16 / API 36,
390 dpi. Values are synthetic. Launcher cell dimensions depend on the home-screen grid;
the provider now requests 2×2 cells and allows resizing to 140×140 dp.

The narrow widget keeps its donut at every system font setting. The wide widget groups
inner time, cover time, and opens beside the chart. Increasing widget height does not
increase the spacing between metric rows. The opens label no longer includes “detected”.

| Layout | Before | After |
| --- | --- | --- |
| Narrow | ![Before](before-small.png) | ![2×2](ja-light-small-140dp-1.0-ready.png) |
| Wide | ![Before](before-wide.png) | ![Wide](ja-light-wide-140dp-1.0-ready.png) |
| Large system text | ![Before](before-large-text.png) | ![Large text](ja-light-small-140dp-2.0-ready.png) |

| Taller narrow | Taller wide | English, dark |
| --- | --- | --- |
| ![Tall narrow](ja-light-small-280dp-1.0-ready.png) | ![Tall wide](ja-light-wide-280dp-1.0-ready.png) | ![English dark](en-dark-wide-140dp-2.0-ready.png) |

| No data | Update failed | Permission required |
| --- | --- | --- |
| ![No data](ja-light-wide-140dp-1.0-no_data.png) | ![Failed](en-dark-wide-140dp-2.0-update_failed.png) | ![Permission](ja-light-small-140dp-2.0-permission_required.png) |

`SummaryWidgetScreenshotTest` generates 288 fixtures: two widths, three heights
(140/180/280 dp), two locales, two themes, three font sizes, and four data states.
`SummaryWidgetRenderingTest` checks actual arc pixels, permission-state reapplication,
host theme/font changes, text bounds, donut-center containment, and fixed metric spacing.
The font-change matrix checks 180 combinations and 1,152 visible text fields, including
permission-required states.
Physical device and manufacturer launcher behavior remain unverified for this update.
