package ru.hck.voice.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ru.hck.voice.database.entity.Transcription;

@Dao
public interface TranscriptionDao {
    @Delete
    void delete(Transcription transcription);

    @Update
    void update(Transcription transcription);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Transcription transcription);

    @Query("select * from transcriptions where id = :id limit 1")
    LiveData<Transcription> getTranscriptionById(long id);

    @Query("select * from transcriptions")
    LiveData<List<Transcription>> getAllTranscriptions();

    // Методы для Python Bridge

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    Transcription getTranscriptionByIdSync(long id);

    @Query("SELECT * FROM transcriptions")
    List<Transcription> getAllTranscriptionsSync();

    @Query("DELETE FROM transcriptions WHERE id = :id")
    void deleteById(long id);
}
