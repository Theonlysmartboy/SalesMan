package com.js.salesman.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.js.salesman.models.Product;

import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products")
    LiveData<List<Product>> getAllProducts();

    @Query("SELECT * FROM products WHERE (ProductName LIKE '%' || :query || '%' OR ProductCode LIKE '%' || :query || '%')")
    LiveData<List<Product>> searchProducts(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProducts(List<Product> products);

    @Query("DELETE FROM products")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM products")
    int getCount();

    @Query("SELECT * FROM products WHERE ProductCode = :code LIMIT 1")
    LiveData<Product> getProductByCode(String code);

    @Transaction
    default void updateProducts(List<Product> products) {
        insertProducts(products);
    }
}
