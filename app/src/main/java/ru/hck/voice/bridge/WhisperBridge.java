package ru.hck.voice.bridge;

public class WhisperBridge {

    static {
        System.loadLibrary("whisper_jni");
    }

    // Настройки распознавания по умолчанию
    private String language = "ru";

    private int threads = 2;
    private int beamSize = 5;
    private int bestOf = 3;

    private float temperature = 0.0f;
    private float temperatureIncrement = 0.2f;

    private boolean translate = false;
    private boolean timestamps = true;
    private boolean noContext = true;

    private boolean initialized = false;

    public WhisperBridge() {
    }

    // =========================
    // ИНИЦИАЛИЗАЦИЯ
    // =========================

    public synchronized void initialize(String modelPath) {
        if (initialized) {
            return;
        }

        String error = nativeInitialize(modelPath);

        if (error != null && !error.isEmpty()) {
            throw new IllegalStateException(error);
        }

        initialized = true;
    }

    // =========================
    // НАСТРОЙКИ
    // =========================

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setThreads(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1");
        }
        this.threads = threads;
    }

    public void setBeamSize(int beamSize) {
        if (beamSize < 1) {
            throw new IllegalArgumentException("beamSize must be >= 1");
        }
        this.beamSize = beamSize;
    }

    public void setBestOf(int bestOf) {
        if (bestOf < 1) {
            throw new IllegalArgumentException("bestOf must be >= 1");
        }
        this.bestOf = bestOf;
    }

    public void setTemperature(float temperature) {
        if (temperature < 0.0f || temperature > 1.0f) {
            throw new IllegalArgumentException(
                    "temperature must be between 0 and 1"
            );
        }
        this.temperature = temperature;
    }

    public void setTemperatureIncrement(float increment) {
        if (increment < 0.0f || increment > 1.0f) {
            throw new IllegalArgumentException(
                    "temperature increment must be between 0 and 1"
            );
        }
        this.temperatureIncrement = increment;
    }

    public void setTranslate(boolean translate) {
        this.translate = translate;
    }

    public void setTimestamps(boolean timestamps) {
        this.timestamps = timestamps;
    }

    public void setNoContext(boolean noContext) {
        this.noContext = noContext;
    }

    // =========================
    // РАСПОЗНАВАНИЕ
    // =========================

    public synchronized String transcribe(String audioPath) {
        if (!initialized) {
            throw new IllegalStateException(
                    "Whisper model is not initialized"
            );
        }

        return nativeTranscribe(
                audioPath,
                language,
                threads,
                beamSize,
                bestOf,
                temperature,
                temperatureIncrement,
                translate,
                timestamps,
                noContext
        );
    }

    // =========================
    // ОСВОБОЖДЕНИЕ РЕСУРСОВ
    // =========================

    public synchronized void release() {
        if (initialized) {
            nativeRelease();
            initialized = false;
        }
    }

    // =========================
    // НАТИВНЫЕ МЕТОДЫ JNI
    // =========================

    private native String nativeInitialize(String modelPath);

    private native String nativeTranscribe(
            String audioPath,
            String language,
            int threads,
            int beamSize,
            int bestOf,
            float temperature,
            float temperatureIncrement,
            boolean translate,
            boolean timestamps,
            boolean noContext
    );

    private native void nativeRelease();
}