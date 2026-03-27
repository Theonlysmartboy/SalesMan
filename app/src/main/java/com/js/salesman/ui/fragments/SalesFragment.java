package com.js.salesman.ui.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SalesFragment extends Fragment {

    private RecyclerView recyclerView;
    private SalesAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private AutoCompleteTextView customerSpinner, productSpinner;
    private TextInputEditText etDate;
    private MaterialButton btnApply, btnClear;
    
    private List<Customer> customerList = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private Customer selectedCustomer;
    private Product selectedProduct;
    private String selectedDate = "";
    
    private ApiService apiService;
    private final Calendar calendar = Calendar.getInstance();

    public SalesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales, container, false);

        apiService = ApiClient.getClient(getActivity()).create(ApiService.class);
        
        // Initialize Views
        recyclerView = view.findViewById(R.id.salesRecyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        customerSpinner = view.findViewById(R.id.customerSpinner);
        productSpinner = view.findViewById(R.id.productSpinner);
        etDate = view.findViewById(R.id.etDate);
        btnApply = view.findViewById(R.id.btnApplyFilters);
        btnClear = view.findViewById(R.id.btnClearFilters);

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
        loadCustomers();
        loadProducts();
        fetchSales();

        return view;
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

    private void loadCustomers() {
        // Using existing sync API to populate filter
        apiService.syncCustomers("sync", "2010-01-01", 100, 0).enqueue(new Callback<ApiResponse<Customer>>() {
            @Override
            public void onResponse(Call<ApiResponse<Customer>> call, Response<ApiResponse<Customer>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    customerList = response.body().getData();
                    ArrayAdapter<Customer> adapter = new ArrayAdapter<>(requireContext(), 
                            android.R.layout.simple_dropdown_item_1line, customerList);
                    customerSpinner.setAdapter(adapter);
                    customerSpinner.setOnItemClickListener((parent, view, position, id) -> 
                            selectedCustomer = customerList.get(position));
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Customer>> call, Throwable t) {}
        });
    }

    private void loadProducts() {
        apiService.syncProducts("sync", "2010-01-01", 100, 0).enqueue(new Callback<ProductListResponse>() {
            @Override
            public void onResponse(Call<ProductListResponse> call, Response<ProductListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    productList = response.body().getData();
                    ArrayAdapter<Product> adapter = new ArrayAdapter<>(requireContext(), 
                            android.R.layout.simple_dropdown_item_1line, productList);
                    // Note: Product model needs a toString() or custom adapter for meaningful display
                    productSpinner.setAdapter(adapter);
                    productSpinner.setOnItemClickListener((parent, view, position, id) -> 
                            selectedProduct = productList.get(position));
                }
            }
            @Override
            public void onFailure(Call<ProductListResponse> call, Throwable t) {}
        });
    }

    private void fetchSales() {
        swipeRefresh.setRefreshing(true);
        
        String custSrNo = selectedCustomer != null ? selectedCustomer.getSrNo() : null;
        
        apiService.filterOrders("filter", custSrNo, selectedDate).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
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
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
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
        productSpinner.setText("");
        etDate.setText("");
        fetchSales();
    }
}
