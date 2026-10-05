package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.js.salesman.models.UserEntity;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    UserEntity getUserByIdSync(String userId);

    @Query("SELECT pinHash FROM users WHERE id = :userId LIMIT 1")
    String getPinHash(String userId);

    @Query("SELECT token FROM users LIMIT 1")
    String getToken();

    @Query("UPDATE users SET pinHash = :pinHash, hasPin = 1 WHERE id = :userId")
    int saveUserPin(String userId, String pinHash);

    @Query("UPDATE users SET hasPin = :hasPin WHERE id = :userId")
    int updateHasPin(String userId, boolean hasPin);
}
