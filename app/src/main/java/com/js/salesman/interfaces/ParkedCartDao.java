package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.js.salesman.models.CartItem;
import com.js.salesman.models.ParkedCart;
import com.js.salesman.models.ParkedCartItem;
import com.js.salesman.models.ParkedCartSummary;

import java.util.List;

@Dao
public interface ParkedCartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertParkedCart(ParkedCart parkedCart);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertParkedCartItem(ParkedCartItem item);

    @Query("SELECT pc.id, pc.name, pc.customer_code AS customer_code, pc.customer_json AS customer_json, " +
            "pc.created_at AS created_at, " +
            "(SELECT COUNT(*) FROM parked_cart_items pci WHERE pci.parked_cart_id = pc.id) AS item_count, " +
            "COALESCE((SELECT SUM(unit_price * quantity) FROM parked_cart_items pci WHERE pci.parked_cart_id = pc.id), 0.0) AS total_amount " +
            "FROM parked_carts pc ORDER BY pc.id DESC")
    List<ParkedCartSummary> getParkedCartSummaries();

    @Query("SELECT COUNT(*) FROM parked_carts")
    int getParkedCartsCount();

    @Query("SELECT * FROM parked_carts WHERE customer_code = :customerCode LIMIT 1")
    ParkedCart getParkedCartByCustomerCode(String customerCode);

    @Query("SELECT * FROM parked_cart_items WHERE parked_cart_id = :parkedCartId AND product_code = :productCode LIMIT 1")
    ParkedCartItem getParkedCartItem(long parkedCartId, String productCode);

    @Query("SELECT * FROM parked_cart_items WHERE parked_cart_id = :parkedCartId")
    List<ParkedCartItem> getParkedCartItems(long parkedCartId);

    @Query("UPDATE parked_carts SET customer_json = :customerJson WHERE id = :id")
    void updateParkedCartCustomerJson(long id, String customerJson);

    @Query("UPDATE parked_cart_items SET quantity = :quantity WHERE id = :id")
    void updateParkedCartItemQuantity(long id, int quantity);

    @Query("DELETE FROM parked_carts WHERE id = :parkedCartId")
    void deleteParkedCart(long parkedCartId);

    @Transaction
    default void moveSingleItemToParkedCart(String customerCode, String customerName, String customerJson, CartItem cartItem) {
        if (cartItem == null) return;
        ParkedCart parkedCart = getParkedCartByCustomerCode(customerCode);
        long parkedCartId;
        if (parkedCart != null) {
            parkedCartId = parkedCart.getId();
            updateParkedCartCustomerJson(parkedCartId, customerJson);
        } else {
            ParkedCart newCart = new ParkedCart();
            newCart.setName(customerName + "(" + customerCode + ")");
            newCart.setCustomerCode(customerCode);
            newCart.setCustomerJson(customerJson);
            newCart.setCreatedAt(String.valueOf(System.currentTimeMillis()));
            parkedCartId = insertParkedCart(newCart);
        }

        ParkedCartItem existingItem = getParkedCartItem(parkedCartId, cartItem.getProductCode());
        if (existingItem != null) {
            updateParkedCartItemQuantity(existingItem.getId(), existingItem.getQuantity() + cartItem.getQuantity());
        } else {
            ParkedCartItem newItem = new ParkedCartItem();
            newItem.setParkedCartId(parkedCartId);
            newItem.setProductCode(cartItem.getProductCode());
            newItem.setProductName(cartItem.getProductName());
            newItem.setUnitPrice(cartItem.getUnitPrice());
            newItem.setQuantity(cartItem.getQuantity());
            insertParkedCartItem(newItem);
        }
    }

    @Transaction
    default void moveEntireCartToParkedCart(String customerCode, String customerName, String customerJson, List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) return;
        ParkedCart parkedCart = getParkedCartByCustomerCode(customerCode);
        long parkedCartId;
        if (parkedCart != null) {
            parkedCartId = parkedCart.getId();
            updateParkedCartCustomerJson(parkedCartId, customerJson);
        } else {
            ParkedCart newCart = new ParkedCart();
            newCart.setName(customerName + "(" + customerCode + ")");
            newCart.setCustomerCode(customerCode);
            newCart.setCustomerJson(customerJson);
            newCart.setCreatedAt(String.valueOf(System.currentTimeMillis()));
            parkedCartId = insertParkedCart(newCart);
        }

        for (CartItem cartItem : cartItems) {
            ParkedCartItem existingItem = getParkedCartItem(parkedCartId, cartItem.getProductCode());
            if (existingItem != null) {
                updateParkedCartItemQuantity(existingItem.getId(), existingItem.getQuantity() + cartItem.getQuantity());
            } else {
                ParkedCartItem newItem = new ParkedCartItem();
                newItem.setParkedCartId(parkedCartId);
                newItem.setProductCode(cartItem.getProductCode());
                newItem.setProductName(cartItem.getProductName());
                newItem.setUnitPrice(cartItem.getUnitPrice());
                newItem.setQuantity(cartItem.getQuantity());
                insertParkedCartItem(newItem);
            }
        }
    }
}
