package com.js.salesman.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.js.salesman.interfaces.CartDao;
import com.js.salesman.interfaces.ParkedCartDao;
import com.js.salesman.models.CartItem;
import com.js.salesman.models.Customer;
import com.js.salesman.models.ParkedCartItem;
import com.js.salesman.models.ParkedCartSummary;
import com.js.salesman.utils.database.AppDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CartRepository {
    private static final String TAG = "CartRepository";
    private static final Gson GSON = new Gson();

    private static volatile CartRepository INSTANCE;

    private final AppDatabase db;
    private final CartDao cartDao;
    private final ParkedCartDao parkedCartDao;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private CartRepository(Context context) {
        this.db = AppDatabase.getInstance(context);
        this.cartDao = db.cartDao();
        this.parkedCartDao = db.parkedCartDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static CartRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CartRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CartRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /** Callback invoked on the main thread. */
    public interface Callback<T> {
        void onResult(T result);
    }

    public void storeOrder(String productCode, String productName, double unitPrice, int quantity,
                            @Nullable Callback<Boolean> callback) {
        executor.execute(() -> deliver(callback,
                storeOrderInternal(productCode, productName, unitPrice, quantity)));
    }

    @WorkerThread
    public boolean storeOrderSync(String productCode, String productName, double unitPrice, int quantity) {
        return storeOrderInternal(productCode, productName, unitPrice, quantity);
    }

    public void updateCartQuantity(String productCode, int quantity, @Nullable Runnable onComplete) {
        write("updateCartQuantity", () -> {
            if (quantity <= 0) {
                cartDao.deleteCartItem(productCode);
            } else {
                cartDao.updateQuantity(productCode, quantity);
            }
        }, onComplete);
    }

    public void deleteCartItem(String productCode, @Nullable Runnable onComplete) {
        write("deleteCartItem", () -> cartDao.deleteCartItem(productCode), onComplete);
    }

    public void clearCart(@Nullable Runnable onComplete) {
        write("clearCart", cartDao::clearCart, onComplete);
    }

    public void getCartCount(@Nullable Callback<Integer> callback) {
        read("getCartCount", cartDao::getCartCount, 0, callback);
    }

    @WorkerThread
    public int getCartCountSync() {
        return cartDao.getCartCount();
    }

    public void getProductQuantity(String productCode, @Nullable Callback<Integer> callback) {
        read("getProductQuantity", () -> loadProductQuantity(productCode), 0, callback);
    }

    @WorkerThread
    public int getProductQuantitySync(String productCode) {
        return loadProductQuantity(productCode);
    }

    public void getCartItems(@Nullable Callback<List<HashMap<String, String>>> callback) {
        read("getCartItems", this::loadCartItems, Collections.emptyList(), callback);
    }

    @WorkerThread
    public List<HashMap<String, String>> getCartItemsSync() {
        return loadCartItems();
    }

    public void moveSingleItemToParkedCart(Customer customer, String productCode, @Nullable Runnable onComplete) {
        write("moveSingleItemToParkedCart", () -> {
            if (customer == null) {
                Log.w(TAG, "moveSingleItemToParkedCart: customer is null, nothing moved");
                return;
            }
            CartItem cartItem = cartDao.getItemByProductCode(productCode);
            if (cartItem == null) {
                Log.w(TAG, "moveSingleItemToParkedCart: no cart item for code=" + productCode);
                return;
            }
            parkedCartDao.moveSingleItemToParkedCart(
                    customer.getCustomerCode(), customer.getCustomerName(), GSON.toJson(customer), cartItem);
            cartDao.deleteCartItem(productCode);
        }, onComplete);
    }

    public void moveEntireCartToParkedCart(Customer customer, @Nullable Runnable onComplete) {
        write("moveEntireCartToParkedCart", () -> {
            if (customer == null) {
                Log.w(TAG, "moveEntireCartToParkedCart: customer is null, nothing moved");
                return;
            }
            List<CartItem> items = cartDao.getAllCartItems();
            if (items == null || items.isEmpty()) {
                return;
            }
            parkedCartDao.moveEntireCartToParkedCart(
                    customer.getCustomerCode(), customer.getCustomerName(), GSON.toJson(customer), items);
            cartDao.clearCart();
        }, onComplete);
    }

    public void restoreParkedCart(long parkedCartId, @Nullable Runnable onComplete) {
        write("restoreParkedCart", () -> {
            List<ParkedCartItem> parkedItems = parkedCartDao.getParkedCartItems(parkedCartId);
            if (parkedItems != null) {
                for (ParkedCartItem pci : parkedItems) {
                    cartDao.insertItem(new CartItem(
                            pci.getProductCode(), pci.getProductName(), pci.getUnitPrice(), pci.getQuantity()));
                }
            }
            parkedCartDao.deleteParkedCart(parkedCartId);
        }, onComplete);
    }

    public void deleteParkedCart(long parkedCartId, @Nullable Runnable onComplete) {
        write("deleteParkedCart", () -> parkedCartDao.deleteParkedCart(parkedCartId), onComplete);
    }

    public void getParkedCarts(@Nullable Callback<List<HashMap<String, String>>> callback) {
        read("getParkedCarts", this::loadParkedCarts, Collections.emptyList(), callback);
    }

    @WorkerThread
    public List<HashMap<String, String>> getParkedCartsSync() {
        return loadParkedCarts();
    }

    public void getParkedCartsCount(@Nullable Callback<Integer> callback) {
        read("getParkedCartsCount", parkedCartDao::getParkedCartsCount, 0, callback);
    }

    @WorkerThread
    public int getParkedCartsCountSync() {
        return parkedCartDao.getParkedCartsCount();
    }

    private boolean storeOrderInternal(String productCode, String productName, double unitPrice, int quantity) {
        if (quantity <= 0) {
            Log.w(TAG, "storeOrder: rejected non-positive quantity=" + quantity + " for code=" + productCode);
            return false;
        }
        try {
            cartDao.insertItem(new CartItem(productCode, productName, unitPrice, quantity));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "storeOrder: failed for code=" + productCode, e);
            return false;
        }
    }

    private int loadProductQuantity(String productCode) {
        Integer qty = cartDao.getProductQuantity(productCode);
        return qty != null ? qty : 0;
    }

    private List<HashMap<String, String>> loadCartItems() {
        List<CartItem> items = cartDao.getAllCartItems();
        List<HashMap<String, String>> result = new ArrayList<>();
        if (items == null) return result;
        for (CartItem item : items) {
            HashMap<String, String> map = new HashMap<>();
            map.put("product_code", item.getProductCode());
            map.put("product_name", item.getProductName());
            map.put("unit_price", String.valueOf(item.getUnitPrice()));
            map.put("quantity", String.valueOf(item.getQuantity()));
            result.add(map);
        }
        return result;
    }

    private List<HashMap<String, String>> loadParkedCarts() {
        List<ParkedCartSummary> summaries = parkedCartDao.getParkedCartSummaries();
        List<HashMap<String, String>> carts = new ArrayList<>();
        if (summaries == null) return carts;
        for (ParkedCartSummary pcs : summaries) {
            HashMap<String, String> cart = new HashMap<>();
            cart.put("id", String.valueOf(pcs.id));
            cart.put("name", pcs.name != null ? pcs.name : "");
            cart.put("customer_code", pcs.customerCode != null ? pcs.customerCode : "");
            cart.put("customer_json", pcs.customerJson != null ? pcs.customerJson : "");
            cart.put("created_at", pcs.createdAt != null ? pcs.createdAt : "");
            cart.put("item_count", String.valueOf(pcs.itemCount));
            cart.put("total_amount", String.valueOf(pcs.totalAmount));
            carts.add(cart);
        }
        return carts;
    }

    private void write(@NonNull String operation, @NonNull Runnable work, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            try {
                db.runInTransaction(work);
            } catch (Exception e) {
                Log.e(TAG, operation + ": failed", e);
            }
            if (onComplete != null) mainHandler.post(onComplete);
        });
    }

    private <T> void read(@NonNull String operation, @NonNull Callable<T> query, T fallback,
                            @Nullable Callback<T> callback) {
        executor.execute(() -> {
            T result;
            try {
                result = query.call();
            } catch (Exception e) {
                Log.e(TAG, operation + ": failed", e);
                result = fallback;
            }
            deliver(callback, result);
        });
    }

    private <T> void deliver(@Nullable Callback<T> callback, T result) {
        if (callback != null) mainHandler.post(() -> callback.onResult(result));
    }
}