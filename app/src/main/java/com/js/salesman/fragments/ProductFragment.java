package com.js.salesman.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
    private List<Product> productList = new ArrayList<>();
    private ApiService apiService;
    private int offset = 0;
    private final int limit = 20;
    private boolean isLoading = false;
    private String lastSync = "2025-01-01 00:00:00";

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
        recyclerView = root.findViewById(R.id.productRecyclerView);
        swipeRefreshLayout = root.findViewById(R.id.productSwipeRefresh);
        adapter = new ProductAdapter(productList);
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
        Call<ProductListResponse> call =
                apiService.syncProducts("sync", lastSync, limit, offset);
        call.enqueue(new Callback<ProductListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> call,
                                @NonNull Response<ProductListResponse> response) {
                swipeRefreshLayout.setRefreshing(false);
                isLoading = false;
                if (response.isSuccessful() && response.body() != null &&
                        response.body().isSuccess()) {
                    List<Product> products = response.body().getData();
                    adapter.addProducts(products);
                    offset += limit;
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
                if (layoutManager != null &&
                        !isLoading &&
                        layoutManager.findLastVisibleItemPosition()
                                >= productList.size() - 5) {
                    loadProducts(false);
                }
            }
        });
    }
    private void searchProducts(String query) {

        Call<ProductListResponse> call =
                apiService.searchProducts("search", query);

        call.enqueue(new Callback<ProductListResponse>() {
            @Override
            public void onResponse(Call<ProductListResponse> call,
                                   Response<ProductListResponse> response) {

                if (response.isSuccessful() &&
                        response.body() != null &&
                        response.body().isSuccess()) {

                    adapter.clearProducts();
                    adapter.addProducts(response.body().getData());
                }
            }

            @Override
            public void onFailure(Call<ProductListResponse> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }
}