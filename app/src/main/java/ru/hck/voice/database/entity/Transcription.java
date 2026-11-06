package ru.hck.voice.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "transcriptions")
public class Transcription {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "audio_path")
    public String audioPath;

    @NonNull
    @ColumnInfo(name = "content")
    public String content;

    public Transcription(@NonNull String audioPath, @NonNull String content)
    {
        this.audioPath = audioPath;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(@NonNull String audioPath) {
        this.audioPath = audioPath;
    }

    @NonNull
    public String getContent() {
        return content;
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }
}
