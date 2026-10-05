package com.js.salesman.repository;

import android.content.Context;

import com.google.gson.Gson;
import com.js.salesman.interfaces.CartDao;
import com.js.salesman.interfaces.ParkedCartDao;
import com.js.salesman.models.CartItem;
import com.js.salesman.models.Customer;
import com.js.salesman.models.ParkedCartItem;
import com.js.salesman.models.ParkedCartSummary;
import com.js.salesman.utils.database.AppDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CartRepository {
    private static volatile CartRepository INSTANCE;
    private final CartDao cartDao;
    private final ParkedCartDao parkedCartDao;
    private final ExecutorService executor;

    private CartRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
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

    public interface Callback<T> {
        void onResult(T result);
    }

    public void storeOrder(String productCode, String productName, double unitPrice, int quantity, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                CartItem item = new CartItem(productCode, productName, unitPrice, quantity);
                cartDao.insertItem(item);
                if (callback != null) callback.onResult(true);
            } catch (Exception e) {
                if (callback != null) callback.onResult(false);
            }
        });
    }

    public boolean storeOrderSync(String productCode, String productName, double unitPrice, int quantity) {
        try {
            CartItem item = new CartItem(productCode, productName, unitPrice, quantity);
            cartDao.insertItem(item);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void getCartCount(Callback<Integer> callback) {
        executor.execute(() -> {
            int count = cartDao.getCartCount();
            if (callback != null) callback.onResult(count);
        });
    }

    public int getCartCountSync() {
        return cartDao.getCartCount();
    }

    public void getProductQuantity(String productCode, Callback<Integer> callback) {
        executor.execute(() -> {
            Integer qty = cartDao.getProductQuantity(productCode);
            if (callback != null) callback.onResult(qty != null ? qty : 0);
        });
    }

    public int getProductQuantitySync(String productCode) {
        Integer qty = cartDao.getProductQuantity(productCode);
        return qty != null ? qty : 0;
    }

    public void getCartItems(Callback<List<HashMap<String, String>>> callback) {
        executor.execute(() -> {
            List<HashMap<String, String>> result = getCartItemsSync();
            if (callback != null) callback.onResult(result);
        });
    }

    public List<HashMap<String, String>> getCartItemsSync() {
        List<CartItem> items = cartDao.getAllCartItems();
        List<HashMap<String, String>> resultList = new ArrayList<>();
        for (CartItem item : items) {
            HashMap<String, String> map = new HashMap<>();
            map.put("product_code", item.getProductCode());
            map.put("product_name", item.getProductName());
            map.put("unit_price", String.valueOf(item.getUnitPrice()));
            map.put("quantity", String.valueOf(item.getQuantity()));
            resultList.add(map);
        }
        return resultList;
    }

    public void updateCartQuantity(String productCode, int quantity, Runnable onComplete) {
        executor.execute(() -> {
            if (quantity <= 0) {
                cartDao.deleteCartItem(productCode);
            } else {
                cartDao.updateQuantity(productCode, quantity);
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteCartItem(String productCode, Runnable onComplete) {
        executor.execute(() -> {
            cartDao.deleteCartItem(productCode);
            if (onComplete != null) onComplete.run();
        });
    }

    public void clearCart(Runnable onComplete) {
        executor.execute(() -> {
            cartDao.clearCart();
            if (onComplete != null) onComplete.run();
        });
    }

    public void moveSingleItemToParkedCart(Customer customer, String productCode, Runnable onComplete) {
        executor.execute(() -> {
            CartItem cartItem = cartDao.getItemByProductCode(productCode);
            if (cartItem != null && customer != null) {
                String customerJson = new Gson().toJson(customer);
                parkedCartDao.moveSingleItemToParkedCart(customer.getCustomerCode(), customer.getCustomerName(), customerJson, cartItem);
                cartDao.deleteCartItem(productCode);
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void moveEntireCartToParkedCart(Customer customer, Runnable onComplete) {
        executor.execute(() -> {
            List<CartItem> items = cartDao.getAllCartItems();
            if (items != null && !items.isEmpty() && customer != null) {
                String customerJson = new Gson().toJson(customer);
                parkedCartDao.moveEntireCartToParkedCart(customer.getCustomerCode(), customer.getCustomerName(), customerJson, items);
                cartDao.clearCart();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void getParkedCarts(Callback<List<HashMap<String, String>>> callback) {
        executor.execute(() -> {
            List<HashMap<String, String>> result = getParkedCartsSync();
            if (callback != null) callback.onResult(result);
        });
    }

    public List<HashMap<String, String>> getParkedCartsSync() {
        List<ParkedCartSummary> summaries = parkedCartDao.getParkedCartSummaries();
        List<HashMap<String, String>> carts = new ArrayList<>();
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

    public void getParkedCartsCount(Callback<Integer> callback) {
        executor.execute(() -> {
            int count = parkedCartDao.getParkedCartsCount();
            if (callback != null) callback.onResult(count);
        });
    }

    public int getParkedCartsCountSync() {
        return parkedCartDao.getParkedCartsCount();
    }

    public void restoreParkedCart(long parkedCartId, Runnable onComplete) {
        executor.execute(() -> {
            List<ParkedCartItem> parkedItems = parkedCartDao.getParkedCartItems(parkedCartId);
            if (parkedItems != null) {
                for (ParkedCartItem pci : parkedItems) {
                    CartItem cartItem = new CartItem(pci.getProductCode(), pci.getProductName(), pci.getUnitPrice(), pci.getQuantity());
                    cartDao.insertItem(cartItem);
                }
            }
            parkedCartDao.deleteParkedCart(parkedCartId);
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteParkedCart(long parkedCartId, Runnable onComplete) {
        executor.execute(() -> {
            parkedCartDao.deleteParkedCart(parkedCartId);
            if (onComplete != null) onComplete.run();
        });
    }
}
