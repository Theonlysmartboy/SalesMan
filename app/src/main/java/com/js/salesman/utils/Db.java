package com.js.salesman.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 3;
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
    private static final String SQL_CREATE_ORDERS_TABLE = "CREATE TABLE tbl_cart (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "product_code TEXT UNIQUE," +
            "product_name TEXT," +
            "unit_price REAL," +
            "quantity INTEGER NOT NULL);";
    private static final String SQL_DELETE_CONFIG_TABLE = "DROP TABLE IF EXISTS tbl_config";
    private static final String SQL_DELETE_USERS_TABLE = "DROP TABLE IF EXISTS tbl_users";
    private static final String SQL_DELETE_ORDERS_TABLE = "DROP TABLE IF EXISTS tbl_cart";
    public Db(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CONFIG_TABLE);
        db.execSQL(SQL_CREATE_USERS_TABLE);
        db.execSQL(SQL_CREATE_ORDERS_TABLE);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_CONFIG_TABLE);
        db.execSQL(SQL_DELETE_USERS_TABLE);
        db.execSQL(SQL_DELETE_ORDERS_TABLE);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public boolean storeConfig(String path) {
        boolean added;
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            ContentValues contentValue = new ContentValues();
            contentValue.put("path", path);
            long result = db.insert("tbl_config", null, contentValue);
            added = result != -1;
        }
        return added;
    }

    public boolean storeUser(String uid, String userName, String role, String fullName, String token) {
        boolean added;
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            ContentValues contentValue = new ContentValues();
            contentValue.put("id", uid);
            contentValue.put("userName", userName);
            contentValue.put("role", role);
            contentValue.put("fullName", fullName);
            contentValue.put("token", token);
            long result = db.insert("tbl_users", null, contentValue);
            added = result != -1;
        }
        return added;
    }

    public HashMap<String, String> getConfig() {
        HashMap<String, String> path = new HashMap<>();
        String selectQuery = "SELECT * from tbl_config";
        try (SQLiteDatabase db = this.getReadableDatabase(); Cursor cursor = db.rawQuery(selectQuery, null)) {
            // Move to first row
            if (cursor.moveToFirst()) {
                path.put("url", cursor.getString(1));
            }
        }
        return path;
    }

    public List<HashMap<String, String>> getUserList() {
        List<HashMap<String, String>> users = new ArrayList<>();
        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery("SELECT * FROM tbl_users", null)) {
            while (cursor.moveToNext()) {
                HashMap<String, String> user = new HashMap<>();
                user.put("id", cursor.getString(0));
                user.put("userName", cursor.getString(1));
                user.put("role", cursor.getString(2));
                user.put("fullName", cursor.getString(3));
                user.put("token", cursor.getString(4));
                users.add(user);
            }
        }
        return users;
    }

    public String getToken() {
        try (SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT token FROM tbl_users LIMIT 1",
                    null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
    }

    public void deleteConfig() {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.execSQL("DELETE FROM tbl_config");
        }
    }

    public void deleteUser() {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.execSQL("DELETE FROM tbl_users");
        }
    }

    public boolean storeOrder(String productCode, String productName, double unitPrice, int quantity) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            ContentValues contentValue = new ContentValues();
            contentValue.put("product_code", productCode);
            contentValue.put("product_name", productName);
            contentValue.put("unit_price", unitPrice);
            contentValue.put("quantity", quantity);
            // Use replace to handle uniqueness (update if exists)
            long result = db.insertWithOnConflict("tbl_cart", null, contentValue, SQLiteDatabase.CONFLICT_REPLACE);
            return result != -1;
        }
    }

    public int getCartCount() {
        try (SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM tbl_cart", null)) {
            int count = 0;
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            return count;
        }
    }

    public int getProductQuantity(String productCode) {
        try (SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("tbl_cart", new String[]{"quantity"},
                "product_code=?", new String[]{productCode},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
    }

    public List<HashMap<String, String>> getCartItems() {
        List<HashMap<String, String>> items = new ArrayList<>();
        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery("SELECT * FROM tbl_cart", null)) {
            while (cursor.moveToNext()) {
                HashMap<String, String> item = new HashMap<>();
                item.put("id", cursor.getString(0));
                item.put("product_code", cursor.getString(1));
                item.put("product_name", cursor.getString(2));
                item.put("unit_price", cursor.getString(3));
                item.put("quantity", cursor.getString(4));
                items.add(item);
            }
        }
        return items;
    }

    public void updateCartQuantity(String productCode, int quantity) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            if (quantity <= 0) {
                db.delete("tbl_cart", "product_code=?", new String[]{productCode});
            } else {
                ContentValues cv = new ContentValues();
                cv.put("quantity", quantity);
                db.update("tbl_cart", cv, "product_code=?", new String[]{productCode});
            }
        }
    }

    public void deleteCartItem(String productCode) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.delete("tbl_cart", "product_code=?", new String[]{productCode});
        }
    }

    public void clearCart() {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.execSQL("DELETE FROM tbl_cart");
        }
    }
}
