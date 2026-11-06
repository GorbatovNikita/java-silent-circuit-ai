
#include <jni.h>
#include "whisper.h"

#include <algorithm>
#include <cstdint>
#include <fstream>
#include <iomanip>
#include <iterator>
#include <limits>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

static whisper_context* g_context = nullptr;
static std::mutex g_mutex;

// Передача ошибки в Java
static void throwJavaException(JNIEnv* env, const char* message) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
    }
}

// Чтение 16- и 32-битных значений WAV в little-endian
static uint16_t readU16(const std::vector<uint8_t>& data, size_t pos) {
    return static_cast<uint16_t>(data[pos]) |
           (static_cast<uint16_t>(data[pos + 1]) << 8);
}

static uint32_t readU32(const std::vector<uint8_t>& data, size_t pos) {
    return static_cast<uint32_t>(data[pos]) |
           (static_cast<uint32_t>(data[pos + 1]) << 8) |
           (static_cast<uint32_t>(data[pos + 2]) << 16) |
           (static_cast<uint32_t>(data[pos + 3]) << 24);
}

// Загрузка WAV PCM16 mono 16 kHz и преобразование в float
static bool loadWav(
        const std::string& path,
        std::vector<float>& samples,
        std::string& error
) {
    std::ifstream file(path, std::ios::binary);

    if (!file) {
        error = "Cannot open audio file: " + path;
        return false;
    }

    std::vector<uint8_t> data(
            (std::istreambuf_iterator<char>(file)),
            std::istreambuf_iterator<char>()
    );

    if (data.size() < 12 ||
        std::string(data.begin(), data.begin() + 4) != "RIFF" ||
        std::string(data.begin() + 8, data.begin() + 12) != "WAVE") {
        error = "Invalid WAV file";
        return false;
    }

    bool foundFormat = false;
    bool foundAudio = false;

    uint16_t audioFormat = 0;
    uint16_t channels = 0;
    uint16_t bitsPerSample = 0;
    uint32_t sampleRate = 0;

    size_t audioOffset = 0;
    size_t audioSize = 0;

    size_t pos = 12;

    while (pos + 8 <= data.size()) {
        std::string chunkId(data.begin() + pos, data.begin() + pos + 4);
        uint32_t chunkSize = readU32(data, pos + 4);

        size_t chunkData = pos + 8;

        if (chunkData > data.size() ||
            chunkSize > data.size() - chunkData) {
            error = "Invalid WAV chunk size";
            return false;
        }

        if (chunkId == "fmt ") {
            if (chunkSize < 16) {
                error = "Invalid WAV format chunk";
                return false;
            }

            audioFormat = readU16(data, chunkData);
            channels = readU16(data, chunkData + 2);
            sampleRate = readU32(data, chunkData + 4);
            bitsPerSample = readU16(data, chunkData + 14);

            foundFormat = true;
        }

        if (chunkId == "data") {
            audioOffset = chunkData;
            audioSize = chunkSize;
            foundAudio = true;
        }

        size_t next = chunkData + chunkSize;

        // WAV chunks выравниваются по двум байтам
        if (chunkSize % 2 != 0) {
            ++next;
        }

        if (next > data.size()) {
            break;
        }

        pos = next;
    }

    if (!foundFormat || !foundAudio) {
        error = "WAV format or audio data not found";
        return false;
    }

    if (audioFormat != 1) {
        error = "Only PCM WAV is supported";
        return false;
    }

    if (channels != 1) {
        error = "Audio must be mono";
        return false;
    }

    if (sampleRate != 16000) {
        error = "Audio sample rate must be 16000 Hz";
        return false;
    }

    if (bitsPerSample != 16 || audioSize % 2 != 0) {
        error = "Audio must be PCM 16-bit";
        return false;
    }

    size_t sampleCount = audioSize / 2;

    if (sampleCount > static_cast<size_t>(
            std::numeric_limits<int>::max())) {
        error = "Audio file is too large";
        return false;
    }

    samples.resize(sampleCount);

    for (size_t i = 0; i < sampleCount; ++i) {
        uint16_t raw = readU16(data, audioOffset + i * 2);
        int16_t value = static_cast<int16_t>(raw);

        samples[i] = static_cast<float>(value) / 32768.0f;
    }

    return true;
}

// Форматирование времени сегмента.
// Whisper возвращает время в сотых долях секунды.
static std::string formatTimestamp(int64_t centiseconds) {
    int64_t milliseconds = centiseconds * 10;

    int64_t hours = milliseconds / 3600000;
    int64_t minutes = (milliseconds / 60000) % 60;
    int64_t seconds = (milliseconds / 1000) % 60;
    int64_t millis = milliseconds % 1000;

    std::ostringstream out;

    out << std::setfill('0')
        << std::setw(2) << hours << ":"
        << std::setw(2) << minutes << ":"
        << std::setw(2) << seconds << "."
        << std::setw(3) << millis;

    return out.str();
}

// Загрузка модели
extern "C"
JNIEXPORT jstring JNICALL
Java_ru_hck_voice_bridge_WhisperBridge_nativeInitialize(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath
) {
    if (modelPath == nullptr) {
        return env->NewStringUTF("Model path is null");
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    if (path == nullptr) {
        return env->NewStringUTF("Cannot read model path");
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context != nullptr) {
        env->ReleaseStringUTFChars(modelPath, path);
        return env->NewStringUTF("");
    }

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = false;

    g_context = whisper_init_from_file_with_params(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (g_context == nullptr) {
        return env->NewStringUTF("Failed to load Whisper model");
    }

    return env->NewStringUTF("");
}

// Распознавание аудио
extern "C"
JNIEXPORT jstring JNICALL
Java_ru_hck_voice_bridge_WhisperBridge_nativeTranscribe(
        JNIEnv* env,
        jobject /* thiz */,
        jstring audioPath,
        jstring language,
        jint threads,
        jint beamSize,
        jint bestOf,
        jfloat temperature,
        jfloat temperatureIncrement,
        jboolean translate,
        jboolean timestamps,
        jboolean noContext
) {
    if (audioPath == nullptr) {
        throwJavaException(env, "Audio path is null");
        return nullptr;
    }

    const char* path = env->GetStringUTFChars(audioPath, nullptr);

    if (path == nullptr) {
        throwJavaException(env, "Cannot read audio path");
        return nullptr;
    }

    const char* lang = nullptr;

    if (language != nullptr) {
        lang = env->GetStringUTFChars(language, nullptr);
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context == nullptr) {
        env->ReleaseStringUTFChars(audioPath, path);

        if (lang != nullptr) {
            env->ReleaseStringUTFChars(language, lang);
        }

        throwJavaException(env, "Whisper model is not initialized");
        return nullptr;
    }

    std::vector<float> samples;
    std::string error;

    if (!loadWav(path, samples, error)) {
        env->ReleaseStringUTFChars(audioPath, path);

        if (lang != nullptr) {
            env->ReleaseStringUTFChars(language, lang);
        }

        throwJavaException(env, error.c_str());
        return nullptr;
    }

    env->ReleaseStringUTFChars(audioPath, path);

    // bestOf применяется к Greedy Search.
    // Здесь используется Beam Search, поэтому bestOf не влияет на результат.
    (void) bestOf;

    whisper_full_params params =
            whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);

    params.n_threads = std::max(1, static_cast<int>(threads));
    params.beam_search.beam_size = std::max(1, static_cast<int>(beamSize));

    params.translate = (translate == JNI_TRUE);
    params.no_context = (noContext == JNI_TRUE);
    params.no_timestamps = (timestamps != JNI_TRUE);

    params.temperature = temperature;
    params.temperature_inc = temperatureIncrement;

    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    if (lang != nullptr && lang[0] != '\0') {
        params.language = lang;
        params.detect_language = false;
    } else {
        params.language = "ru";
        params.detect_language = false;
    }

    int result = whisper_full(
            g_context,
            params,
            samples.data(),
            static_cast<int>(samples.size())
    );

    if (lang != nullptr) {
        env->ReleaseStringUTFChars(language, lang);
    }

    if (result != 0) {
        throwJavaException(env, "Whisper transcription failed");
        return nullptr;
    }

    // Получаем результат
    std::ostringstream output;

    int segmentCount = whisper_full_n_segments(g_context);

    for (int i = 0; i < segmentCount; ++i) {
        const char* text = whisper_full_get_segment_text(g_context, i);

        if (text == nullptr) {
            continue;
        }

        if (timestamps == JNI_TRUE) {
            int64_t start = whisper_full_get_segment_t0(g_context, i);
            int64_t end = whisper_full_get_segment_t1(g_context, i);

            output << "["
                   << formatTimestamp(start)
                   << " --> "
                   << formatTimestamp(end)
                   << "] ";
        }

        output << text << "\n";
    }

    return env->NewStringUTF(output.str().c_str());
}

// Освобождение модели
extern "C"
JNIEXPORT void JNICALL
Java_ru_hck_voice_bridge_WhisperBridge_nativeRelease(
        JNIEnv* /* env */,
        jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
    }
}

