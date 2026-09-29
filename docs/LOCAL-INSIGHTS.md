# Local usage explanations

Home describes the previous 30 complete local calendar days in two or three
sentences, independently of the chart period selector. No prompt entry or
generation button is required. Leaving Home or pausing cancels generation.

## Offline boundary and distribution

No ML Kit, AICore, cloud inference, telemetry, network permission or runtime
downloader is used. Native inference runs directly in the app process. Evidence,
text and cache stay in app-private storage; production prompts are never logged.
Existing backup/transfer exclusions and data deletion behavior are unchanged.
The privacy policy is unchanged: this describes the same local usage evidence.

The model ships in an **install-time asset pack**, using Android AssetManager
without the Play delivery SDK. Store installation delivery is distinct from app
runtime and receives no usage evidence. The asset is copied to noBackupFilesDir
with size/SHA-256 verification on first use. Asset plus extracted copy require
roughly 1.3 GB of storage, excluding app code and other data.

- Engine: llama.cpp b6500, commit `a7a98e0fffed794396b3fbad4dcdbbc184963645`, MIT.
  CMake pins/verifies the source archive and builds CPU-only static dependencies.
  CURL, server, RPC and dynamically loaded backends are disabled.
- Model: official [Qwen/Qwen3-0.6B-GGUF](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF),
  `Qwen3-0.6B-Q8_0.gguf`, Apache-2.0.
- Model revision: `23749fefcc72300e3a2ad315e1317431b06b590a`.
- Bytes: `639446688`.
- SHA-256: `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031`.

Both license texts are packaged with the app and available from **Model and
licenses** on the card. The model binary is ignored by Git. Only the developer
or CI machine fetches source/model dependencies.

```sh
bash scripts/prepare-insight-model.sh
./gradlew bundleRelease
```

The asset-pack manifest task requires model verification, so a bundle cannot
silently ship without the fixed weights. For standalone test APKs, which do not
receive install-time asset packs:

```sh
./gradlew assembleDebug -PincludeInsightModelInApk=true
```

Ordinary `assembleDebug` intentionally excludes the large model: it can test
ordinary functionality and fake inference, but cannot generate explanations.
Use NDK 28.2.13676358 and CMake 3.22.1. arm64-v8a and x86_64 are compiled.
ABI compatibility alone does not guarantee acceptable performance on a device.
The card is hidden on low-RAM devices and devices with less than 4 GiB of total
memory. This is an admission threshold, not a tested-device certification.

## Evidence and generation

The pure analyzer extracts display ratios, classified-usage days, complete
session median, inner-display app rankings and a previous-period comparison.
Each app ratio uses that app's own classified time; overlapping app times are
not divided by device time. Missing records never imply non-use.

Generation requires sync through yesterday, 14 classified-usage days in the
period and 90% classified observed time. Comparisons require 24 such days in
both periods, 95% classification and no recorded evidence-gap days. These are
conservative product thresholds, not statistical confidence or proof that all
usage was collected. Partial/duplicate/wrong-zone daily rows and the potentially
partial first day are excluded. Only complete sessions entirely in the period
contribute to the median, including valid zero-duration sessions.

Only usage days, device display share and complete-session median are passed
to the model as short localized sentences with evidence IDs (at most three).
App rankings, comparisons and limitations remain in the evidence view; mixing
them into the small model's prompt caused semantic errors in synthetic tests.
The parser
rejects unknown/duplicate IDs, unexpected numbers, malformed/overlong/truncated
output and reasoning tags. This is structural validation, **not proof of semantic
faithfulness**: ratio swaps, mean/median confusion and invented causes still need
output evaluation. App names are untrusted data, not prompt instructions.

The cache identity covers evidence, calendar period/timezone, language,
calibration, model hash and prompt version. Home rechecks at most once per
minute. The same failed input is not retried repeatedly in one process;
navigation cancellation may resume. Failed generation leaves charts usable.

## Validation

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
bash scripts/check-forbidden-android-permissions.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest -PincludeInsightModelInApk=true \
  -Pandroid.testInstrumentationRunnerArguments.localLlm=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nagopy.android.foldlytics.insight.LocalInsightIntegrationTest,com.nagopy.android.foldlytics.insight.UsageInsightViewModelTest,com.nagopy.android.foldlytics.ui.UsageInsightCardTest
```

Only run device tests on an emulator or dedicated test device: installation can
replace the debug app. Real-model tests use synthetic evidence and produce a
test-only evaluation file. No user diagnostic archive is used.

Physical-device latency, peak memory, temperature/battery impact, Google Play
installation delivery and broader language-quality evaluation remain release
checks. Host/emulator smoke tests alone do not settle these criteria.

### Initial synthetic evaluation (2026-09-12)

- JVM tests, debug lint/build and the R8-enabled release bundle passed.
- Seven instrumented tests passed on Pixel 9 Pro Fold API 36 **AVD** (Android 16,
  arm64): real-model generation/cancellation, cache, lifecycle recovery and UI.
- With optimized native code, Japanese/English × inner share 0%/70% generated
  in 5.9–11.2 seconds on that emulator. The four outputs preserved the supplied
  counts, display assignments and median in manual inspection. These timings
  exclude the initial asset extraction and are not physical-phone benchmarks.
- Earlier mixed-fact prompts produced wrong Japanese ratios/day meanings even
  when the structural parser accepted them. Restricting and simplifying input
  corrected the four smoke cases, not all possible histories. A separate host
  case with 14 recorded days / 8 inner days invented 6 cover days; the unexpected
  number is rejected by the parser. Broader semantic evaluation remains a
  **merge/release gate**, not a solved problem.
- No forbidden Android permissions were found in the built debug APK.
