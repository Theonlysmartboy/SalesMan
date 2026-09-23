package com.js.salesman.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.js.salesman.clients.ApiClient;
import com.js.salesman.utils.database.AppDatabase;
import com.js.salesman.interfaces.ProductDao;
import com.js.salesman.interfaces.SyncDao;
import com.js.salesman.utils.database.SyncMetadata;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.models.ProductResponse;
import com.js.salesman.utils.NetworkUtil;
import com.js.salesman.utils.managers.SessionManager;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

public class ProductRepository {
    private static final String TAG = "ProductRepository";
    private static final String SYNC_TYPE_PRODUCTS = "products";
    private final Context context;
    private final ProductDao productDao;
    private final SyncDao syncDao;
    private final ApiInterface apiInterface;
    private final Executor executor;

    public ProductRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(this.context);
        this.productDao = db.productDao();
        this.syncDao = db.syncDao();
        this.apiInterface = ApiClient.getApi(this.context);
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

    public interface GetProductsCallback {
        void onSuccess(List<Product> products, boolean hasNextPage);
        void onError(String message);
    }

    public interface GetProductDetailsCallback {
        void onSuccess(Product product, String source);
        void onError(String message);
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String message);
    }

    private Double getValidLat(SessionManager session) {
        Double lat = session.getCachedLat();
        if (lat != null && lat == 0.0) return null;
        return lat;
    }

    private Double getValidLng(SessionManager session) {
        Double lng = session.getCachedLng();
        if (lng != null && lng == 0.0) return null;
        return lng;
    }

    public void getProductsPaged(int limit, int offset, String query, GetProductsCallback callback) {
        executor.execute(() -> {
            boolean isOnline = NetworkUtil.isNetworkAvailable(context);
            String mode = isOnline ? "ONLINE" : "OFFLINE";
            SessionManager session = new SessionManager(context);
            Double lat = getValidLat(session);
            Double lng = getValidLng(session);

            Log.d(TAG, String.format("getProductsPaged [%s]: offset=%d, limit=%d, query='%s', lat=%s, lng=%s",
                    mode, offset, limit, query != null ? query : "", lat, lng));

            if (isOnline) {
                try {
                    Call<ProductListResponse> call;
                    if (query == null || query.trim().isEmpty()) {
                        call = apiInterface.getProductsPaged("sync", limit, offset, lat, lng);
                    } else {
                        call = apiInterface.searchProductsPaged("search", query.trim(), limit, offset, lat, lng);
                    }

                    Log.d(TAG, String.format("ONLINE PRODUCT REQUEST: limit=%d, offset=%d, query='%s', lat=%s, lng=%s",
                            limit, offset, query != null ? query : "", lat, lng));

                    Response<ProductListResponse> response = call.execute();

                    Log.d(TAG, String.format("ONLINE PRODUCT RESPONSE: httpCode=%d, isSuccessful=%b, bodyNotNull=%b, isSuccess=%b",
                            response.code(), response.isSuccessful(), response.body() != null,
                            response.body() != null && response.body().isSuccess()));

                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        List<Product> products = response.body().getData();
                        if (products == null) products = Collections.emptyList();
                        boolean hasNext = products.size() >= limit;
                        String firstCode = !products.isEmpty() ? products.get(0).getProductCode() : "N/A";
                        String lastCode = !products.isEmpty() ? products.get(products.size() - 1).getProductCode() : "N/A";

                        Log.d(TAG, String.format("ONLINE PRODUCT RESULT: parsedCount=%d, firstProduct=%s, lastProduct=%s, hasNext=%b",
                                products.size(), firstCode, lastCode, hasNext));

                        if (callback != null) callback.onSuccess(products, hasNext);
                        return;
                    } else {
                        Log.w(TAG, "getProductsPaged [ONLINE] API call unsuccessful, falling back to local cache.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getProductsPaged [ONLINE] Network error, falling back to local cache.", e);
                }
            }

            // OFFLINE or Online fallback to local Room cache
            try {
                List<Product> products;
                if (query == null || query.trim().isEmpty()) {
                    products = productDao.getProductsPaged(limit, offset);
                } else {
                    products = productDao.searchProductsPaged(query.trim(), limit, offset);
                }
                if (products == null) products = Collections.emptyList();
                boolean hasNext = products.size() >= limit;
                Log.d(TAG, String.format("getProductsPaged [OFFLINE/CACHE]: retrieved %d items, hasNextPage=%b",
                        products.size(), hasNext));
                if (callback != null) callback.onSuccess(products, hasNext);
            } catch (Exception e) {
                Log.e(TAG, "getProductsPaged [OFFLINE/CACHE] Error reading local database", e);
                if (callback != null) {
                    callback.onError(NetworkUtil.getFriendlyNetError(null, e, isOnline));
                }
            }
        });
    }

    public void getProductsPagedOnlineOnly(int limit, int offset, String query, GetProductsCallback callback) {
        executor.execute(() -> {
            boolean isOnline = NetworkUtil.isNetworkAvailable(context);
            if (!isOnline) {
                Log.d(TAG, "getProductsPagedOnlineOnly: Device is OFFLINE. Skipping online product selection.");
                if (callback != null) callback.onError("Internet connection required for product selection.");
                return;
            }

            SessionManager session = new SessionManager(context);
            Double lat = getValidLat(session);
            Double lng = getValidLng(session);

            Log.d(TAG, String.format("SalesOrder ONLINE PRODUCT REQUEST: limit=%d, offset=%d, query='%s', lat=%s, lng=%s",
                    limit, offset, query != null ? query : "", lat, lng));

            try {
                Call<ProductListResponse> call;
                if (query == null || query.trim().isEmpty()) {
                    call = apiInterface.getProductsPaged("sync", limit, offset, lat, lng);
                } else {
                    call = apiInterface.searchProductsPaged("search", query.trim(), limit, offset, lat, lng);
                }
                Response<ProductListResponse> response = call.execute();

                Log.d(TAG, String.format("SalesOrder ONLINE PRODUCT RESPONSE: httpCode=%d, isSuccessful=%b, bodyNotNull=%b, isSuccess=%b",
                        response.code(), response.isSuccessful(), response.body() != null,
                        response.body() != null && response.body().isSuccess()));

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Product> products = response.body().getData();
                    if (products == null) products = Collections.emptyList();
                    boolean hasNext = products.size() >= limit;
                    String firstCode = !products.isEmpty() ? products.get(0).getProductCode() : "N/A";
                    String lastCode = !products.isEmpty() ? products.get(products.size() - 1).getProductCode() : "N/A";

                    Log.d(TAG, String.format("SalesOrder ONLINE PRODUCT RESULT: receivedCount=%d, firstProduct=%s, lastProduct=%s",
                            products.size(), firstCode, lastCode));

                    if (callback != null) callback.onSuccess(products, hasNext);
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : "HTTP error " + response.code();
                    Log.w(TAG, "SalesOrder ONLINE PRODUCT REQUEST Unsuccessful: " + msg);
                    if (callback != null) callback.onError("Unable to load products from server.");
                }
            } catch (Exception e) {
                Log.e(TAG, "SalesOrder ONLINE PRODUCT REQUEST Exception:", e);
                if (callback != null) callback.onError(NetworkUtil.getFriendlyNetError(null, e, false));
            }
        });
    }

    public void getProductDetails(String code, GetProductDetailsCallback callback) {
        executor.execute(() -> {
            boolean isOnline = NetworkUtil.isNetworkAvailable(context);
            String mode = isOnline ? "ONLINE" : "OFFLINE";
            SessionManager session = new SessionManager(context);
            Double lat = getValidLat(session);
            Double lng = getValidLng(session);

            Log.d(TAG, String.format("getProductDetails [%s]: productID/code=%s, lat=%s, lng=%s", mode, code, lat, lng));

            if (isOnline) {
                try {
                    Response<ProductResponse> response = apiInterface.getProductDetails("get", code, lat, lng).execute();
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess() && response.body().getData() != null) {
                        Product product = response.body().getData();
                        Log.d(TAG, "getProductDetails [ONLINE] Success from API for code=" + code);
                        // Refresh local cache for this product
                        productDao.updateProducts(Collections.singletonList(product));
                        if (callback != null) callback.onSuccess(product, "API");
                        return;
                    } else {
                        Log.w(TAG, "getProductDetails [ONLINE] Unsuccessful API response, falling back to cache.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getProductDetails [ONLINE] Network exception, falling back to cache.", e);
                }
            }

            // OFFLINE or Fallback to cache
            try {
                Product product = productDao.getProductByCodeSync(code);
                if (product != null) {
                    Log.d(TAG, "getProductDetails [CACHE] Found product in local DB for code=" + code);
                    if (callback != null) callback.onSuccess(product, isOnline ? "Cache (Fallback)" : "Cache");
                } else {
                    Log.e(TAG, "getProductDetails [CACHE] Product not found in local DB for code=" + code);
                    if (callback != null) callback.onError("Product details not found.");
                }
            } catch (Exception e) {
                Log.e(TAG, "getProductDetails [CACHE] Error reading product from local DB", e);
                if (callback != null) callback.onError("Error loading product details.");
            }
        });
    }

    public void syncProducts(SyncCallback callback) {
        executor.execute(() -> {
            try {
                String oldLastSync = syncDao.getLastSyncTimestamp(SYNC_TYPE_PRODUCTS);
                String lastSync = oldLastSync;
                if (lastSync == null || lastSync.trim().isEmpty()) {
                    lastSync = "2010-01-01 00:00:00";
                }
                SessionManager session = new SessionManager(context);
                Double lat = getValidLat(session);
                Double lng = getValidLng(session);

                Log.d(TAG, "syncProducts [CACHE SYNC]: starting delta-sync with old lastSync=" + lastSync + ", lat=" + lat + ", lng=" + lng);
                Response<ProductListResponse> response = apiInterface.syncProducts(
                        "delta-sync", lastSync, lat, lng
                ).execute();

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    ProductListResponse body = response.body();
                    List<Product> products = body.getData();
                    int receivedCount = products != null ? products.size() : 0;
                    int insertCount = 0;

                    if (products != null && !products.isEmpty()) {
                        productDao.updateProducts(products);
                        insertCount = products.size();
                    }

                    // Determine authoritative new lastSync timestamp
                    String newSyncTime = null;
                    if (body.getTimestamp() != null && !body.getTimestamp().trim().isEmpty()) {
                        newSyncTime = body.getTimestamp().trim();
                    } else {
                        String dateHeader = response.headers().get("Date");
                        if (dateHeader != null && !dateHeader.trim().isEmpty()) {
                            try {
                                SimpleDateFormat httpFormat = new SimpleDateFormat(
                                        "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                                Date parsedDate = httpFormat.parse(dateHeader);
                                if (parsedDate != null) {
                                    SimpleDateFormat dbFormat = new SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss", Locale.US);
                                    newSyncTime = dbFormat.format(parsedDate);
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (newSyncTime == null || newSyncTime.trim().isEmpty()) {
                        newSyncTime = LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    }

                    // Update lastSync only after successful local persistence
                    syncDao.updateLastSyncTimestamp(new SyncMetadata(SYNC_TYPE_PRODUCTS, newSyncTime));
                    Log.d(TAG, String.format("syncProducts [SUCCESS]: records received=%d, updated/inserted=%d, old lastSync=%s, new lastSync=%s",
                            receivedCount, insertCount, lastSync, newSyncTime));

                    if (callback != null) callback.onSuccess();
                } else {
                    String error = response.body() != null ? response.body().getMessage() : "HTTP error " + response.code();
                    Log.e(TAG, "syncProducts [FAILED]: " + error);
                    if (callback != null) callback.onError(error);
                }
            } catch (Exception e) {
                Log.e(TAG, "syncProducts [EXCEPTION]:", e);
                if (callback != null) {
                    String friendlyError = NetworkUtil.getFriendlyNetError(null, e, true);
                    callback.onError(friendlyError);
                }
            }
        });
    }
}
