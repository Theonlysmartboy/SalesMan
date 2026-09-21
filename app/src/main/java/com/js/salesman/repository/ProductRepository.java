package com.js.salesman.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.database.AppDatabase;
import com.js.salesman.database.ProductDao;
import com.js.salesman.database.SyncDao;
import com.js.salesman.database.SyncMetadata;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.utils.NetworkUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ProductRepository {
    private static final String TAG = "ProductRepository";
    private static final String SYNC_TYPE_PRODUCTS = "products";
    private final ProductDao productDao;
    private final SyncDao syncDao;
    private final ApiInterface apiInterface;
    private final Executor executor;

    public ProductRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.productDao = db.productDao();
        this.syncDao = db.syncDao();
        this.apiInterface = ApiClient.getApi(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Product>> getProducts() {
        return productDao.getAllProducts();
    }

    public LiveData<List<Product>> searchProducts(String query) {
        return productDao.searchProducts(query);
    }

    public LiveData<Product> getProductByCode(String code) {
        return productDao.getProductByCode(code);
    }

    public boolean hasCachedProducts() {
        return productDao.getCount() > 0;
    }

    public void syncProducts(SyncCallback callback) {
        executor.execute(() -> {
            try {
                String lastSync = syncDao.getLastSyncTimestamp(SYNC_TYPE_PRODUCTS);
                if (lastSync == null) {
                    // Initial sync - fetch from a long time ago
                    lastSync = "2010-01-01 00:00:00";
                }

                Log.d(TAG, "Syncing products since: " + lastSync);

                Response<ProductListResponse> response = apiInterface.syncProducts(
                        "sync", lastSync, 500, 0, 0.0, 0.0
                ).execute();

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Product> products = response.body().getData();
                    if (products != null && !products.isEmpty()) {
                        productDao.updateProducts(products);
                        Log.d(TAG, "Database updated with " + products.size() + " products");
                    }

                    // Update sync metadata with current time
                    String newSyncTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    syncDao.updateLastSyncTimestamp(new SyncMetadata(SYNC_TYPE_PRODUCTS, newSyncTime));
                    
                    if (callback != null) callback.onSuccess();
                } else {
                    String error = "Unknown error";
                    if (response.body() != null) {
                        error = response.body().getMessage();
                    }
                    Log.e(TAG, "Sync failed: " + error);
                    if (callback != null) callback.onError(error);
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync exception", e);
                if (callback != null) {
                    String friendlyError = NetworkUtil.getFriendlyNetError(null, e, true);
                    callback.onError(friendlyError);
                }
            }
        });
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String message);
    }
}
