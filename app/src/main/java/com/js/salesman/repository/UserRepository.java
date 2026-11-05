package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.js.salesman.interfaces.UserDao;
import com.js.salesman.models.UserEntity;
import com.js.salesman.utils.database.AppDatabase;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private static volatile UserRepository INSTANCE;
    private final AppDatabase db;
    private final UserDao userDao;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private UserRepository(Context context) {
        this.db = AppDatabase.getInstance(context);
        this.userDao = db.userDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static UserRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (UserRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new UserRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    public void storeUser(String uid, String userName, boolean hasPin, String role,
                            String fullName, String token, @Nullable Callback<Boolean> callback) {
        read("storeUser",
                () -> storeUserInternal(uid, userName, hasPin, role, fullName, token),
                false, callback);
    }

    @WorkerThread
    public boolean storeUserSync(String uid, String userName, boolean hasPin, String role,
                                    String fullName, String token) {
        return storeUserInternal(uid, userName, hasPin, role, fullName, token);
    }

    public void saveUserPin(String userId, String pinHash, @Nullable Callback<Boolean> callback) {
        read("saveUserPin", () -> saveUserPinInternal(userId, pinHash), false, callback);
    }

    @WorkerThread
    public boolean saveUserPinSync(String userId, String pinHash) {
        return saveUserPinInternal(userId, pinHash);
    }

    public void getUserPinHash(String userId, @Nullable Callback<String> callback) {
        read("getUserPinHash", () -> loadPinHash(userId), null, callback);
    }

    @WorkerThread
    @Nullable
    public String getUserPinHashSync(String userId) {
        return loadPinHash(userId);
    }

    public void userHasPin(String userId, @Nullable Callback<Boolean> callback) {
        read("userHasPin", () -> loadHasPin(userId), false, callback);
    }

    @WorkerThread
    public boolean userHasPinSync(String userId) {
        return loadHasPin(userId);
    }

    /** {@code hasPin == 1} marks the user as having a PIN; any other value clears it. */
    public void updatePinLocal(String userId, int hasPin, @Nullable Callback<Boolean> callback) {
        read("updatePinLocal", () -> updatePinLocalInternal(userId, hasPin), false, callback);
    }

    @WorkerThread
    public boolean updatePinLocalSync(String userId, int hasPin) {
        return updatePinLocalInternal(userId, hasPin);
    }

    @WorkerThread
    @Nullable
    public String getTokenSync() {
        return userDao.getToken();
    }

    @WorkerThread
    public HashMap<String, String> getUserDetailsSync(String userId) {
        HashMap<String, String> userMap = new HashMap<>();
        if (isBlank(userId)) return userMap;
        UserEntity user = userDao.getUserByIdSync(userId);
        if (user != null) {
            userMap.put("id", orEmpty(user.getId()));
            userMap.put("userName", orEmpty(user.getUserName()));
            userMap.put("has_pin", user.isHasPin() ? "1" : "0");
            userMap.put("role", orEmpty(user.getRole()));
            userMap.put("fullName", orEmpty(user.getFullName()));
            userMap.put("token", orEmpty(user.getToken()));
        }
        return userMap;
    }

    private boolean storeUserInternal(String uid, String userName, boolean hasPin, String role,
                                        String fullName, String token) {
        if (isBlank(uid)) {
            Log.w(TAG, "storeUser: rejected blank user id");
            return false;
        }
        try {
            db.runInTransaction(() -> {
                UserEntity existing = userDao.getUserByIdSync(uid);
                String pinHash = existing != null ? existing.getPinHash() : null;
                boolean finalHasPin = hasPin || (existing != null && existing.isHasPin());
                userDao.insertUser(new UserEntity(
                        uid, userName, finalHasPin, pinHash, role, fullName, token));
            });
            return true;
        } catch (Exception e) {
            Log.e(TAG, "storeUser: failed for id=" + uid, e);
            return false;
        }
    }

    private boolean saveUserPinInternal(String userId, String pinHash) {
        if (isBlank(userId)) return false;
        try {
            return userDao.saveUserPin(userId, pinHash) > 0;
        } catch (Exception e) {
            Log.e(TAG, "saveUserPin: failed for id=" + userId, e);
            return false;
        }
    }

    private boolean updatePinLocalInternal(String userId, int hasPin) {
        if (isBlank(userId)) return false;
        try {
            return userDao.updateHasPin(userId, hasPin == 1) > 0;
        } catch (Exception e) {
            Log.e(TAG, "updatePinLocal: failed for id=" + userId, e);
            return false;
        }
    }

    @Nullable
    private String loadPinHash(String userId) {
        return isBlank(userId) ? null : userDao.getPinHash(userId);
    }

    private boolean loadHasPin(String userId) {
        if (isBlank(userId)) return false;
        UserEntity user = userDao.getUserByIdSync(userId);
        return user != null
                && user.isHasPin()
                && user.getPinHash() != null
                && !user.getPinHash().isEmpty();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String orEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    private <T> void read(@NonNull String operation, @NonNull Callable<T> query,
                            @Nullable T fallback, @Nullable Callback<T> callback) {
        executor.execute(() -> {
            T result;
            try {
                result = query.call();
            } catch (Exception e) {
                Log.e(TAG, operation + ": failed", e);
                result = fallback;
            }
            if (callback != null) {
                final T delivered = result;
                mainHandler.post(() -> callback.onResult(delivered));
            }
        });
    }
}