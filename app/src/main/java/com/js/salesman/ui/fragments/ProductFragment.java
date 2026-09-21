package com.js.salesman.ui.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.appbar.MaterialToolbar;
import com.js.salesman.R;
import com.js.salesman.adapters.ProductAdapter;
import com.js.salesman.models.Product;
import com.js.salesman.utils.TrailingDotsLoader;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.models.Customer;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.OrderHelper;
import com.js.salesman.viewmodels.ProductViewModel;

import es.dmoral.toasty.Toasty;

public class ProductFragment extends Fragment {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProductAdapter adapter;
    private ProductViewModel viewModel;
    private String currentQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final long SEARCH_DELAY = 200;
    private SessionManager sessionManager;
    private TextView tvSelectedCustomer;
    private Customer activeCustomer;
    private ProgressBar customerLoadProgress;
    private Timer searchTimer;
    private TrailingDotsLoader progressLoader;

    public ProductFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_product, container, false);
        sessionManager = new SessionManager(requireContext());
        activeCustomer = sessionManager.getSelectedCustomer();
        tvSelectedCustomer = root.findViewById(R.id.tvSelectedCustomer);
        progressLoader = root.findViewById(R.id.progressLoader);
        updateCustomerUI();
        
        MaterialToolbar toolbar = root.findViewById(R.id.productToolbar);
        toolbar.post(() -> {
            for (int i = 0; i < toolbar.getMenu().size(); i++) {
                MenuItem item = toolbar.getMenu().getItem(i);
                if (item.getIcon() != null) {
                    item.getIcon().setTint(
                            requireContext().getColor(R.color.honeydew)
                    );
                }
            }
        });

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint("Search products...");
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    viewModel.setSearchQuery(query);
                    return true;
                }
                @Override
                public boolean onQueryTextChange(String newText) {
                    if (searchRunnable != null) {
                        searchHandler.removeCallbacks(searchRunnable);
                    }
                    searchRunnable = () -> {
                        currentQuery = (newText == null) ? "" : newText.trim();
                        viewModel.setSearchQuery(currentQuery);
                    };
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
                    return true;
                }
            });
        }

        recyclerView = root.findViewById(R.id.productRecyclerView);
        swipeRefreshLayout = root.findViewById(R.id.productSwipeRefresh);
        adapter = new ProductAdapter(new Product.OnProductClickListener() {
            @Override
            public void onProductClick(String productCode) {
                Bundle bundle = new Bundle();
                bundle.putString("action", "get");
                bundle.putString("code", productCode);
                ProductDescriptionFragment fragment = new ProductDescriptionFragment();
                fragment.setArguments(bundle);
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
            }
            @Override
            public void onAddToOrderClick(Product product) {
                OrderHelper.addItemToOrder(ProductFragment.this, product);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        setupViewModel();
        setupRefresh();
        
        return root;
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        
        viewModel.getProducts().observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                adapter.setProducts(products);
            }
        });

        viewModel.getIsSyncing().observe(getViewLifecycleOwner(), isSyncing -> {
            if (Boolean.TRUE.equals(isSyncing)) {
                if (!swipeRefreshLayout.isRefreshing()) {
                    showLoader();
                }
            } else {
                hideLoader();
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        viewModel.getSyncError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toasty.error(requireContext(), error, Toasty.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (activeCustomer == null) {
            activeCustomer = new Customer("0", "0",
                    "Select Customer", "WALKIN", 0,
                    0,0);
            sessionManager.setSelectedCustomer(activeCustomer);
            updateCustomerUI();
        }
        
        // Initial sync if products are empty or just to keep data fresh
        viewModel.refreshProducts();
    }

    private void updateCustomerUI() {
        if (activeCustomer != null) {
            tvSelectedCustomer.setText("Customer: " + activeCustomer.getCustomerName());
            tvSelectedCustomer.setOnClickListener(null);
        } else {
            tvSelectedCustomer.setText(R.string.customer_walk_in);
            tvSelectedCustomer.setOnClickListener(null);
        }
    }

    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshProducts());
    }

    private void showLoader() {
        if (progressLoader != null) {
            progressLoader.setVisibility(View.VISIBLE);
            progressLoader.bringToFront();
        }
    }

    private void hideLoader() {
        if (progressLoader != null) {
            progressLoader.setVisibility(View.GONE);
        }
    }
}
