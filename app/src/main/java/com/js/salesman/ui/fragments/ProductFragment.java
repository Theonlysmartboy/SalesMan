package com.js.salesman.ui.fragments;

import android.annotation.SuppressLint;
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
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.utils.TrailingDotsLoader;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.models.Customer;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.OrderHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import es.dmoral.toasty.Toasty;
import retrofit2.Callback;
import retrofit2.Call;
import retrofit2.Response;

public class ProductFragment extends Fragment {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProductAdapter adapter;
    private ApiInterface apiInterface;
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
        //tvSelectedCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
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
        int closeButtonId = androidx.appcompat.R.id.search_close_btn;
        assert searchView != null;
        View closeButton = searchView.findViewById(closeButtonId);
        if (closeButton instanceof android.widget.ImageView) {
            ((android.widget.ImageView) closeButton).setColorFilter(
                    requireContext().getColor(R.color.honeydew)
            );
        }
        int searchIconId = androidx.appcompat.R.id.search_button;
        View searchIcon = searchView.findViewById(searchIconId);
        if (searchIcon instanceof android.widget.ImageView) {
            ((android.widget.ImageView) searchIcon).setColorFilter(
                    requireContext().getColor(R.color.honeydew)
            );
        }
        int collapseIconId = androidx.appcompat.R.id.search_close_btn;
        View collapseIcon = searchView.findViewById(collapseIconId);
        if (collapseIcon instanceof android.widget.ImageView) {
            ((android.widget.ImageView) collapseIcon).setColorFilter(
                    requireContext().getColor(R.color.honeydew)
            );
        }
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
        apiInterface = ApiClient.getClient(requireActivity()).create(ApiInterface.class);
        setupPagination();
        setupRefresh();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (activeCustomer == null) {
            // Bypass customer selection – create a dummy customer
            activeCustomer = new Customer("0", "0",
                    "Select Customer", "WALKIN", 0,
                    0,0);
            sessionManager.setSelectedCustomer(activeCustomer);
            updateCustomerUI();
        }
        loadProducts(true);
    }
    private void updateCustomerUI() {
        if (activeCustomer != null) {
            tvSelectedCustomer.setText("Customer: " + activeCustomer.getCustomerName());
            // Removed the click listener so it doesn't open dialog
            tvSelectedCustomer.setOnClickListener(null);
        } else {
            tvSelectedCustomer.setText(R.string.customer_walk_in);
            tvSelectedCustomer.setOnClickListener(null);
        }
    }

    @SuppressLint("InflateParams")
    private void showCustomerSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.layout_customer_select, null);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.customerSelectRecycler);
        SearchView searchView = view.findViewById(R.id.customerSearchView);
        customerLoadProgress = view.findViewById(R.id.customerLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        CustomerSelectAdapter customerAdapter = new CustomerSelectAdapter(customer -> {
            if (activeCustomer != null && !activeCustomer.getCustomerCode()
                    .equals(customer.getCustomerCode())) {
                try (Db db = new Db(requireContext())) {
                    if (db.getCartCount() > 0) {
                        showChangeCustomerDialog(customer, dialog);
                        return;
                    }
                }
            }
            selectCustomer(customer);
            dialog.dismiss();
        });
        recyclerView.setAdapter(customerAdapter);
        loadCustomers(customerAdapter, "");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                loadCustomers(customerAdapter, query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                if (searchTimer != null) searchTimer.cancel();
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> loadCustomers(customerAdapter,
                                    newText));
                        }
                    }
                }, 600);
                return true;
            }
        });
        dialog.show();
    }

    private void loadCustomers(CustomerSelectAdapter adapter, String query) {
        if (customerLoadProgress != null) customerLoadProgress.setVisibility(View.VISIBLE);
        if (query.isEmpty()) {
            apiInterface.syncCustomers("sync", "2010-01-01", 50,
                    0).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                       @NonNull Response<ApiResponse<Customer>> response) {
                    if (customerLoadProgress != null) customerLoadProgress.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        adapter.setCustomers(response.body().getData());
                    }
                }
                @Override
                public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                      @NonNull Throwable t) {
                    if (customerLoadProgress != null) customerLoadProgress.setVisibility(View.GONE);
                }
            });
        } else {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("query", query);
            apiInterface.searchCustomers("search", payload).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                       @NonNull Response<ApiResponse<Customer>> response) {
                    if (customerLoadProgress != null) customerLoadProgress.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        adapter.setCustomers(response.body().getData());
                    }
                }
                @Override
                public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                      @NonNull Throwable t) {
                    if (customerLoadProgress != null) customerLoadProgress.setVisibility(View.GONE);
                }
            });
        }
    }

    private void showChangeCustomerDialog(Customer newCustomer, BottomSheetDialog selectionDialog) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Change Customer?")
                .setMessage("You have items in your cart. What would you like to do?")
                .setPositiveButton("Park & Change", (d, w) -> {
                    try (Db db = new Db(requireContext())) {
                        db.moveEntireCartToParkedCart(activeCustomer);
                    }
                    selectCustomer(newCustomer);
                    selectionDialog.dismiss();
                    requireActivity().invalidateOptionsMenu();
                })
                .setNegativeButton("Clear & Change", (d, w) -> {
                    try (Db db = new Db(requireContext())) {
                        db.clearCart();
                    }
                    selectCustomer(newCustomer);
                    selectionDialog.dismiss();
                    requireActivity().invalidateOptionsMenu();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void selectCustomer(Customer customer) {
        activeCustomer = customer;
        sessionManager.setSelectedCustomer(customer);
        updateCustomerUI();
        loadProducts(true);
    }

    // LOAD PRODUCTS (PAGINATION FIXED)
    /*private void loadProducts(boolean reset) {
        if (isLoading) return;
        if (!hasMoreData && !reset) return;
        LocationUtils.getUserLocation(requireContext(), requireActivity(),
                new LocationUtils.LocationResultCallback() {
            @Override
            public void onSuccess(double lat, double lng) {
                sessionManager.saveLastLocation(lat, lng);
                executeLoadProducts(reset, lat, lng);
            }
            @Override
            public void onFailure(String error) {
                Double cachedLat = sessionManager.getCachedLat();
                Double cachedLng = sessionManager.getCachedLng();
                if (cachedLat != null && cachedLng != null) {
                    executeLoadProducts(reset, cachedLat, cachedLng);
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    Toasty.error(requireContext(),
                    "GPS is required for accurate pricing. Please enable location services.",
                    Toasty.LENGTH_LONG).show();
                }
            }
        });
    }*/

    private void loadProducts(boolean reset) {
        if (isLoading) return;
        if (!hasMoreData && !reset) return;
        if (reset && !swipeRefreshLayout.isRefreshing()) {
            showLoader();   // Show full‑screen loader for initial load / refresh
        }
        // Bypass location – use 0,0 or any default
        sessionManager.saveLastLocation(0.0, 0.0);
        executeLoadProducts(reset, 0.0, 0.0);
    }

    private void executeLoadProducts(boolean reset, double lat, double lng) {
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
        apiInterface.syncProducts("sync", lastSync, limit, offset, lat,
                lng).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductListResponse> call,
                                        @NonNull Response<ProductListResponse> response) {
                        hideLoader();
                        swipeRefreshLayout.setRefreshing(false);
                        isLoading = false;
                        if (response.isSuccessful()
                                && response.body() != null) {
                            if (response.body().isSuccess()) {
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
                            } else {
                                Log.d("TAG", response.body().getMessage());
                                Toasty.error(requireContext(), response.body().getMessage(),
                                        Toasty.LENGTH_LONG).show();
                            }
                        }else {
                            Log.d("TAG", "No Products Found");
                            Toasty.warning(requireActivity(), "No Products Found",
                                    Toasty.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductListResponse> call,
                                        @NonNull Throwable t) {
                        hideLoader();
                        swipeRefreshLayout.setRefreshing(false);
                        isLoading = false;
                        if (t instanceof UnknownHostException
                                || t instanceof SocketTimeoutException) {
                            Log.d("TAG",
                                    "Unable to reach server. Check your internet connection.");
                            Toasty.error(requireContext(),
                            "Unable to reach server. Check your internet connection.",
                                    Toasty.LENGTH_LONG).show();
                        } else {
                            Toasty.error(requireActivity(), "Error loading products",
                                    Toasty.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // PAGINATION LISTENER
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

    // PULL TO REFRESH
    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> loadProducts(true));
    }

    // SEARCH
    /*private void searchProducts(String query) {
        if (searchCall != null && !searchCall.isCanceled()) {
            searchCall.cancel();
        }
        LocationUtils.getUserLocation(requireContext(), requireActivity(),
        new LocationUtils.LocationResultCallback() {
            @Override
            public void onSuccess(double lat, double lng) {
                sessionManager.saveLastLocation(lat, lng);
                executeSearchProducts(query, lat, lng);
            }
            @Override
            public void onFailure(String error) {
                Double cachedLat = sessionManager.getCachedLat();
                Double cachedLng = sessionManager.getCachedLng();
                if (cachedLat != null && cachedLng != null) {
                    executeSearchProducts(query, cachedLat, cachedLng);
                } else {
                    Toasty.error(requireContext(),
                            "GPS is required for accurate pricing. Please enable location services.",
                            Toasty.LENGTH_LONG).show();
                }
            }
        });
    }*/

    private void searchProducts(String query) {
        if (searchCall != null && !searchCall.isCanceled()) {
            searchCall.cancel();
        }
        showLoader();
        // Bypass location
        sessionManager.saveLastLocation(0.0, 0.0);
        executeSearchProducts(query, 0.0, 0.0);
    }

    private void executeSearchProducts(String query, double lat, double lng) {
        isLoading = true;
        searchCall = apiInterface.searchProducts("search", query, lat, lng);
        searchCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductListResponse> call,
                                   @NonNull Response<ProductListResponse> response) {
                isLoading = false;
                hideLoader();
                if (!call.isCanceled() && response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        adapter.clearProducts();
                        adapter.addProducts(response.body().getData());
                    } else {
                        Log.d("TAG", response.body().getMessage());
                        Toasty.error(requireContext(), response.body().getMessage(),
                                Toasty.LENGTH_LONG).show();
                    }
                }else{
                    Log.d("TAG", "No Product Found matching the search term");
                    Toasty.warning(requireActivity(),
                            "No Product Found matching the search term",
                            Toasty.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                isLoading = false;
                hideLoader();
                if (call.isCanceled()) return;
                if (t instanceof UnknownHostException || t instanceof SocketTimeoutException) {
                    Log.d("TAG", "Unable to reach server. Check your internet connection.");
                    Toasty.error(requireContext(),
                            "Unable to reach server. Check your internet connection.",
                            Toasty.LENGTH_LONG).show();
                } else {
                    Log.d("TAG", "Error searching products " + t.getMessage());
                    Toasty.error(requireActivity(),
                            "Error searching products", Toasty.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showLoader() {
        if (progressLoader != null) {
            progressLoader.setVisibility(View.VISIBLE);
            // Optionally bring to front
            progressLoader.bringToFront();
        }
    }

    private void hideLoader() {
        if (progressLoader != null) {
            progressLoader.setVisibility(View.GONE);
        }
    }
}
