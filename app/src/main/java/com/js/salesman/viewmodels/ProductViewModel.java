package com.js.salesman.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.js.salesman.models.Product;
import com.js.salesman.repository.ProductRepository;

import java.util.List;

public class ProductViewModel extends AndroidViewModel {
    private final ProductRepository repository;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final LiveData<List<Product>> products;
    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncError = new MutableLiveData<>(null);

    public ProductViewModel(@NonNull Application application) {
        super(application);
        repository = new ProductRepository(application);
        
        products = Transformations.switchMap(searchQuery, query -> {
            if (query == null || query.isEmpty()) {
                return repository.getProducts();
            } else {
                return repository.searchProducts(query);
            }
        });
    }

    public LiveData<List<Product>> getProducts() {
        return products;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public LiveData<Product> getProductByCode(String code) {
        return repository.getProductByCode(code);
    }

    public LiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }

    public LiveData<String> getSyncError() {
        return syncError;
    }

    public void refreshProducts() {
        isSyncing.setValue(true);
        syncError.setValue(null);
        repository.syncProducts(new ProductRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                isSyncing.postValue(false);
            }

            @Override
            public void onError(String message) {
                isSyncing.postValue(false);
                syncError.postValue(message);
            }
        });
    }
}
