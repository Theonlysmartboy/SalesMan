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
    private static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "cypos.db";
    
    private static final String SQL_CREATE_CONFIG_TABLE = "CREATE TABLE tbl_config (" +
            "id INTEGER PRIMARY KEY NOT NULL," +
            "path varchar(100) NOT NULL);";
    
    private static final String SQL_CREATE_USERS_TABLE = "CREATE TABLE tbl_users (" +
            "id INTEGER PRIMARY KEY NOT NULL," +
            "userName varchar(100) NOT NULL," +
            "has_pin tinyint(1) default 0," +
            "pin_hash TEXT," +
            "role varchar(65) NOT NULL," +
            "fullName varchar(100) NOT NULL," +
            "token varchar(255) NOT NULL);";
    
    private static final String SQL_CREATE_ORDERS_TABLE = "CREATE TABLE tbl_cart (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "product_code TEXT UNIQUE," +
            "product_name TEXT," +
            "unit_price REAL," +
            "quantity INTEGER NOT NULL);";

    private static final String SQL_CREATE_PARKED_CARTS_TABLE = "CREATE TABLE tbl_parked_carts (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT," +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);";

    private static final String SQL_CREATE_PARKED_CART_ITEMS_TABLE = "CREATE TABLE tbl_parked_cart_items (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "parked_cart_id INTEGER," +
            "product_code TEXT," +
            "product_name TEXT," +
            "unit_price REAL," +
            "quantity INTEGER," +
            "FOREIGN KEY(parked_cart_id) REFERENCES tbl_parked_carts(id) ON DELETE CASCADE);";

    public Db(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CONFIG_TABLE);
        db.execSQL(SQL_CREATE_USERS_TABLE);
        db.execSQL(SQL_CREATE_ORDERS_TABLE);
        db.execSQL(SQL_CREATE_PARKED_CARTS_TABLE);
        db.execSQL(SQL_CREATE_PARKED_CART_ITEMS_TABLE);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            db.execSQL(SQL_CREATE_PARKED_CARTS_TABLE);
            db.execSQL(SQL_CREATE_PARKED_CART_ITEMS_TABLE);
        } else {
            db.execSQL("DROP TABLE IF EXISTS tbl_config");
            db.execSQL("DROP TABLE IF EXISTS tbl_users");
            db.execSQL("DROP TABLE IF EXISTS tbl_cart");
            db.execSQL("DROP TABLE IF EXISTS tbl_parked_carts");
            db.execSQL("DROP TABLE IF EXISTS tbl_parked_cart_items");
            onCreate(db);
        }
    }

    public boolean storeConfig(String path) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValue = new ContentValues();
        contentValue.put("path", path);
        long result = db.insert("tbl_config", null, contentValue);
        return result != -1;
    }

    public boolean storeUser(String uid, String userName, boolean has_pin, String role, String fullName, String token) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Preserve existing pin_hash if it exists
        String existingPinHash = null;
        try (Cursor cursor = db.rawQuery("SELECT pin_hash FROM tbl_users WHERE id = ?", new String[]{uid})) {
            if (cursor.moveToFirst()) {
                existingPinHash = cursor.getString(0);
            }
        }

        ContentValues contentValue = new ContentValues();
        contentValue.put("id", uid);
        contentValue.put("userName", userName);
        contentValue.put("has_pin", has_pin);
        contentValue.put("role", role);
        contentValue.put("fullName", fullName);
        contentValue.put("token", token);
        
        if (existingPinHash != null) {
            contentValue.put("pin_hash", existingPinHash);
        }

        long result = db.insertWithOnConflict("tbl_users", null, contentValue,
                SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public boolean saveUserPin(String userId, String pinHash) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("pin_hash", pinHash);
        cv.put("has_pin", 1);
        long result = db.update("tbl_users", cv, "id=?", new String[]{userId});
        return result != -1;
    }

    public String getUserPinHash(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT pin_hash FROM tbl_users WHERE id=? LIMIT 1",
                new String[]{userId})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
    }

    public boolean userHasPin(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT has_pin, pin_hash FROM tbl_users WHERE id=? LIMIT 1",
                new String[]{userId})) {

            if (cursor.moveToFirst()) {
                boolean hasPinFlag = cursor.getInt(0) == 1;
                String pinHash = cursor.getString(1);
                // Consider user as having a PIN only if we have the local hash too
                return hasPinFlag && pinHash != null && !pinHash.isEmpty();
            }
            return false;
        }
    }

    public boolean updatePinLocal(String userId, int hasPin) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("has_pin", hasPin);
        int rows = db.update(
                "tbl_users",
                values,
                "id = ?",
                new String[]{userId}
        );
        return rows > 0;
    }

    public HashMap<String, String> getConfig() {
        HashMap<String, String> path = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * from tbl_config", null)) {
            if (cursor.moveToFirst()) {
                path.put("url", cursor.getString(1));
            }
        }
        return path;
    }

    public String getToken() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT token FROM tbl_users LIMIT 1", null)) {
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
        this.getWritableDatabase().execSQL("DELETE FROM tbl_users");
    }

    public boolean storeOrder(String productCode, String productName, double unitPrice, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValue = new ContentValues();
        contentValue.put("product_code", productCode);
        contentValue.put("product_name", productName);
        contentValue.put("unit_price", unitPrice);
        contentValue.put("quantity", quantity);
        long result = db.insertWithOnConflict("tbl_cart", null, contentValue, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public int getCartCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM tbl_cart", null)) {
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        }
    }

    public int getProductQuantity(String productCode) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query("tbl_cart", new String[]{"quantity"}, "product_code=?", new String[]{productCode}, null, null, null)) {
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        }
    }

    public List<HashMap<String, String>> getCartItems() {
        List<HashMap<String, String>> items = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM tbl_cart", null)) {
            while (cursor.moveToNext()) {
                HashMap<String, String> item = new HashMap<>();
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
        SQLiteDatabase db = this.getWritableDatabase();
        if (quantity <= 0) {
            db.delete("tbl_cart", "product_code=?", new String[]{productCode});
        } else {
            ContentValues cv = new ContentValues();
            cv.put("quantity", quantity);
            db.update("tbl_cart", cv, "product_code=?", new String[]{productCode});
        }
    }

    public void deleteCartItem(String productCode) {
        this.getWritableDatabase().delete("tbl_cart", "product_code=?", new String[]{productCode});
    }

    public void clearCart() {
        this.getWritableDatabase().execSQL("DELETE FROM tbl_cart");
    }

    public long createParkedCart(String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        return db.insert("tbl_parked_carts", null, cv);
    }

    public void moveSingleItemToParkedCart(String productCode, long parkedCartId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor cursor = db.query("tbl_cart", null, "product_code=?", new String[]{productCode}, null, null, null)) {
                if (cursor.moveToFirst()) {
                    ContentValues cv = new ContentValues();
                    cv.put("parked_cart_id", parkedCartId);
                    cv.put("product_code", cursor.getString(cursor.getColumnIndexOrThrow("product_code")));
                    cv.put("product_name", cursor.getString(cursor.getColumnIndexOrThrow("product_name")));
                    cv.put("unit_price", cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
                    cv.put("quantity", cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
                    db.insert("tbl_parked_cart_items", null, cv);
                    db.delete("tbl_cart", "product_code=?", new String[]{productCode});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void moveEntireCartToParkedCart(String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cvCart = new ContentValues();
            cvCart.put("name", name);
            long parkedCartId = db.insert("tbl_parked_carts", null, cvCart);
            
            try (Cursor cursor = db.query("tbl_cart", null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues cvItem = new ContentValues();
                    cvItem.put("parked_cart_id", parkedCartId);
                    cvItem.put("product_code", cursor.getString(cursor.getColumnIndexOrThrow("product_code")));
                    cvItem.put("product_name", cursor.getString(cursor.getColumnIndexOrThrow("product_name")));
                    cvItem.put("unit_price", cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
                    cvItem.put("quantity", cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
                    db.insert("tbl_parked_cart_items", null, cvItem);
                }
            }
            db.delete("tbl_cart", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<HashMap<String, String>> getParkedCarts() {
        List<HashMap<String, String>> carts = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT pc.*, (SELECT COUNT(*) FROM tbl_parked_cart_items pci WHERE pci.parked_cart_id = pc.id) as item_count, " +
                "(SELECT SUM(unit_price * quantity) FROM tbl_parked_cart_items pci WHERE pci.parked_cart_id = pc.id) as total_amount " +
                "FROM tbl_parked_carts pc ORDER BY created_at DESC";
        try (Cursor cursor = db.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                HashMap<String, String> cart = new HashMap<>();
                cart.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                cart.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")));
                cart.put("created_at", cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
                cart.put("item_count", cursor.getString(cursor.getColumnIndexOrThrow("item_count")));
                cart.put("total_amount", cursor.getString(cursor.getColumnIndexOrThrow("total_amount")));
                carts.add(cart);
            }
        }
        return carts;
    }

    public int getParkedCartsCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM tbl_parked_carts", null)) {
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        }
    }

    public void restoreParkedCart(long parkedCartId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor cursor = db.query("tbl_parked_cart_items", null, "parked_cart_id=?", new String[]{String.valueOf(parkedCartId)}, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues cv = new ContentValues();
                    cv.put("product_code", cursor.getString(cursor.getColumnIndexOrThrow("product_code")));
                    cv.put("product_name", cursor.getString(cursor.getColumnIndexOrThrow("product_name")));
                    cv.put("unit_price", cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")));
                    cv.put("quantity", cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
                    db.insertWithOnConflict("tbl_cart", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.delete("tbl_parked_carts", "id=?", new String[]{String.valueOf(parkedCartId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteParkedCart(long parkedCartId) {
        this.getWritableDatabase().delete("tbl_parked_carts", "id=?", new String[]{String.valueOf(parkedCartId)});
    }
}
