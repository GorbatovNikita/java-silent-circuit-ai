
package ru.hck.voice.bridge;

public class LlamaBridge {

    static {
        System.loadLibrary("llama_jni");
    }

    // =====================================
    // НАСТРОЙКИ МОДЕЛИ
    // =====================================

    private int contextSize = 2048;
    private int threads = 4;

    private int batchSize = 256;
    private int microBatchSize = 128;

    // =====================================
    // НАСТРОЙКИ ГЕНЕРАЦИИ
    // =====================================

    private int maxTokens = 256;

    private float temperature = 0.7f;
    private int topK = 20;
    private float topP = 0.8f;

    private long seed = -1L;

    private String systemPrompt =
            "Ты полезный русскоязычный помощник. " +
            "Отвечай понятно, кратко и по существу.";

    private boolean initialized = false;

    // =====================================
    // ЗАГРУЗКА МОДЕЛИ
    // =====================================

    public synchronized void initialize(String modelPath) {

        if (initialized) {
            return;
        }

        if (modelPath == null || modelPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Model path is empty"
            );
        }

        nativeInitialize(
                modelPath,
                contextSize,
                threads,
                batchSize,
                microBatchSize
        );

        initialized = true;
    }

    // =====================================
    // ГЕНЕРАЦИЯ ОТВЕТА
    // =====================================

    public synchronized String generate(String prompt) {

        if (!initialized) {
            throw new IllegalStateException(
                    "Qwen model is not initialized"
            );
        }

        if (prompt == null || prompt.isEmpty()) {
            return "";
        }

        return nativeGenerate(
                systemPrompt,
                prompt,
                maxTokens,
                temperature,
                topK,
                topP,
                seed
        );
    }

    // =====================================
    // НАСТРОЙКИ МОДЕЛИ
    // =====================================

    public void setContextSize(int value) {
        if (initialized) {
            throw new IllegalStateException(
                    "Release model before changing context size"
            );
        }

        if (value < 256) {
            throw new IllegalArgumentException(
                    "Invalid context size"
            );
        }

        contextSize = value;
    }

    public void setThreads(int value) {
        if (initialized) {
            throw new IllegalStateException(
                    "Release model before changing threads"
            );
        }

        if (value < 1) {
            throw new IllegalArgumentException(
                    "Invalid thread count"
            );
        }

        threads = value;
    }

    public void setBatchSize(int value) {
        if (initialized) {
            throw new IllegalStateException(
                    "Release model before changing batch size"
            );
        }

        if (value < 1) {
            throw new IllegalArgumentException(
                    "Invalid batch size"
            );
        }

        batchSize = value;
    }

    public void setMicroBatchSize(int value) {
        if (initialized) {
            throw new IllegalStateException(
                    "Release model before changing micro-batch size"
            );
        }

        if (value < 1 || value > batchSize) {
            throw new IllegalArgumentException(
                    "Invalid micro-batch size"
            );
        }

        microBatchSize = value;
    }

    // =====================================
    // НАСТРОЙКИ ГЕНЕРАЦИИ
    // =====================================

    public void setMaxTokens(int value) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "Invalid max tokens"
            );
        }

        maxTokens = value;
    }

    public void setTemperature(float value) {
        if (value < 0.0f || value > 2.0f) {
            throw new IllegalArgumentException(
                    "Invalid temperature"
            );
        }

        temperature = value;
    }

    public void setTopK(int value) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "Invalid top-k"
            );
        }

        topK = value;
    }

    public void setTopP(float value) {
        if (value <= 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(
                    "Invalid top-p"
            );
        }

        topP = value;
    }

    public void setSeed(long value) {
        seed = value;
    }

    public void setSystemPrompt(String value) {
        if (value == null) {
            value = "";
        }

        systemPrompt = value;
    }

    // =====================================
    // СОСТОЯНИЕ
    // =====================================

    public boolean isInitialized() {
        return initialized;
    }

    // =====================================
    // ОСВОБОЖДЕНИЕ РЕСУРСОВ
    // =====================================

    public synchronized void release() {

        if (!initialized) {
            return;
        }

        try {
            nativeRelease();
        } finally {
            initialized = false;
        }
    }

    // =====================================
    // JNI
    // =====================================

    private native void nativeInitialize(
            String modelPath,
            int contextSize,
            int threads,
            int batchSize,
            int microBatchSize
    );

    private native String nativeGenerate(
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            int topK,
            float topP,
            long seed
    );

    private native void nativeRelease();
}
