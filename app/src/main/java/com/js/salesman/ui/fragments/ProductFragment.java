package com.js.salesman.ui.fragments;

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
import com.js.salesman.api.service.ApiService;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.api.client.ApiClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import es.dmoral.toasty.Toasty;
import retrofit2.Callback;
import retrofit2.Call;
import retrofit2.Response;

public class ProductFragment extends Fragment {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProductAdapter adapter;
    private ApiService apiService;
    private int offset = 0;
    private final int limit = 50;
    private boolean isLoading = false;
    private boolean isSearching = false;
    private boolean hasMoreData = true;   // needed for pagination stop
    private String currentQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Call<ProductListResponse> searchCall;
    private static final long SEARCH_DELAY = 200;

    public ProductFragment() {}

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
        adapter = new ProductAdapter(productCode -> {
            // Open productDescription fragment
            Bundle bundle = new Bundle();
            bundle.putString("action", "get");
            bundle.putString("code", productCode);
            ProductDescriptionFragment fragment = new ProductDescriptionFragment();
            fragment.setArguments(bundle);
            // Replace current fragment
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        apiService = ApiClient.getClient(getActivity()).create(ApiService.class);
        setupPagination();
        setupRefresh();
        loadProducts(true); // first load
        return root;
    }

    // ==============================
    // LOAD PRODUCTS (PAGINATION FIXED)
    // ==============================
    private void loadProducts(boolean reset) {
        if (isLoading) return;
        if (!hasMoreData && !reset) return;
        isLoading = true;
        if (reset) {
            offset = 0;
            hasMoreData = true;
            adapter.clearProducts();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twelveMonthsAgo = now.minusMonths(12);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String lastSync = twelveMonthsAgo.format(formatter);
        apiService.syncProducts("sync", lastSync, limit, offset).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductListResponse> call,
                                        @NonNull Response<ProductListResponse> response) {
                        swipeRefreshLayout.setRefreshing(false);
                        isLoading = false;
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().isSuccess()) {
                            List<Product> products = response.body().getData();
                            if (products != null && !products.isEmpty()) {
                                adapter.addProducts(products);
                                offset += products.size();
                                // Stop pagination if fewer than limit returned
                                if (products.size() < limit) {
                                    hasMoreData = false;
                                }
                            } else {
                                hasMoreData = false;
                            }
                        }else {
                            Log.d("DEBUG_RESPONSE", "Response not successful: " + response.code());
                            Toasty.warning(requireActivity(), "No Products Found", Toasty.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ProductListResponse> call,
                                        @NonNull Throwable t) {
                        swipeRefreshLayout.setRefreshing(false);
                        isLoading = false;
                        Log.d("ProductFragment", "Load Error: " + t.getMessage());
                        Toasty.error(requireActivity(), "Error loading products", Toasty.LENGTH_SHORT).show();
                    }
                });
    }

    // ==============================
    // PAGINATION LISTENER (FIXED)
    // ==============================
    private void setupPagination() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                int totalItemCount = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (!isLoading
                        && hasMoreData
                        && !isSearching
                        && lastVisible >= totalItemCount - 3) {
                    loadProducts(false);
                }
            }
        });
    }

    // ==============================
    // PULL TO REFRESH
    // ==============================
    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> loadProducts(true));
    }

    // ==============================
    // SEARCH (NO PAGINATION HERE)
    // ==============================
    private void searchProducts(String query) {
        if (searchCall != null && !searchCall.isCanceled()) {
            searchCall.cancel();
        }
        isLoading = true;
        searchCall = apiService.searchProducts("search", query);
        searchCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> call,
                                   @NonNull Response<ProductListResponse> response) {
                isLoading = false;
                if (!call.isCanceled()
                        && response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess()) {
                    adapter.clearProducts();
                    adapter.addProducts(response.body().getData());
                }else{
                    Log.d("DEBUG_RESPONSE", "Response not successful: " + response.code());
                    Toasty.warning(requireActivity(), "No Product Found matching the search term", Toasty.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ProductListResponse> call,
                                  @NonNull Throwable t) {
                isLoading = false;
                if (call.isCanceled()) return;
                Log.d("SEARCH", "Error: " + t.getMessage());
                Toasty.error(requireActivity(), "Error searching products", Toasty.LENGTH_SHORT).show();
            }
        });
    }
}