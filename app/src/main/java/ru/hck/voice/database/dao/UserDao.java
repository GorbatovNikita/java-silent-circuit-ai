package ru.hck.voice.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import ru.hck.voice.database.entity.User;

@Dao
public interface UserDao {
    @Delete
    void delete(User user);

    @Update
    void update(User user);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(User user);

    @Query("select * from users where id = 1")
    LiveData<User> getCurrentUser();
}
