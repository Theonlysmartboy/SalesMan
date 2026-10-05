package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.js.salesman.models.CartItem;

import java.util.List;

@Dao
public interface CartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertItem(CartItem item);

    @Query("SELECT * FROM cart_items")
    List<CartItem> getAllCartItems();

    @Query("SELECT COUNT(*) FROM cart_items")
    int getCartCount();

    @Query("SELECT quantity FROM cart_items WHERE product_code = :productCode LIMIT 1")
    Integer getProductQuantity(String productCode);

    @Query("SELECT * FROM cart_items WHERE product_code = :productCode LIMIT 1")
    CartItem getItemByProductCode(String productCode);

    @Query("UPDATE cart_items SET quantity = :quantity WHERE product_code = :productCode")
    void updateQuantity(String productCode, int quantity);

    @Query("DELETE FROM cart_items WHERE product_code = :productCode")
    void deleteCartItem(String productCode);

    @Query("DELETE FROM cart_items")
    void clearCart();
}
