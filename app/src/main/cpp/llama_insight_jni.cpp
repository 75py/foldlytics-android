#include <jni.h>
#include "llama.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {
constexpr int CONTEXT_TOKENS = 4096;
constexpr int BATCH_TOKENS = 128;
constexpr int MAX_PROMPT_BYTES = 65536;

struct Request {
    std::atomic<bool> cancelled{false};
};

Request * from_handle(jlong handle) {
    return reinterpret_cast<Request *>(static_cast<intptr_t>(handle));
}

void check_cancelled(const Request & request) {
    if (request.cancelled.load(std::memory_order_relaxed)) {
        throw std::runtime_error("Generation was cancelled.");
    }
}

bool abort_decode(void * data) {
    return static_cast<Request *>(data)->cancelled.load(std::memory_order_relaxed);
}

bool continue_loading(float, void * data) {
    return !abort_decode(data);
}

std::string read_bytes(JNIEnv * env, jbyteArray value) {
    if (!value) throw std::runtime_error("Missing generation input.");
    const auto size = env->GetArrayLength(value);
    if (size > MAX_PROMPT_BYTES) throw std::runtime_error("Generation input is too long.");
    std::string result(size, '\0');
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte *>(result.data()));
    if (env->ExceptionCheck()) throw std::runtime_error("Unable to read generation input.");
    if (result.find('\0') != std::string::npos) throw std::runtime_error("Invalid generation input.");
    return result;
}

void throw_java(JNIEnv * env, const char * message) {
    if (!env->ExceptionCheck()) {
        jclass type = env->FindClass("java/lang/IllegalStateException");
        if (type) env->ThrowNew(type, message);
    }
}

std::string generate(Request & request, const std::string & path,
                     const std::string & system, const std::string & user, int max_tokens) {
    check_cancelled(request);
    if (max_tokens < 1 || max_tokens > 512) throw std::runtime_error("Invalid output token limit.");
    static std::once_flag initialized;
    std::call_once(initialized, [] {
        // Model metadata, paths and prompts must not enter device logs.
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        llama_backend_init();
    });

    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.use_mmap = true;
    model_params.progress_callback = continue_loading;
    model_params.progress_callback_user_data = &request;
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
        llama_model_load_from_file(path.c_str(), model_params), llama_model_free);
    check_cancelled(request);
    if (!model) throw std::runtime_error("Unable to load the local language model.");

    char architecture[64]{};
    llama_model_meta_val_str(model.get(), "general.architecture", architecture, sizeof(architecture));
    if (std::string(architecture) != "qwen3" || llama_model_n_embd(model.get()) != 1024 ||
        llama_model_n_layer(model.get()) != 28) {
        throw std::runtime_error("Only the configured Qwen3-0.6B model is supported.");
    }
    const char * chat_template = llama_model_chat_template(model.get(), nullptr);
    if (!chat_template) throw std::runtime_error("The model has no chat template.");
    const llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
    const int length = llama_chat_apply_template(chat_template, messages, 2, true, nullptr, 0);
    if (length <= 0 || length > MAX_PROMPT_BYTES) throw std::runtime_error("Unsupported model chat template.");
    std::vector<char> formatted(length + 1);
    const int written = llama_chat_apply_template(chat_template, messages, 2, true, formatted.data(), formatted.size());
    if (written != length) throw std::runtime_error("Unable to format the model prompt.");
    // Qwen3 non-thinking mode: prefill the completed, empty reasoning block.
    const std::string prompt = std::string(formatted.data(), length) + "<think>\n\n</think>\n\n";
    const llama_vocab * vocab = llama_model_get_vocab(model.get());
    const int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (count <= 0 || count + max_tokens > CONTEXT_TOKENS) {
        throw std::runtime_error("The analysis exceeds the local model context. Use a shorter summary.");
    }
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) != count) {
        throw std::runtime_error("Unable to tokenize the analysis.");
    }

    auto context_params = llama_context_default_params();
    context_params.n_ctx = CONTEXT_TOKENS;
    context_params.n_batch = BATCH_TOKENS;
    context_params.n_ubatch = BATCH_TOKENS;
    context_params.n_threads = std::min(4u, std::max(1u, std::thread::hardware_concurrency()));
    context_params.n_threads_batch = context_params.n_threads;
    context_params.offload_kqv = false;
    context_params.op_offload = false;
    context_params.abort_callback = abort_decode;
    context_params.abort_callback_data = &request;
    std::unique_ptr<llama_context, decltype(&llama_free)> context(
        llama_init_from_model(model.get(), context_params), llama_free);
    if (!context) throw std::runtime_error("Unable to allocate local model context.");
    for (int offset = 0; offset < count; offset += BATCH_TOKENS) {
        check_cancelled(request);
        auto batch = llama_batch_get_one(tokens.data() + offset, std::min(BATCH_TOKENS, count - offset));
        if (llama_decode(context.get(), batch) != 0) {
            check_cancelled(request);
            throw std::runtime_error("Unable to evaluate the analysis prompt.");
        }
    }

    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    if (!sampler) throw std::runtime_error("Unable to initialize token sampling.");
    // Qwen's non-thinking defaults, with a stable seed for reproducible summaries.
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(42));
    std::string output;
    for (int index = 0; index < max_tokens; ++index) {
        check_cancelled(request);
        llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        std::vector<char> piece(256);
        int size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (size < 0) {
            if (size < -MAX_PROMPT_BYTES) throw std::runtime_error("Invalid model token.");
            piece.resize(-size);
            size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (size < 0) throw std::runtime_error("Unable to read generated text.");
        output.append(piece.data(), size);
        if (index + 1 < max_tokens) {
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context.get(), batch) != 0) {
                check_cancelled(request);
                throw std::runtime_error("Unable to continue local generation.");
            }
        }
    }
    check_cancelled(request);
    return output;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_nagopy_android_foldlytics_insight_LlamaInsightEngine_nativeCreateRequest(JNIEnv * env, jobject) {
    try {
        return static_cast<jlong>(reinterpret_cast<intptr_t>(new Request()));
    } catch (...) {
        throw_java(env, "Unable to allocate a local inference request.");
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_nagopy_android_foldlytics_insight_LlamaInsightEngine_nativeCancel(JNIEnv *, jobject, jlong handle) {
    if (auto * request = from_handle(handle)) request->cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nagopy_android_foldlytics_insight_LlamaInsightEngine_nativeDestroyRequest(JNIEnv *, jobject, jlong handle) {
    delete from_handle(handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nagopy_android_foldlytics_insight_LlamaInsightEngine_nativeGenerate(
    JNIEnv * env, jobject, jlong handle, jbyteArray model_path, jbyteArray system_prompt,
    jbyteArray user_prompt, jint max_tokens) {
    try {
        auto * request = from_handle(handle);
        if (!request) throw std::runtime_error("Missing inference request.");
        const auto output = generate(*request, read_bytes(env, model_path), read_bytes(env, system_prompt),
                                     read_bytes(env, user_prompt), max_tokens);
        jbyteArray result = env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception & error) {
        throw_java(env, error.what());
    } catch (...) {
        throw_java(env, "Local generation failed.");
    }
    return nullptr;
}
