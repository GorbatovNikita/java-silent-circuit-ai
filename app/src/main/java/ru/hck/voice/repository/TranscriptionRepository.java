
package ru.hck.voice.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.hck.voice.database.AppDatabase;
import ru.hck.voice.database.dao.TranscriptionDao;
import ru.hck.voice.database.entity.Transcription;

public class TranscriptionRepository {

    public interface InsertCallback {
        void onInsertComplete(long transcriptionId);
    }

    private final TranscriptionDao transcriptionDao;

    private final LiveData<List<Transcription>> allTranscriptions;

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    public TranscriptionRepository(Application application) {

        AppDatabase database =
                AppDatabase.getInstance(application);

        transcriptionDao = database.transcriptionDao();

        allTranscriptions =
                transcriptionDao.getAllTranscriptions();
    }

    public LiveData<List<Transcription>> getAllTranscriptions() {
        return allTranscriptions;
    }

    public LiveData<Transcription> getTranscriptionById(long id) {
        return transcriptionDao.getTranscriptionById(id);
    }

    public void insert(
            Transcription transcription,
            InsertCallback callback
    ) {

        executorService.execute(() -> {

            long transcriptionId =
                    transcriptionDao.insert(transcription);

            if (callback != null) {
                callback.onInsertComplete(transcriptionId);
            }
        });
    }

    public void update(Transcription transcription) {

        executorService.execute(() -> {
            transcriptionDao.update(transcription);
        });
    }

    public void delete(Transcription transcription) {

        executorService.execute(() -> {
            transcriptionDao.delete(transcription);
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
