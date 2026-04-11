package com.js.salesman.ui.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.adapters.ProductSelectAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.adapters.SalesAdapter;
import com.js.salesman.api.client.ApiClient;
import com.js.salesman.api.service.ApiService;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Order;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.session.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SalesFragment extends Fragment {

    private SalesAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView customerSpinner, productSpinner;
    private TextInputEditText etDate;
    private Customer selectedCustomer;
    private Product selectedProduct;
    private String selectedDate = "";
    private int customerOffset = 0, productOffset = 0;
    private final int limit = 20;
    private boolean isCustomerLoading = false, isProductLoading = false;
    private boolean hasMoreCustomers = true, hasMoreProducts = true;
    private String currentCustomerQuery = "", currentProductQuery = "";
    private CustomerSelectAdapter customerAdapter;
    private ProductSelectAdapter productAdapter;
    private ProgressBar loadProgress;
    private Timer searchTimer;
    
    private ApiService apiService;
    private final Calendar calendar = Calendar.getInstance();

    public SalesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales, container, false);

        apiService = ApiClient.getClient(getActivity()).create(ApiService.class);
        
        // Initialize Views
        RecyclerView recyclerView = view.findViewById(R.id.salesRecyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        customerSpinner = view.findViewById(R.id.customerSpinner);
        productSpinner = view.findViewById(R.id.productSpinner);
        etDate = view.findViewById(R.id.etDate);
        MaterialButton btnApply = view.findViewById(R.id.btnApplyFilters);
        MaterialButton btnClear = view.findViewById(R.id.btnClearFilters);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SalesAdapter();
        recyclerView.setAdapter(adapter);

        // Date Picker
        etDate.setOnClickListener(v -> showDatePicker());

        // Listeners
        btnApply.setOnClickListener(v -> fetchSales());
        btnClear.setOnClickListener(v -> clearFilters());
        swipeRefresh.setOnRefreshListener(this::fetchSales);

        // Initial Data Loads
        fetchSales();

        customerSpinner.setOnClickListener(v -> showCustomerSelectionDialog());
        productSpinner.setOnClickListener(v -> showProductSelectionDialog());

        return view;
    }

    private void showCustomerSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_customer_select,
                (ViewGroup) requireView().getParent(), false);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.customerSelectRecycler);
        SearchView searchView = view.findViewById(R.id.customerSearchView);
        loadProgress = view.findViewById(R.id.customerLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        customerAdapter = new CustomerSelectAdapter(customer -> {
            selectedCustomer = customer;
            customerSpinner.setText(customer.getCustomerName());
            customerSpinner.setTextColor(ContextCompat.getColor(requireActivity(), R.color.black));
            dialog.dismiss();
        });
        recyclerView.setAdapter(customerAdapter);
        customerOffset = 0;
        hasMoreCustomers = true;
        currentCustomerQuery = "";
        loadCustomers(true);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null && !isCustomerLoading && hasMoreCustomers) {
                        int total = lm.getItemCount();
                        int last = lm.findLastVisibleItemPosition();
                        if (last >= total - 2) {
                            loadCustomers(false);
                        }
                    }
                }
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (searchTimer != null) searchTimer.cancel();
                currentCustomerQuery = query;
                loadCustomers(true);
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
                            getActivity().runOnUiThread(() -> {
                                currentCustomerQuery = newText;
                                loadCustomers(true);
                            });
                        }
                    }
                }, 600);
                return true;
            }
        });

        dialog.show();
    }

    private void showProductSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_product_select,
                (ViewGroup) requireView().getParent(), false);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.productSelectRecycler);
        SearchView searchView = view.findViewById(R.id.productSearchView);
        loadProgress = view.findViewById(R.id.productLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        productAdapter = new ProductSelectAdapter(product -> {
            selectedProduct = product;
            productSpinner.setText(product.getProductName());
            productSpinner.setTextColor(ContextCompat.getColor(requireActivity(), R.color.black));
            dialog.dismiss();
        });
        recyclerView.setAdapter(productAdapter);
        productOffset = 0;
        hasMoreProducts = true;
        currentProductQuery = "";
        loadProducts(true);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null && !isProductLoading && hasMoreProducts) {
                        int total = lm.getItemCount();
                        int last = lm.findLastVisibleItemPosition();
                        if (last >= total - 2) {
                            loadProducts(false);
                        }
                    }
                }
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (searchTimer != null) searchTimer.cancel();
                currentProductQuery = query;
                loadProducts(true);
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
                            getActivity().runOnUiThread(() -> {
                                currentProductQuery = newText;
                                loadProducts(true);
                            });
                        }
                    }
                }, 600);
                return true;
            }
        });

        dialog.show();
    }

    private void showDatePicker() {
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            selectedDate = sdf.format(calendar.getTime());
            etDate.setText(selectedDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadCustomers(boolean reset) {
        if (isCustomerLoading) return;
        if (!reset && !hasMoreCustomers) return;
        isCustomerLoading = true;
        if (loadProgress != null) loadProgress.setVisibility(View.VISIBLE);
        if (reset) {
            customerOffset = 0;
            hasMoreCustomers = true;
            if (customerAdapter != null) customerAdapter.clear();
        }
        if (currentCustomerQuery.isEmpty()) {
            apiService.syncCustomers("sync", "2010-01-01", limit, customerOffset)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                               @NonNull Response<ApiResponse<Customer>> response) {
                            handleCustomerResponse(response);
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                              @NonNull Throwable t) {
                            isCustomerLoading = false;
                            if (loadProgress != null) loadProgress.setVisibility(View.GONE);
                        }
                    });
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", currentCustomerQuery);
            payload.put("limit", limit);
            payload.put("offset", customerOffset);

            apiService.searchCustomers("search", payload)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                               @NonNull Response<ApiResponse<Customer>> response) {
                            handleCustomerResponse(response);
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                              @NonNull Throwable t) {
                            isCustomerLoading = false;
                            if (loadProgress != null) loadProgress.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void handleCustomerResponse(Response<ApiResponse<Customer>> response) {
        isCustomerLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        if (response.isSuccessful() && response.body() != null) {
            List<Customer> newCustomers = response.body().getData();
            if (newCustomers != null && !newCustomers.isEmpty()) {
                if (customerAdapter != null) {
                    customerAdapter.addCustomers(newCustomers);
                    customerOffset += newCustomers.size();
                    if (newCustomers.size() < limit) {
                        hasMoreCustomers = false;
                    }
                }
            } else {
                hasMoreCustomers = false;
            }
        } else {
            hasMoreCustomers = false;
        }
    }

    private void loadProducts(boolean reset) {
        if (isProductLoading) return;
        if (!reset && !hasMoreProducts) return;
        isProductLoading = true;
        if (loadProgress != null) loadProgress.setVisibility(View.VISIBLE);
        if (reset) {
            productOffset = 0;
            hasMoreProducts = true;
            if (productAdapter != null) productAdapter.clear();
        }
        if (currentProductQuery.isEmpty()) {
            apiService.syncProducts("sync", "2010-01-01", limit, productOffset)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ProductListResponse> call, @NonNull Response<ProductListResponse> response) {
                            handleProductResponse(response);
                        }

                        @Override
                        public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                            isProductLoading = false;
                            if (loadProgress != null) loadProgress.setVisibility(View.GONE);
                        }
                    });
        } else {
            apiService.searchProducts("search", currentProductQuery)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ProductListResponse> call, @NonNull Response<ProductListResponse> response) {
                            handleProductResponse(response);
                        }

                        @Override
                        public void onFailure(@NonNull Call<ProductListResponse> call, @NonNull Throwable t) {
                            isProductLoading = false;
                            if (loadProgress != null) loadProgress.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void handleProductResponse(Response<ProductListResponse> response) {
        isProductLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        if (response.isSuccessful() && response.body() != null) {
            List<Product> newProducts = response.body().getData();
            if (newProducts != null && !newProducts.isEmpty()) {
                if (productAdapter != null) {
                    productAdapter.addProducts(newProducts);
                    productOffset += newProducts.size();
                    if (newProducts.size() < limit) {
                        hasMoreProducts = false;
                    }
                }
            } else {
                hasMoreProducts = false;
            }
        } else {
            hasMoreProducts = false;
        }
    }

    private void fetchSales() {
        swipeRefresh.setRefreshing(true);
        String customerSrNo = selectedCustomer != null ? selectedCustomer.getSrNo() : null;
        String productCode = selectedProduct != null ? selectedProduct.getProductCode() : null;
        String salesman = new SessionManager(requireContext()).getUserId();
        
        apiService.filterOrders("filter", salesman, productCode, customerSrNo, selectedDate).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Order>> call, @NonNull Response<ApiResponse<Order>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setOrders(response.body().getData());
                    if (response.body().getData().isEmpty()) {
                        Toasty.info(requireContext(), "No sales found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toasty.error(requireContext(), "Failed to fetch sales", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Order>> call, @NonNull Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toasty.error(requireContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearFilters() {
        selectedCustomer = null;
        selectedProduct = null;
        selectedDate = "";
        customerSpinner.setText("");
        customerSpinner.setHint(R.string.customer);
        productSpinner.setText("");
        productSpinner.setHint(R.string.product);
        etDate.setText("");
        fetchSales();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchTimer != null) searchTimer.cancel();
    }
}
