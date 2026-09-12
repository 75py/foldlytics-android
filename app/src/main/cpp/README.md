# Offline usage insight engine

The Android library `foldlytics_llama` embeds the CPU-only llama.cpp engine at
commit `a7a98e0fffed794396b3fbad4dcdbbc184963645` (b6500). CMake downloads that
source archive during the developer build and verifies SHA-256
`4d78e6aa4a9124b58dff994416525294fbab2990fe905a640d4cbd26bf563a31`.
No model downloader, HTTP server, curl, RPC, or dynamically loaded backend is
built into the Android library. This dependency is MIT licensed; see
`LLAMA-LICENSE.txt`. Distribute that notice with the app.

The supported model is the official Qwen/Qwen3-0.6B-GGUF Q8_0 model, licensed
under Apache-2.0. Model bytes do not belong in Git. The app's model preparation
and delivery must verify its fixed SHA-256 before exposing a local file to the
engine. See the repository's model preparation script and accompanying model
license notices. Model identity checks in native code are compatibility checks,
not a replacement for checksum verification.

Sources:

- https://github.com/ggml-org/llama.cpp/tree/a7a98e0fffed794396b3fbad4dcdbbc184963645
- https://huggingface.co/Qwen/Qwen3-0.6B-GGUF
- https://huggingface.co/Qwen/Qwen3-0.6B (non-thinking sampling defaults)

`LlamaInsightEngine.generate(File, systemPrompt, userPrompt, maxOutputTokens)`
runs on one shared background worker. Each call loads its own model and fresh
4096-token context and releases both on success, failure, or cancellation. At
most four CPU threads are used. Prompts are evaluated in 128-token batches and
must fit together with the requested output (1–512 tokens); overflow fails
explicitly rather than silently losing facts. Qwen's metadata chat template is
applied with an empty completed `<think>` block to select non-thinking mode.
Generation uses temperature 0.7, top-p 0.8, top-k 20, and seed 42.

Coroutine cancellation sets an atomic native flag. Model loading checks a
progress callback, and CPU decoding checks an abort callback plus token/batch
boundaries. Native initialization or allocation may complete before cancellation
can be observed. A synchronized request lifetime prevents cancellation from
touching freed storage. Logs are suppressed and prompts are never persisted by
this engine. Generated text is untrusted; the caller must validate its fact IDs
and numbers before displaying it.

## Developer verification

Build the application using the project's Gradle commands. NDK 28's flexible
page support and a 16-KiB linker alignment are used for current Android devices.
The JNI class and native method names must be preserved by release shrinking.

For a host smoke test, configure this directory with CMake, a JDK's `JAVA_HOME`,
and `-DFOLDLYTICS_BUILD_SMOKE_TEST=ON`; build target
`foldlytics_inference_smoke`, then pass the verified model path to the executable.
The smoke test checks pre-cancellation, cancellation during generation, context
overflow rejection, and produces Japanese and English summaries from synthetic
facts only. It does not measure Android
latency or guarantee the model's factual accuracy.
