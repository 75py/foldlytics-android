# PR #16 app-ranking comparison

These unmodified screenshots use synthetic, in-memory data from
`StoreScreenshotCaptureTest`, with generic app names and no user usage history.

- Before: main at `dc12e5b8a010cd63c660faf027be0f8f6346fc2b`.
- After: integrated PR #16 at `074fba2a8eb392b93221a0a7685738e52af1b007`.
- Device: `Foldlytics_PR16_Final_20260905` foldable AVD, Android API 36,
  CLOSED (state 0), 1080 × 2424 pixels, 390 dpi, font scale 1.0, awake and unlocked.

## Total ranking: same data before and after

The four total-ranking captures use the same existing 90-day synthetic dataset. The after
view adds the Usage time / Display share selector, classified-time wording, and per-app shares;
the ranking order and measured durations remain the same.
The even-split exclusion note appears only in Display share, where it applies.

| Locale | Before | After |
| --- | --- | --- |
| English | ![Before: total ranking in English](before-total-en.png) | ![After: total ranking with display-share selector in English](after-total-en.png) |
| Japanese | ![Before: total ranking in Japanese](before-total-ja.png) | ![After: total ranking with display-share selector in Japanese](after-total-ja.png) |

## New display-share view: separate synthetic examples

The share captures use a separate dataset to make the behavior visible. Reading has 60 minutes
inner / 40 minutes outer and ranks ahead of Photos' 5 minutes of inner-only use. Messages has
60 minutes outer / 40 minutes inner and ranks ahead of Maps' 5 minutes of outer-only use.
Reading and Messages each show 10 minutes of display-undetermined time outside their shares.
Browser's even split belongs to neither majority list. These values are not a before/after
comparison with the total-ranking dataset above.

| Locale | Inner higher | Outer higher |
| --- | --- | --- |
| English | ![Inner-majority app shares in English](inner-share-en.png) | ![Outer-majority app shares in English](outer-share-en.png) |
| Japanese | ![Inner-majority app shares in Japanese](inner-share-ja.png) | ![Outer-majority app shares in Japanese](outer-share-ja.png) |

The coordinator also validated the UI on the OPENED display (state 2, 2076 × 2152 pixels),
with no clipping found in either state. The eight PNGs here are the CLOSED captures; no opened
captures are included. Synthetic UI checks do not establish real sensor accuracy: physical-device
usage access, calibration, and hinge sensing remain unverified.

Validation at the after-source: 142 JVM tests passed; lint reported zero errors and nine existing
warnings; both APK builds passed. The full connected suite passed 82/82 on CLOSED, and direct
instrumentation of `AppUsageScreenTest` plus `StoreScreenshotCaptureTest` passed 11/11 on OPENED.
The coordinator reviewed the updated captures and the scoped copy fix.

See [capture instructions](../../../store-assets/google-play/README.md#display-share-review-screenshots).
