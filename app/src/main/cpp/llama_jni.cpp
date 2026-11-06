
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include <stdexcept>

#include "llama.h"

// =====================================================
// ГЛОБАЛЬНЫЕ РЕСУРСЫ
// =====================================================

static llama_model* g_model = nullptr;
static llama_context* g_context = nullptr;

static bool g_backend_initialized = false;

static std::mutex g_mutex;

// =====================================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// =====================================================

static void throwJavaException(
        JNIEnv* env,
        const char* message
) {
    jclass exceptionClass =
            env->FindClass("java/lang/IllegalStateException");

    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
    }
}

static std::string jstringToString(
        JNIEnv* env,
        jstring value
) {
    if (value == nullptr) {
        return "";
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);

    if (chars == nullptr) {
        return "";
    }

    std::string result(chars);

    env->ReleaseStringUTFChars(value, chars);

    return result;
}

// =====================================================
// ИНИЦИАЛИЗАЦИЯ МОДЕЛИ
// =====================================================

extern "C"
JNIEXPORT void JNICALL
Java_ru_hck_voice_bridge_LlamaBridge_nativeInitialize(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath,
        jint contextSize,
        jint threads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model != nullptr && g_context != nullptr) {
        return;
    }

    std::string path = jstringToString(env, modelPath);

    if (path.empty()) {
        throwJavaException(env, "Model path is empty");
        return;
    }

    if (contextSize < 256 || threads < 1) {
        throwJavaException(env, "Invalid context size or thread count");
        return;
    }

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    // Параметры загрузки модели
    llama_model_params modelParams =
            llama_model_default_params();

    // На первом этапе используем CPU
    modelParams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(
            path.c_str(),
            modelParams
    );

    if (g_model == nullptr) {
        throwJavaException(env, "Failed to load GGUF model");
        return;
    }

    // Параметры контекста
    llama_context_params contextParams =
            llama_context_default_params();

    contextParams.n_ctx = static_cast<uint32_t>(contextSize);
    contextParams.n_batch = 512;
    contextParams.n_ubatch = 512;

    contextParams.n_threads = threads;
    contextParams.n_threads_batch = threads;

    g_context = llama_init_from_model(
            g_model,
            contextParams
    );

    if (g_context == nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;

        throwJavaException(env, "Failed to initialize llama context");
        return;
    }
}

// =====================================================
// ГЕНЕРАЦИЯ ОТВЕТА
// =====================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_ru_hck_voice_bridge_LlamaBridge_nativeGenerate(
        JNIEnv* env,
        jobject /* thiz */,
        jstring systemPrompt,
        jstring userPrompt,
        jint maxTokens,
        jfloat temperature
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model == nullptr || g_context == nullptr) {
        throwJavaException(env, "Qwen model is not initialized");
        return nullptr;
    }

    if (maxTokens < 1 || temperature < 0.0f || temperature > 2.0f) {
        throwJavaException(env, "Invalid generation parameters");
        return nullptr;
    }

    const std::string system = jstringToString(env, systemPrompt);
    const std::string user = jstringToString(env, userPrompt);

    if (user.empty()) {
        return env->NewStringUTF("");
    }

    // -------------------------------------------------
    // Формат диалога Qwen3
    // -------------------------------------------------

    std::string prompt =
            "<|im_start|>system\n" +
            system +
            "<|im_end|>\n"
            "<|im_start|>user\n" +
            user +
            "<|im_end|>\n"
            "<|im_start|>assistant\n";

    // -------------------------------------------------
    // Очищаем контекст предыдущего запроса
    // -------------------------------------------------

    llama_memory_clear(
            llama_get_memory(g_context),
            true
    );

    // -------------------------------------------------
    // Токенизация
    // -------------------------------------------------

    const llama_vocab* vocab =
            llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens(
            prompt.size() + 16
    );

    int32_t tokenCount = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
    );

    if (tokenCount < 0) {
        tokens.resize(static_cast<size_t>(-tokenCount));

        tokenCount = llama_tokenize(
                vocab,
                prompt.c_str(),
                static_cast<int32_t>(prompt.size()),
                tokens.data(),
                static_cast<int32_t>(tokens.size()),
                true,
                true
        );
    }

    if (tokenCount <= 0) {
        throwJavaException(env, "Tokenization failed");
        return nullptr;
    }

    tokens.resize(static_cast<size_t>(tokenCount));

    if (tokens.size() >= llama_n_ctx(g_context)) {
        throwJavaException(env, "Prompt exceeds context size");
        return nullptr;
    }

    // -------------------------------------------------
    // Обработка входного текста
    // -------------------------------------------------

    llama_batch batch = llama_batch_get_one(
            tokens.data(),
            static_cast<int32_t>(tokens.size())
    );

    if (llama_decode(g_context, batch) != 0) {
        throwJavaException(env, "Failed to process prompt");
        return nullptr;
    }

    // -------------------------------------------------
    // Настройка генерации
    // -------------------------------------------------

    llama_sampler* sampler = llama_sampler_chain_init(
            llama_sampler_chain_default_params()
    );

    if (sampler == nullptr) {
        throwJavaException(env, "Failed to create sampler");
        return nullptr;
    }

    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_top_k(40)
    );

    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_top_p(0.95f, 1)
    );

    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_temp(temperature)
    );

    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_dist(LLAMA_DEFAULT_SEED)
    );

    // -------------------------------------------------
    // Генерация
    // -------------------------------------------------

    std::string answer;

    std::vector<char> pieceBuffer(1024);

    for (int i = 0; i < maxTokens; ++i) {

        llama_token newToken = llama_sampler_sample(
                sampler,
                g_context,
                -1
        );

        llama_sampler_accept(
                sampler,
                newToken
        );

        // Проверяем конец генерации
        if (llama_vocab_is_eog(vocab, newToken)) {
            break;
        }

        // Преобразуем токен в текст
        int pieceSize = llama_token_to_piece(
                vocab,
                newToken,
                pieceBuffer.data(),
                static_cast<int32_t>(pieceBuffer.size()),
                0,
                true
        );

        if (pieceSize < 0) {
            pieceBuffer.resize(static_cast<size_t>(-pieceSize));

            pieceSize = llama_token_to_piece(
                    vocab,
                    newToken,
                    pieceBuffer.data(),
                    static_cast<int32_t>(pieceBuffer.size()),
                    0,
                    true
            );
        }

        if (pieceSize > 0) {
            answer.append(
                    pieceBuffer.data(),
                    static_cast<size_t>(pieceSize)
            );
        }

        // Передаём следующий токен модели
        llama_token nextToken = newToken;

        llama_batch nextBatch = llama_batch_get_one(
                &nextToken,
                1
        );

        if (llama_decode(g_context, nextBatch) != 0) {
            break;
        }
    }

    llama_sampler_free(sampler);

    return env->NewStringUTF(answer.c_str());
}

// =====================================================
// ОСВОБОЖДЕНИЕ РЕСУРСОВ
// =====================================================

extern "C"
JNIEXPORT void JNICALL
Java_ru_hck_voice_bridge_LlamaBridge_nativeRelease(
        JNIEnv* /* env */,
        jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }

    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

