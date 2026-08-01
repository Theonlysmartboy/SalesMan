package com.js.salesman.ui.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.adapters.ProductSelectAdapter;
import com.js.salesman.adapters.ReportAdapter;
import com.js.salesman.adapters.SalesAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Order;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.models.ReportEntry;
import com.js.salesman.utils.LocationUtils;
import com.js.salesman.utils.managers.SessionManager;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportsFragment extends Fragment {

    private BarChart barChart;
    private ReportAdapter adapter;
    private ProgressBar progressBar;
    private TextInputEditText etMonth;
    private AutoCompleteTextView spinnerCustomer, spinnerProduct;
    private final List<ReportEntry> currentData = new ArrayList<>();
    private final List<Customer> customerList = new ArrayList<>();
    private final List<Product> productList = new ArrayList<>();
    private Customer selectedCustomer = null;
    private Product selectedProduct = null;
    private SessionManager session;

    // --- Merged SalesFragment Fields ---
    private SalesAdapter salesAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView orderCustomerSpinner, orderProductSpinner;
    private TextInputEditText etOrderDate;
    private Customer orderSelectedCustomer;
    private Product orderSelectedProduct;
    private String selectedOrderDate = "";
    private int customerOffset = 0, productOffset = 0;
    private final int limit = 20;
    private boolean isCustomerLoading = false, isProductLoading = false;
    private boolean hasMoreCustomers = true, hasMoreProducts = true;
    private String currentCustomerQuery = "", currentProductQuery = "";
    private CustomerSelectAdapter customerAdapter;
    private ProductSelectAdapter productAdapter;
    private ProgressBar loadProgress;
    private Timer searchTimer;
    private ApiInterface apiInterface;
    private final Calendar calendar = Calendar.getInstance();
    private LinearLayout amountContainer, ordersContainer;
    private static final String ARG_INITIAL_TAB = "initial_tab";

    public static ReportsFragment newInstance(String initialTab) {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TAB, initialTab);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);
        session = new SessionManager(requireContext());
        apiInterface = ApiClient.getClient(requireActivity()).create(ApiInterface.class);

        initViews(view);
        setupChart();
        setupFilters();
        loadReports();

        // Initial Tab Handling
        if (getArguments() != null) {
            String initialTab = getArguments().getString(ARG_INITIAL_TAB);
            if ("orders".equals(initialTab)) {
                MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroup);
                toggleGroup.check(R.id.btnOrders);
            }
        }

        return view;
    }

    private void initViews(View view) {
        amountContainer = view.findViewById(R.id.amountContainer);
        ordersContainer = view.findViewById(R.id.ordersContainer);

        barChart = view.findViewById(R.id.barChart);
        ListView listView = view.findViewById(R.id.listViewReports);
        progressBar = view.findViewById(R.id.progressBar);
        etMonth = view.findViewById(R.id.etFilterMonth);
        spinnerCustomer = view.findViewById(R.id.spinnerCustomer);
        spinnerProduct = view.findViewById(R.id.spinnerProduct);
        adapter = new ReportAdapter(requireContext(), currentData);
        listView.setAdapter(adapter);
        etMonth.setOnClickListener(v -> showMonthPicker());
        spinnerCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        spinnerProduct.setOnClickListener(v -> showProductSelectionDialog());

        // --- Merged SalesFragment View Init ---
        RecyclerView recyclerView = view.findViewById(R.id.salesRecyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        orderCustomerSpinner = view.findViewById(R.id.orderCustomerSpinner);
        orderProductSpinner = view.findViewById(R.id.orderProductSpinner);
        etOrderDate = view.findViewById(R.id.etOrderDate);
        MaterialButton btnApply = view.findViewById(R.id.btnOrderApplyFilters);
        MaterialButton btnClear = view.findViewById(R.id.btnOrderClearFilters);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        salesAdapter = new SalesAdapter();
        salesAdapter.setOnItemClickListener(order -> {
            OrderDescriptionFragment fragment = OrderDescriptionFragment.newInstance(order.getOrderNo());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(salesAdapter);

        etOrderDate.setOnClickListener(v -> showOrderDatePicker());
        btnApply.setOnClickListener(v -> fetchSales());
        btnClear.setOnClickListener(v -> clearOrderFilters());
        swipeRefresh.setOnRefreshListener(this::fetchSales);
        orderCustomerSpinner.setOnClickListener(v -> showOrderCustomerSelectionDialog());
        orderProductSpinner.setOnClickListener(v -> showOrderProductSelectionDialog());

        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroup);
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnAmount) {
                    amountContainer.setVisibility(View.VISIBLE);
                    ordersContainer.setVisibility(View.GONE);
                    updateUI();
                } else if (checkedId == R.id.btnOrders) {
                    amountContainer.setVisibility(View.GONE);
                    ordersContainer.setVisibility(View.VISIBLE);
                    fetchSales();
                }
            }
        });
    }

    private void setupChart() {
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.getDescription().setEnabled(false);
        barChart.setMaxVisibleValueCount(60);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(7);
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(true);
    }

    private void setupFilters() {
        java.text.SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM",
                Locale.getDefault());
        etMonth.setText(sdf.format(new Date()));
    }

    private void showCustomerSelectionDialog() {
        String[] displayList = new String[customerList.size() + 1];
        displayList[0] = "All Customers";
        for (int i = 0; i < customerList.size(); i++) {
            displayList[i + 1] = customerList.get(i).getCustomerName();
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Filter by Customer")
                .setItems(displayList, (dialog, which) -> {
                    if (which == 0) {
                        selectedCustomer = null;
                        spinnerCustomer.setText(R.string.all_customers);
                    } else {
                        selectedCustomer = customerList.get(which - 1);
                        spinnerCustomer.setText(selectedCustomer.getCustomerName());
                    }
                    loadReports();
                })
                .show();
    }

    private void showProductSelectionDialog() {
        String[] displayList = new String[productList.size() + 1];
        displayList[0] = "All Products";
        for (int i = 0; i < productList.size(); i++) {
            displayList[i + 1] = productList.get(i).getProductName();
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Filter by Product")
                .setItems(displayList, (dialog, which) -> {
                    if (which == 0) {
                        selectedProduct = null;
                        spinnerProduct.setText(R.string.all_products);
                    } else {
                        selectedProduct = productList.get(which - 1);
                        spinnerProduct.setText(selectedProduct.getProductName());
                    }
                    loadReports();
                })
                .show();
    }

    private void showMonthPicker() {
        final Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM",
                    Locale.getDefault());
            Date date = sdf.parse(String.valueOf(etMonth.getText()));
            if (date != null) cal.setTime(date);
        } catch (Exception ignored) {}
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_month_picker, null);
        NumberPicker monthPicker = view.findViewById(R.id.pickerMonth);
        NumberPicker yearPicker = view.findViewById(R.id.pickerYear);
        SwitchMaterial switchMode = view.findViewById(R.id.switchMode);
        // ---- Month Picker ----
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setDisplayedValues(new DateFormatSymbols().getShortMonths());
        // ---- Year Picker ----
        int currentYear = cal.get(Calendar.YEAR);
        yearPicker.setMinValue(currentYear - 20);
        yearPicker.setMaxValue(currentYear + 10);
        monthPicker.setValue(cal.get(Calendar.MONTH));
        yearPicker.setValue(currentYear);
        // ---- Mode Handling ----
        // Default: Month + Year
        monthPicker.setVisibility(switchMode.isChecked() ? View.VISIBLE : View.GONE);
        // Toggle behavior
        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchMode.setText(R.string.month_year);
                monthPicker.setVisibility(View.VISIBLE);
            } else {
                switchMode.setText(R.string.year_only);
                monthPicker.setVisibility(View.GONE);
            }
        });
        new AlertDialog.Builder(requireContext())
                .setTitle("Select Period")
                .setView(view)
                .setPositiveButton("OK", (dialog, which) -> {
                    int year = yearPicker.getValue();
                    int month = monthPicker.getValue();
                    java.text.SimpleDateFormat sdf;
                    if (!switchMode.isChecked()) {
                        // Year only
                        sdf = new SimpleDateFormat("yyyy",
                                Locale.getDefault());
                        cal.set(Calendar.YEAR, year);
                        etMonth.setText(sdf.format(cal.getTime()));
                    } else {
                        // Month + Year
                        sdf = new SimpleDateFormat("yyyy-MM",
                                Locale.getDefault());
                        cal.set(Calendar.YEAR, year);
                        cal.set(Calendar.MONTH, month);
                        etMonth.setText(sdf.format(cal.getTime()));
                    }
                    loadReports();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadReports() {
        progressBar.setVisibility(View.VISIBLE);
        ApiInterface api = ApiClient.getClient(requireActivity()).create(ApiInterface.class);
        String month = Objects.requireNonNull(etMonth.getText()).toString();
        String customerCode = selectedCustomer != null ? selectedCustomer.getCustomerCode() : null;
        String productCode = selectedProduct != null ? selectedProduct.getProductCode() : null;
        api.getSalesReport("report", session.getUserId(), month, productCode, customerCode)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call,
                                           @NonNull Response<Map<String, Object>> response) {
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            processResponse(response.body());
                        } else {
                            Toasty.error(requireContext(), "Failed to load reports",
                                    Toasty.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call,
                                          @NonNull Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Log.d("ReportsFragment", "API call failed with exception: "+ t);
                        Toasty.error(requireContext(), "Network error",
                                Toasty.LENGTH_SHORT).show();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void processResponse(Map<String, Object> body) {
        try {
            Log.d("ReportsFragment", "API Response: " + body);
            if (!Boolean.TRUE.equals(body.get("success"))) {
                Log.w("ReportsFragment", "API success is false");
                return;
            }
            currentData.clear();
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map)) {
                Log.e("ReportsFragment", "data is not a Map: " + dataObj);
                return;
            }
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            // Prioritize daily data if available, otherwise use monthly
            Object reportListObj = dataMap.get("daily");
            if (reportListObj == null || (reportListObj instanceof List && ((List<?>)
                    reportListObj).isEmpty())) {
                reportListObj = dataMap.get("monthly");
            }
            if (reportListObj instanceof List) {
                List<Map<String, Object>> reports = (List<Map<String, Object>>) reportListObj;
                for (Map<String, Object> item : reports) {
                    String label = item.containsKey("day") ? String.valueOf(item.get("day"))
                            : String.valueOf(item.get("month"));
                    if (item.containsKey("date")) label = String.valueOf(item.get("date"));
                    int totalOrders = 0;
                    if (item.get("total_orders") != null) {
                        try {
                            totalOrders = ((Number) Objects.requireNonNull(item
                                    .get("total_orders"))).intValue();
                        } catch (Exception e) {
                            totalOrders = Integer.parseInt(String.valueOf(item
                                    .get("total_orders")));
                        }
                    }
                    double amount = 0.0;
                    if (item.get("total_amount") != null) {
                        try {
                            amount = Double.parseDouble(String.valueOf(item
                                    .get("total_amount")));
                        } catch (Exception e) {
                            Log.e("ReportsFragment", "Error parsing amount", e);
                        }
                    }
                    currentData.add(new ReportEntry(label, totalOrders, amount));
                }
            } else {
                Log.w("ReportsFragment", "Report data is not a List: " + reportListObj);
                Toasty.error(requireContext(), "Report data is not a List " +reportListObj,
                        Toasty.LENGTH_LONG).show();
            }
            updateFilterLists(body);
            updateUI();
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error processing response", e);
            Toasty.error(requireContext(), "Error processing response",
                    Toasty.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("unchecked")
    private void updateFilterLists(Map<String, Object> body) {
        try {
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map)) return;
            Map<String, Object> data = (Map<String, Object>) dataObj;
            // ---------- CUSTOMERS ----------
            Object customersObj = data.get("customers");
            customerList.clear();
            if (customersObj instanceof List<?>) {
                for (Object item : (List<?>) customersObj) {
                    if (item instanceof Map) {
                        Map<String, Object> c = (Map<String, Object>) item;
                        customerList.add(new Customer(
                                null, // SrNo
                                String.valueOf(c.get("CustomerCode")),
                                String.valueOf(c.get("CustomerName")),
                                null,
                                0,
                                0,
                                0
                        ));
                    }
                }
            }
            // ---------- PRODUCTS ----------
            Object productsObj = data.get("products");
            productList.clear();
            if (productsObj instanceof List<?>) {
                for (Object item : (List<?>) productsObj) {
                    if (item instanceof Map) {
                        Map<String, Object> p = (Map<String, Object>) item;
                        productList.add(new Product(
                                String.valueOf(p.get("ProductCode")),
                                String.valueOf(p.get("ProductName")),
                                null, null, null,
                                null, null, 1,
                                "0", "", null
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error updating filters", e);
            Toasty.error(requireContext(), "Error updating filters",
                    Toasty.LENGTH_LONG).show();
        }
    }

    private void updateUI() {
        if (getActivity() == null) return;
        adapter.notifyDataSetChanged();
        MaterialButtonToggleGroup toggleGroup = requireView().findViewById(R.id.toggleGroup);
        boolean showAmount = toggleGroup.getCheckedButtonId() == R.id.btnAmount;
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < currentData.size(); i++) {
            ReportEntry entry = currentData.get(i);
            float value = showAmount ? (float) entry.getTotalAmount() : (float) entry
                    .getTotalOrders();
            entries.add(new BarEntry(i, value));
            labels.add(entry.getLabel());
        }
        BarDataSet dataSet = new BarDataSet(entries, showAmount ? "Sales Amount" : "Order Count");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.invalidate();
    }

    // --- Merged SalesFragment Methods ---

    private void showOrderCustomerSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_customer_select,
                (ViewGroup) requireView().getParent(), false);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.customerSelectRecycler);
        SearchView searchView = view.findViewById(R.id.customerSearchView);
        loadProgress = view.findViewById(R.id.customerLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        customerAdapter = new CustomerSelectAdapter(customer -> {
            orderSelectedCustomer = customer;
            orderCustomerSpinner.setText(customer.getCustomerName());
            orderCustomerSpinner.setTextColor(ContextCompat.getColor(requireActivity(), R.color.black));
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

    private void showOrderProductSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_product_select,
                (ViewGroup) requireView().getParent(), false);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.productSelectRecycler);
        SearchView searchView = view.findViewById(R.id.productSearchView);
        loadProgress = view.findViewById(R.id.productLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        productAdapter = new ProductSelectAdapter(product -> {
            orderSelectedProduct = product;
            orderProductSpinner.setText(product.getProductName());
            orderProductSpinner.setTextColor(ContextCompat.getColor(requireActivity(), R.color.black));
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

    private void showOrderDatePicker() {
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            selectedOrderDate = sdf.format(calendar.getTime());
            etOrderDate.setText(selectedOrderDate);
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
            apiInterface.syncCustomers("sync", "2010-01-01", limit, customerOffset)
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
            apiInterface.searchCustomers("search", payload)
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
        LocationUtils.getUserLocation(requireContext(), requireActivity(), new LocationUtils.LocationResultCallback() {
            @Override
            public void onSuccess(double lat, double lng) {
                session.saveLastLocation(lat, lng);
                executeLoadProducts(reset, lat, lng);
            }
            @Override
            public void onFailure(String error) {
                Double cachedLat = session.getCachedLat();
                Double cachedLng = session.getCachedLng();
                if (cachedLat != null && cachedLng != null) {
                    executeLoadProducts(reset, cachedLat, cachedLng);
                } else {
                    if (loadProgress != null) loadProgress.setVisibility(View.GONE);
                    Toasty.error(requireContext(), "GPS is required for accurate pricing. Please enable location services.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void executeLoadProducts(boolean reset, double lat, double lng) {
        isProductLoading = true;
        if (loadProgress != null) loadProgress.setVisibility(View.VISIBLE);
        if (reset) {
            productOffset = 0;
            hasMoreProducts = true;
            if (productAdapter != null) productAdapter.clear();
        }
        if (currentProductQuery.isEmpty()) {
            apiInterface.syncProducts("sync", "2010-01-01", limit, productOffset, lat, lng)
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
            apiInterface.searchProducts("search", currentProductQuery, lat, lng)
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
        String customerSrNo = orderSelectedCustomer != null ? orderSelectedCustomer.getSrNo() : null;
        String productCode = orderSelectedProduct != null ? orderSelectedProduct.getProductCode() : null;
        String salesman = session.getUserId();
        apiInterface.filterOrders("filter", salesman, productCode, customerSrNo, selectedOrderDate).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Order>> call, @NonNull Response<ApiResponse<Order>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    salesAdapter.setOrders(response.body().getData());
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

    private void clearOrderFilters() {
        orderSelectedCustomer = null;
        orderSelectedProduct = null;
        selectedOrderDate = "";
        orderCustomerSpinner.setText("");
        orderCustomerSpinner.setHint(R.string.customer);
        orderProductSpinner.setText("");
        orderProductSpinner.setHint(R.string.product);
        etOrderDate.setText("");
        fetchSales();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchTimer != null) searchTimer.cancel();
    }
}
