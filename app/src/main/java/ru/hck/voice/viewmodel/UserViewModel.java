package ru.hck.voice.viewmodel;


import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import ru.hck.voice.database.entity.User;
import ru.hck.voice.repository.UserRepository;

public class UserViewModel extends AndroidViewModel {

    private final UserRepository repository;

    private final LiveData<User> currentUser;

    public UserViewModel(@NonNull Application application) {

        super(application);

        repository = new UserRepository(application);

        currentUser = repository.getCurrentUser();
    }

    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public void insert(
            User user,
            UserRepository.InsertCallback callback
    ) {
        repository.insert(user, userId -> {

            // Return callback on main thread
            new Handler(Looper.getMainLooper()).post(() -> {

                if (callback != null) {
                    callback.onInsertComplete(userId);
                }

            });
        });
    }

    public void update(User user) {
        repository.update(user);
    }

    public void delete(User user) {
        repository.delete(user);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.shutdown();
    }
}
