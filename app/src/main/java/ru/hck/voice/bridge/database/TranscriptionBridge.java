package ru.hck.voice.bridge.database;

import android.content.Context;

import java.util.List;

import ru.hck.voice.database.AppDatabase;
import ru.hck.voice.database.dao.TranscriptionDao;
import ru.hck.voice.database.entity.Transcription;

public class TranscriptionBridge {

    private static TranscriptionDao dao;

    public static void initialize(Context context) {
        AppDatabase database = AppDatabase.getInstance(
            context.getApplicationContext()
        );

        dao = database.transcriptionDao();
    }

    private static void checkInitialized() {
        if (dao == null) {
            throw new IllegalStateException(
                "TranscriptionBridge is not initialized"
            );
        }
    }

    // Добавление транскрипции
    public static long insert(Transcription transcription) {
        checkInitialized();
        return dao.insert(transcription);
    }

    // Обновление транскрипции
    public static void update(Transcription transcription) {
        checkInitialized();
        dao.update(transcription);
    }

    // Удаление транскрипции
    public static void delete(Transcription transcription) {
        checkInitialized();
        dao.delete(transcription);
    }

    // Удаление по ID
    public static void deleteById(long id) {
        checkInitialized();
        dao.deleteById(id);
    }

    // Получение одной транскрипции
    public static Transcription getById(long id) {
        checkInitialized();
        return dao.getTranscriptionByIdSync(id);
    }

    // Получение всех транскрипций
    public static List<Transcription> getAll() {
        checkInitialized();
        return dao.getAllTranscriptionsSync();
    }
}
