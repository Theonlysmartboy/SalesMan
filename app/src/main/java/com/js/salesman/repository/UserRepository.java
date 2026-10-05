package com.js.salesman.repository;

import android.content.Context;

import com.js.salesman.interfaces.UserDao;
import com.js.salesman.models.UserEntity;
import com.js.salesman.utils.database.AppDatabase;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {
    private static volatile UserRepository INSTANCE;
    private final UserDao userDao;
    private final ExecutorService executor;

    private UserRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
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
                          String fullName, String token, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                UserEntity existing = userDao.getUserByIdSync(uid);
                String pinHash = existing != null ? existing.getPinHash() : null;
                boolean finalHasPin = hasPin || (existing != null && existing.isHasPin());
                UserEntity user = new UserEntity(uid, userName, finalHasPin, pinHash, role, fullName, token);
                userDao.insertUser(user);
                if (callback != null) callback.onResult(true);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    public boolean storeUserSync(String uid, String userName, boolean hasPin, String role,
                                 String fullName, String token) {
        try {
            UserEntity existing = userDao.getUserByIdSync(uid);
            String pinHash = existing != null ? existing.getPinHash() : null;
            boolean finalHasPin = hasPin || (existing != null && existing.isHasPin());
            UserEntity user = new UserEntity(uid, userName, finalHasPin, pinHash, role, fullName, token);
            userDao.insertUser(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void saveUserPin(String userId, String pinHash, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                int updated = userDao.saveUserPin(userId, pinHash);
                if (callback != null) callback.onResult(updated > 0);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    public boolean saveUserPinSync(String userId, String pinHash) {
        try {
            int updated = userDao.saveUserPin(userId, pinHash);
            return updated > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void getUserPinHash(String userId, Callback<String> callback) {
        executor.execute(() -> {
            String hash = userDao.getPinHash(userId);
            if (callback != null) callback.onResult(hash);
        });
    }

    public String getUserPinHashSync(String userId) {
        return userDao.getPinHash(userId);
    }

    public void userHasPin(String userId, Callback<Boolean> callback) {
        executor.execute(() -> {
            UserEntity user = userDao.getUserByIdSync(userId);
            boolean hasPin = user != null && user.isHasPin() && user.getPinHash() != null && !user.getPinHash().isEmpty();
            if (callback != null) callback.onResult(hasPin);
        });
    }

    public boolean userHasPinSync(String userId) {
        UserEntity user = userDao.getUserByIdSync(userId);
        return user != null && user.isHasPin() && user.getPinHash() != null && !user.getPinHash().isEmpty();
    }

    public void updatePinLocal(String userId, int hasPin, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                int updated = userDao.updateHasPin(userId, hasPin == 1);
                if (callback != null) callback.onResult(updated > 0);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    public boolean updatePinLocalSync(String userId, int hasPin) {
        try {
            int updated = userDao.updateHasPin(userId, hasPin == 1);
            return updated > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTokenSync() {
        return userDao.getToken();
    }

    public HashMap<String, String> getUserDetailsSync(String userId) {
        HashMap<String, String> userMap = new HashMap<>();
        UserEntity user = userDao.getUserByIdSync(userId);
        if (user != null) {
            userMap.put("id", user.getId());
            userMap.put("userName", user.getUserName());
            userMap.put("has_pin", user.isHasPin() ? "1" : "0");
            userMap.put("role", user.getRole());
            userMap.put("fullName", user.getFullName());
            userMap.put("token", user.getToken());
        }
        return userMap;
    }
}
