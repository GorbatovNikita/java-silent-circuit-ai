
package ru.hck.voice.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import ru.hck.voice.database.entity.Transcription;
import ru.hck.voice.repository.TranscriptionRepository;

public class TranscriptionViewModel extends AndroidViewModel {

    private final TranscriptionRepository repository;

    private final LiveData<List<Transcription>> allTranscriptions;

    public TranscriptionViewModel(
            @NonNull Application application
    ) {
        super(application);

        repository = new TranscriptionRepository(application);

        allTranscriptions = repository.getAllTranscriptions();
    }

    public LiveData<List<Transcription>> getAllTranscriptions() {
        return allTranscriptions;
    }

    public LiveData<Transcription> getTranscriptionById(long id) {
        return repository.getTranscriptionById(id);
    }

    public void insert(
            Transcription transcription,
            TranscriptionRepository.InsertCallback callback
    ) {

        repository.insert(transcription, transcriptionId -> {

            new Handler(Looper.getMainLooper()).post(() -> {

                if (callback != null) {
                    callback.onInsertComplete(transcriptionId);
                }
            });
        });
    }

    public void update(Transcription transcription) {
        repository.update(transcription);
    }

    public void delete(Transcription transcription) {
        repository.delete(transcription);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.shutdown();
    }
}
