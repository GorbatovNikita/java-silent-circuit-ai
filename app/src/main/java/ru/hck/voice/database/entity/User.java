package ru.hck.voice.database.entity;

import androidx.annotation.ColorLong;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "username")
    private String username;

    @NonNull
    @ColumnInfo(name = "auth_type")
    private String authType;

    @NonNull
    @ColumnInfo(name = "auth_hash")
    private String authHash;

    public User(String username, String authType, String authHash)
    {
        this.username = username;
        this.authType = authType;
        this.authHash = authHash;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }

    @NonNull
    public String getAuthType() {
        return authType;
    }

    public void setAuthType(@NonNull String authType) {
        this.authType = authType;
    }

    @NonNull
    public String getAuthHash() {
        return authHash;
    }

    public void setAuthHash(@NonNull String authHash) {
        this.authHash = authHash;
    }
}
