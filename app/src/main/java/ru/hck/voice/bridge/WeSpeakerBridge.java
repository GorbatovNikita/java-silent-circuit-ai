package ru.hck.voice.bridge;

import ai.onnxruntime.*;
import java.util.Collections;
import java.util.Map;

public class WeSpeakerBridge {
    private static OrtEnvironment env;
    private static OrtSession session;

    // Инициализация при загрузке класса (ленивая загрузка в статическом блоке)
    static {
        try {
            env = OrtEnvironment.getEnvironment();
            // Предполагается, что модель находится в assets или по абсолютному пути;
            // здесь для примера указан путь в файловой системе, скорректируйте при необходимости
            session = env.createSession("wespeaker_resnet34_LM.onnx",
                    new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new RuntimeException("Не удалось загрузить модель WeSpeaker ONNX", e);
        }
    }

    /**
     * Вызывается из Python.
     *
     * @param audio       Все аудиоданные окон, конкатенированные в один float[], 16 кГц моно
     * @param lengths     Количество сэмплов в каждом окне
     * @param sampleRate  Частота дискретизации (должна быть 16000)
     * @return float[][], форма [N, 256], где N — количество окон
     */
    public static float[][] computeEmbeddings(float[] audio, int[] lengths, int sampleRate) {
        if (sampleRate != 16000) {
            throw new IllegalArgumentException("WeSpeaker требует частоту дискретизации 16 кГц");
        }

        int numWindows = lengths.length;
        float[][] results = new float[numWindows][256];

        int offset = 0;
        for (int i = 0; i < numWindows; i++) {
            int len = lengths[i];

            // 1. Извлечение текущего окна аудио
            float[] windowAudio = new float[len];
            System.arraycopy(audio, offset, windowAudio, 0, len);
            offset += len;

            // 2. Дополнение нулями до 160000 сэмплов (10 секунд × 16000)
            float[] paddedAudio = new float[160000];
            System.arraycopy(windowAudio, 0, paddedAudio, 0, Math.min(len, 160000));

            // 3. Построение тензора waveform: форма [3, 160000], три слота заполняются одинаковыми данными
            long[] waveformShape = new long[]{3, 160000};
            float[][] waveformData = new float[3][160000];
            for (int s = 0; s < 3; s++) {
                System.arraycopy(paddedAudio, 0, waveformData[s], 0, 160000);
            }

            // 4. Построение тензора mask: форма [3, 589], все единицы (упрощённая обработка)
            // Примечание: mask в идеале должен отмечать активные кадры речи; здесь для примера заполняется единицами
            long[] maskShape = new long[]{3, 589};
            float[][] maskData = new float[3][589];
            for (int s = 0; s < 3; s++) {
                java.util.Arrays.fill(maskData[s], 1.0f);
            }

            try (OnnxTensor waveformTensor = OnnxTensor.createTensor(env, waveformData);
                 OnnxTensor maskTensor = OnnxTensor.createTensor(env, maskData)) {

                Map<String, OnnxTensor> inputs = Map.of(
                        "waveform", waveformTensor,
                        "mask", maskTensor
                );

                try (OrtSession.Result result = session.run(inputs)) {
                    // Выход embedding: [3, 256], берётся результат первого слота
                    float[][][] emb = (float[][][]) ((OnnxTensor) result.get(0)).getValue();
                    System.arraycopy(emb[0][0], 0, results[i], 0, 256);
                }
            } catch (OrtException e) {
                throw new RuntimeException("Ошибка инференса ONNX", e);
            }
        }

        return results;
    }
}