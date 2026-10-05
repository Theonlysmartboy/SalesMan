package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.js.salesman.models.AppConfig;

@Dao
public interface ConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertConfig(AppConfig config);

    @Query("SELECT value FROM config WHERE `key` = :key LIMIT 1")
    String getConfigValue(String key);

    @Query("DELETE FROM config WHERE `key` = :key")
    void deleteConfigByKey(String key);
}
