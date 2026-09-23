package com.js.salesman.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.js.salesman.models.Product;
import com.js.salesman.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

public class ProductViewModel extends AndroidViewModel {
    public static final int PAGE_SIZE = 20;
    private final ProductRepository repository;

    private final MutableLiveData<List<Product>> pagedProducts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasNextPage = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<String> syncError = new MutableLiveData<>(null);

    private String currentQuery = "";
    private int currentOffset = 0;

    public ProductViewModel(@NonNull Application application) {
        super(application);
        repository = new ProductRepository(application);
    }

    public LiveData<List<Product>> getPagedProducts() {
        return pagedProducts;
    }

    public LiveData<List<Product>> getProducts() {
        return pagedProducts;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }

    public LiveData<Boolean> getHasNextPage() {
        return hasNextPage;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getSyncError() {
        return syncError;
    }

    public void setSearchQuery(String query) {
        String cleanQuery = (query == null) ? "" : query.trim();
        if (!cleanQuery.equals(currentQuery)) {
            loadFirstPage(cleanQuery);
        }
    }

    public void loadFirstPage(String query) {
        currentQuery = (query == null) ? "" : query.trim();
        currentOffset = 0;
        // postValue is safe from any thread (this can be called from repository callbacks)
        hasNextPage.postValue(true);
        isLoading.postValue(true);
        error.postValue(null);
        repository.getProductsPaged(PAGE_SIZE, currentOffset, currentQuery,
                new ProductRepository.GetProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> products, boolean hasNext) {
                        isLoading.postValue(false);
                        hasNextPage.postValue(hasNext);
                        pagedProducts.postValue(new ArrayList<>(products));
                        currentOffset = products.size();
                    }
                    @Override
                    public void onError(String message) {
                        isLoading.postValue(false);
                        error.postValue(message);
                    }
                });
    }

    public void loadNextPage() {
        if (Boolean.TRUE.equals(isLoading.getValue())
                || Boolean.FALSE.equals(hasNextPage.getValue())) {
            return;
        }
        // postValue for safety
        isLoading.postValue(true);
        repository.getProductsPaged(PAGE_SIZE, currentOffset, currentQuery,
                new ProductRepository.GetProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> newProducts, boolean hasNext) {
                        isLoading.postValue(false);
                        hasNextPage.postValue(hasNext);
                        List<Product> currentList = pagedProducts.getValue();
                        if (currentList == null) currentList = new ArrayList<>();
                        List<Product> updatedList = new ArrayList<>(currentList);
                        // Prevent duplicates based on product code
                        for (Product newProduct : newProducts) {
                            boolean exists = false;
                            for (Product existing : updatedList) {
                                if (existing.getProductCode()
                                        .equalsIgnoreCase(newProduct.getProductCode())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                updatedList.add(newProduct);
                            }
                        }
                        currentOffset += newProducts.size();
                        pagedProducts.postValue(updatedList);
                    }
                    @Override
                    public void onError(String message) {
                        isLoading.postValue(false);
                        error.postValue(message);
                    }
                });
    }

    public LiveData<Product> getProductByCode(String code) {
        return repository.getProductByCode(code);
    }

    public void getProductDetails(String code, ProductRepository.GetProductDetailsCallback callback) {
        repository.getProductDetails(code, callback);
    }

    public void refreshProducts() {
        isSyncing.postValue(true);
        syncError.postValue(null);
        repository.syncProducts(new ProductRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                isSyncing.postValue(false);
                // Reload first page to reflect updated cache
                loadFirstPage(currentQuery);
            }
            @Override
            public void onError(String message) {
                isSyncing.postValue(false);
                syncError.postValue(message);
                // Reload first page even if sync fails
                loadFirstPage(currentQuery);
            }
        });
    }
}