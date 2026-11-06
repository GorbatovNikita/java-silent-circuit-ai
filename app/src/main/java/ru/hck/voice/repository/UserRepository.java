package ru.hck.voice.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.hck.voice.database.AppDatabase;
import ru.hck.voice.database.dao.UserDao;
import ru.hck.voice.database.entity.User;

public class UserRepository {

    public interface InsertCallback {
        void onInsertComplete(long userId);
    }

    private final UserDao userDao;

    private final LiveData<User> currentUser;

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    public UserRepository(Application application) {

        AppDatabase database =
                AppDatabase.getInstance(application);

        userDao = database.userDao();

        currentUser = userDao.getCurrentUser();
    }

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public void insert(User user, InsertCallback callback) {

        executorService.execute(() -> {

            long userId = userDao.insert(user);

            if (callback != null) {
                callback.onInsertComplete(userId);
            }
        });
    }

    public void update(User user) {

        executorService.execute(() -> {
            userDao.update(user);
        });
    }

    public void delete(User user) {

        executorService.execute(() -> {
            userDao.delete(user);
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
