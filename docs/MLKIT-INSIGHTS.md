# ML Kit usage explanations — alternative to #31

This branch is an alternative to [PR #31](https://github.com/75py/foldlytics-android/pull/31),
based on main. Keep it a draft until the gates below are resolved; it does not replace
or merge the native implementation.

| Choice | PR #31 | This branch |
| --- | --- | --- |
| Runtime | llama.cpp b6500, JNI/CMake/NDK | ML Kit Prompt API through Android AICore |
| Model | Install-time Qwen3-0.6B Q8_0 asset, 639,446,688 bytes, plus internal copy (~1.3 GB combined) | Existing Gemini Nano managed by AICore; no bundled model or native build |
| Availability | App-provided model | Only when `checkStatus()` returns `AVAILABLE` |
| Privacy tradeoff | Local inference without ML Kit | Local inference plus Google's SDK metrics processing |

The dependency is `com.google.mlkit:genai-prompt:1.0.0-beta4`, as documented in
[Google's setup guide](https://developers.google.com/ml-kit/genai/prompt/android/get-started).
Foldlytics never invokes `download()`: `DOWNLOADABLE`, `DOWNLOADING`, and `UNAVAILABLE`
do not initiate model provisioning. AICore may independently obtain configurations
and models; this is not a promise that the device never contacts Google.

Home automatically requests a short explanation of aggregated usage facts while its
lifecycle is `RESUMED`. Leaving Home or losing that state cancels the request.
Successful explanations are cached under `noBackupFilesDir`; the cache is derived
data, excluded from Android backup. Inference does not run in a worker or foreground
service. Google permits GenAI inference only while the app is the top foreground app;
quota and background errors can still occur despite the lifecycle check.
[Device support and execution limits](https://developers.google.com/ml-kit/genai)
vary by model and device. Japanese and English output must be validated on actual
supported hardware; a translated UI does not establish model quality.

## Verification (2026-09-12)

- `./gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest bundleRelease` passed,
  including 15 new analyzer/protocol JVM tests and the R8 release build.
- `bash scripts/check-forbidden-android-permissions.sh app/build/outputs/apk/debug/app-debug.apk` passed;
  the merged release manifest also contains none of the three forbidden permissions.
- 14 targeted instrumentation tests passed on the `Foldlytics_Pixel_9_Pro_Fold_API_36`
  ARM64 emulator (Android 16/API 36): ViewModel (7), cache (1), real SDK availability (1),
  card (3), and existing English/Japanese compact disclosure dialogs (2).
  Run the classes `insight.UsageInsightViewModelTest`, `insight.InsightCacheTest`,
  `insight.MlKitAvailabilityTest`, `ui.UsageInsightCardTest`, and
  `ui.UsageAccessDisclosureDialogTest` under `com.nagopy.android.foldlytics`.
- The emulator has no AICore package. The real SDK test checks availability only;
  it does not download a model or establish successful Gemini Nano inference.
- Release AAB is approximately 6.4 MiB; no model is bundled. This is bundle size,
  not an installed-size estimate or AICore storage estimate.
- [Before](screenshots/mlkit-insights/before.png) and [after](screenshots/mlkit-insights/after.png)
  are UI-test captures with synthetic history and fixed sample text, **not** live model output.

The three facts are recorded/inner-use days, classified device display-time share,
and median active duration of complete inner sessions. App rankings and previous-period
comparisons are intentionally outside this initial scope. Generation requires at least
14 recorded days in the 30-day window, 90% classified observed time, and sync through
yesterday. Missing days are not assumed unused. Numeric/format checks reject some bad
outputs but cannot prove semantic correctness; model-quality evaluation remains a gate.

## Privacy assessment

ML Kit processes prompt contents and generated text on-device and does not send those
contents to Google. Separately, it sends API metrics to Google for performance,
debugging, maintenance, improvement, and abuse detection. Google places responsibility
for informing users on the app developer.
[ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms)

The [SDK data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
explicitly covers `genai-prompt`: device/app details, user/device/other and installation
identifiers, latency, API configuration, input/output sizes, feature versions, event
types, error codes, and configured languages are used for diagnostics and usage
analytics. It describes HTTPS transport. Input/output **sizes** are distinct from
their contents. Do not label this SDK collection anonymous or conclude that no data
is collected merely because inference is local. Final Play categories must reflect
the shipped dependency and actual integration.

Foldlytics adds no app-owned analytics or telemetry code. The SDK adds the
`com.google.android.apps.aicore.service.BIND_SERVICE` permission and AICore package
visibility for access to its on-device service; it also contributes non-exported
initialization and transport components. The existing `INTERNET`/`ACCESS_NETWORK_STATE` manifest removals and
forbidden-permission verification remain in force. Their presence does not establish
that AICore or Google Play services cannot transmit SDK metrics. A build/manifest check
also cannot prove the full behavior of those separate services on a device.

The [published privacy policy](https://www.nagopy.com/privacy-policy/), inspected on
2026-09-12 (revision 2026-08-16), documents usage aggregation and diagnostics but not
generated explanations or ML Kit metrics. Its Foldlytics section says no analytics
SDKs are used. The repository's updated disclosure and listing draft therefore do
not make the currently published policy sufficient for this branch.

## Merge and release gates

- Confirm merged debug and release manifests retain the existing permission policy
  and pass the forbidden-permission check. Do not enable networking to make the SDK work.
- Run the documented unit tests, debug build, and lint; verify lifecycle cancellation,
  cache invalidation, unavailable/failure states, and bounded generation.
- Record device, Android/AICore/model versions, Japanese and English sample quality,
  latency, repeated-generation battery/thermal behavior, and foreground transitions.
  Successful supported-device inference and thermal validation are still pending.
- Approve and publish the external privacy-policy revision explaining local generated
  explanations, SDK metrics, purposes, recipients, and relevant Google policy links;
  review and update Play Data safety and publish the matching listing. This checkout
  cannot publish those external changes. The Home card and drawer disclose SDK
  metrics to existing users too; confirm the appropriate disclosure/consent path
  before release rather than relying only on permission setup.
- Resolve target-audience eligibility under the
  [GenAI additional terms](https://developers.google.com/ml-kit/genai-terms): the API
  client must not be directed toward or likely accessed by people under 18. This is
  a product/terms assessment still pending, not a claim that adding an age label or
  a dialog would satisfy the restriction. No age-gating flow is added in this PR.

Keep the policy/publication and audience decisions explicit in PR review. This
technical assessment does not establish legal compliance or finalize Play answers.
