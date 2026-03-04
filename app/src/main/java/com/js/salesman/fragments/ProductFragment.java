package com.js.salesman.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.appbar.MaterialToolbar;
import com.js.salesman.R;
import com.js.salesman.adapters.ProductAdapter;
import com.js.salesman.data.api.ApiService;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.network.ApiClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Callback;
import retrofit2.Call;
import retrofit2.Response;

public class ProductFragment extends Fragment {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProductAdapter adapter;
    private final List<Product> productList = new ArrayList<>();
    private ApiService apiService;
    private int offset = 0;
    private boolean isLoading = false;
    private boolean isSearching = false;
    private String currentQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Call<ProductListResponse> searchCall;
    private static final long SEARCH_DELAY = 400; // milliseconds

    public ProductFragment() {
        // Required empty public constructor
    }
   @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_product, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.productToolbar);
        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        assert searchView != null;
        searchView.setQueryHint("Search products...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchProducts(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (newText == null || newText.trim().isEmpty()) {
                        isSearching = false;
                        currentQuery = "";
                        loadProducts(true);
                    } else {
                        isSearching = true;
                        currentQuery = newText.trim();
                        searchProducts(currentQuery);
                    }
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
                return true;
            }
        });
        recyclerView = root.findViewById(R.id.productRecyclerView);
        swipeRefreshLayout = root.findViewById(R.id.productSwipeRefresh);
        adapter = new ProductAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        apiService = ApiClient.getClient(getActivity()).create(ApiService.class);
        setupPagination();
        setupRefresh();
        loadProducts(false);
      // Inflate the layout for this fragment
        return root;
    }
    private void loadProducts(boolean reset) {
        if (isLoading) return;
        isLoading = true;
        if (reset) {
            offset = 0;
            adapter.clearProducts();
        }
        String lastSync = "2025-01-01 00:00:00";
        int limit = 20;
        Call<ProductListResponse> call =
                apiService.syncProducts("sync", lastSync, limit, offset);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> call,
                                   @NonNull Response<ProductListResponse> response) {
                swipeRefreshLayout.setRefreshing(false);
                isLoading = false;
                if (response.isSuccessful() && response.body() != null &&
                        response.body().isSuccess()) {
                    List<Product> products = response.body().getData();

                    if (products != null && !products.isEmpty()) {
                        adapter.addProducts(products);
                        offset += products.size();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                isLoading = false;
                Log.d("ProductFragment", "onFailure: " + t);
            }
        });
    }
    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> loadProducts(true));
    }
    private void setupPagination() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                int totalItemCount = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (!isLoading && totalItemCount <= (lastVisible + 5)) {
                    if (!isSearching) {
                        loadProducts(false);   // normal pagination
                    }
                    // No pagination during search (unless backend supports it)
                }
            }
        });
    }

    private void searchProducts(String query) {
        // Cancel previous call if still running
        if (searchCall != null && !searchCall.isCanceled()) {
            searchCall.cancel();
        }
        isLoading = true;
        isSearching = true;
        searchCall = apiService.searchProducts("search", query);
        searchCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> call,
                                   @NonNull Response<ProductListResponse> response) {
                isLoading = false;
                isSearching = false;
                if (!call.isCanceled() &&
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().isSuccess()) {
                    adapter.clearProducts();
                    adapter.addProducts(response.body().getData());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                isLoading = false;
                isSearching = false;
                if (call.isCanceled()) return;
                Log.d("SEARCH", "Error: " + t.getMessage());
            }
        });
    }
    private void filterProducts(String query) {
        if (query == null || query.isEmpty()) {
            adapter.clearProducts();
            adapter.addProducts(productList);
            return;
        }
        List<Product> filtered = new ArrayList<>();
        for (Product product : productList) {
            if (product.getProductName()
                    .toLowerCase()
                    .contains(query.toLowerCase())) {
                filtered.add(product);
            }
        }
        adapter.clearProducts();
        adapter.addProducts(filtered);
    }
}