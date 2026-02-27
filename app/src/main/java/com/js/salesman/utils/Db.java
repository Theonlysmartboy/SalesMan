package com.js.salesman.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;

public class Db extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "cypos.db";
    private static final String SQL_CREATE_CONFIG_TABLE = "CREATE TABLE tbl_config (" +
            "id INTEGER PRIMARY KEY NOT NULL," +
            "path varchar(100) NOT NULL);";

    private static final String SQL_CREATE_USERS_TABLE = "CREATE TABLE tbl_users (" +
            "id INTEGER PRIMARY KEY NOT NULL," +
            "userName varchar(100) NOT NULL," +
            "role varchar(65) NOT NULL," +
            "fullName varchar(100) NOT NULL," +
            "token varchar(255) NOT NULL);";
    // "DROP TABLE IF EXISTS tbl_config;"
    private static final String SQL_DELETE_CONFIG_TABLE = "DROP TABLE IF EXISTS tbl_config";
    private static final String SQL_DELETE_USERS_TABLE = "DROP TABLE IF EXISTS tbl_users";

    public Db(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CONFIG_TABLE);
        db.execSQL(SQL_CREATE_USERS_TABLE);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_CONFIG_TABLE);
        db.execSQL(SQL_DELETE_USERS_TABLE);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public boolean storeConfig(String path) {
        boolean added;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValue = new ContentValues();
        contentValue.put("path", path);
        long result = db.insert("tbl_config", null, contentValue);
        added = result != -1;
        return added;
    }

    public boolean storeUser(String uid, String userName, String role, String fullName, String token) {
        boolean added;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValue = new ContentValues();
        contentValue.put("id", uid);
        contentValue.put("userName", userName);
        contentValue.put("role", role);
        contentValue.put("fullName", fullName);
        contentValue.put("token", token);
        long result = db.insert("tbl_users", null, contentValue);
        added = result != -1;
        return added;
    }

    public HashMap<String, String> getConfig() {
        HashMap<String, String> path = new HashMap<>();
        String selectQuery = "SELECT * from tbl_config";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        // Move to first row
        cursor.moveToFirst();
        if (cursor.getCount() > 0) {
            path.put("url", cursor.getString(1));
        }
        cursor.close();
        db.close();
        return path;
    }

    public Cursor getUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * from tbl_users", null);
    }

    public void deleteConfig() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from tbl_config");
    }

    public void deleteUser() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from tbl_users");
    }
}
