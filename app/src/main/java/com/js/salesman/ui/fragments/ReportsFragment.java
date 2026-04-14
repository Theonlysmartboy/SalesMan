package com.js.salesman.ui.fragments;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.adapters.ProductSelectAdapter;
import com.js.salesman.adapters.ReportAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Product;
import com.js.salesman.models.ReportEntry;
import com.js.salesman.session.SessionManager;
import com.js.salesman.utils.ReportUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

    private List<ReportEntry> currentData = new ArrayList<>();
    private final List<Customer> customerList = new ArrayList<>();
    private final List<Product> productList = new ArrayList<>();
    private Customer selectedCustomer = null;
    private Product selectedProduct = null;
    private boolean showAmount = true;
    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);
        initViews(view);
        setupChart();
        setupFilters();
        loadReports();
        return view;
    }

    private void initViews(View view) {
        barChart = view.findViewById(R.id.barChart);
        ListView listView = view.findViewById(R.id.listViewReports);
        progressBar = view.findViewById(R.id.progressBar);
        etMonth = view.findViewById(R.id.etFilterMonth);
        spinnerCustomer = view.findViewById(R.id.spinnerCustomer);
        spinnerProduct = view.findViewById(R.id.spinnerProduct);
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroup);

        session = new SessionManager(requireContext());
        adapter = new ReportAdapter(requireContext(), currentData);
        listView.setAdapter(adapter);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showAmount = (checkedId == R.id.btnAmount);
                updateUI();
            }
        });
    }

    private void setupChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setDoubleTapToZoomEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(12);

        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(true);
    }

    private void setupFilters() {
        etMonth.setOnClickListener(v -> showMonthPicker());

        spinnerCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        spinnerProduct.setOnClickListener(v -> showProductSelectionDialog());

        spinnerCustomer.setText("All Customers");
        spinnerProduct.setText("All Products");
    }

    private void showCustomerSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_customer_select, null);
        dialog.setContentView(view);

        RecyclerView recyclerView = view.findViewById(R.id.customerSelectRecycler);
        SearchView searchView = view.findViewById(R.id.customerSearchView);
        view.findViewById(R.id.customerLoadProgress).setVisibility(View.GONE);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        List<Customer> displayList = new ArrayList<>();
        displayList.add(new Customer("0", "ALL", "All Customers"));
        displayList.addAll(customerList);

        CustomerSelectAdapter adapter = new CustomerSelectAdapter(customer -> {
            if (customer.getSrNo().equals("0")) {
                selectedCustomer = null;
                spinnerCustomer.setText("All Customers");
            } else {
                selectedCustomer = customer;
                spinnerCustomer.setText(customer.getCustomerName());
            }
            dialog.dismiss();
            loadReports();
        });
        
        adapter.setCustomers(displayList);
        recyclerView.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }
            @Override
            public boolean onQueryTextChange(String newText) {
                List<Customer> filtered = new ArrayList<>();
                for (Customer c : displayList) {
                    if (c.getCustomerName().toLowerCase().contains(newText.toLowerCase()) || 
                        c.getCustomerCode().toLowerCase().contains(newText.toLowerCase())) {
                        filtered.add(c);
                    }
                }
                adapter.setCustomers(filtered);
                return true;
            }
        });

        dialog.show();
    }

    private void showProductSelectionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_product_select, null);
        dialog.setContentView(view);

        RecyclerView recyclerView = view.findViewById(R.id.productSelectRecycler);
        SearchView searchView = view.findViewById(R.id.productSearchView);
        view.findViewById(R.id.productLoadProgress).setVisibility(View.GONE);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Product> displayList = new ArrayList<>();
        // Create a dummy "All Products" item
        Product allProduct = new Product("0", "All Products", "", "0", "", 0, 0, "0", "0", "", "", null);
        displayList.add(allProduct);
        displayList.addAll(productList);

        ProductSelectAdapter adapter = new ProductSelectAdapter(product -> {
            if (product.getProductCode().equals("0")) {
                selectedProduct = null;
                spinnerProduct.setText("All Products");
            } else {
                selectedProduct = product;
                spinnerProduct.setText(product.getProductName());
            }
            dialog.dismiss();
            loadReports();
        });

        adapter.setProducts(displayList);
        recyclerView.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }
            @Override
            public boolean onQueryTextChange(String newText) {
                List<Product> filtered = new ArrayList<>();
                for (Product p : displayList) {
                    if (p.getProductName().toLowerCase().contains(newText.toLowerCase()) || 
                        p.getProductCode().toLowerCase().contains(newText.toLowerCase())) {
                        filtered.add(p);
                    }
                }
                adapter.setProducts(filtered);
                return true;
            }
        });

        dialog.show();
    }

    private void showMonthPicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            String selected = String.format(Locale.getDefault(), "%d-%02d", year, month + 1);
            etMonth.setText(selected);
            loadReports();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void loadReports() {
        progressBar.setVisibility(View.VISIBLE);
        String salesman = session.getUserId();
        String month = Objects.requireNonNull(etMonth.getText()).toString();
        
        // Use SrNo for customer and ProductCode for product
        String customer = (selectedCustomer != null) ? selectedCustomer.getSrNo() : "";
        String product = (selectedProduct != null) ? selectedProduct.getProductCode() : "";

        ApiInterface api = ApiClient.getApi(requireContext());
        api.getSalesReport("report", salesman, month, product, customer).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    processResponse(response.body(), month);
                } else {
                    Toasty.error(requireContext(), "Failed to load reports").show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toasty.error(requireContext(), "Error: " + t.getMessage()).show();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void processResponse(Map<String, Object> body, String selectedMonth) {
        try {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            List<Map<String, Object>> rawList;

            if (selectedMonth == null || selectedMonth.isEmpty()) {
                assert data != null;
                rawList = (List<Map<String, Object>>) data.get("monthly");
            } else {
                assert data != null;
                rawList = (List<Map<String, Object>>) data.get("daily");
            }

            List<ReportEntry> entries = new ArrayList<>();
            if (rawList != null) {
                for (Map<String, Object> map : rawList) {
                    String label = String.valueOf(map.get("label") != null ? map.get("label") :
                            map.get("month") != null ? map.get("month") :
                                    map.get("day") != null ? map.get("day") : "");

                    int orders = 0;
                    Object ordersObj = map.get("total_orders");
                    if (ordersObj != null) {
                        if (ordersObj instanceof Number) {
                            orders = ((Number) ordersObj).intValue();
                        } else {
                            try {
                                orders = (int) Double.parseDouble(String.valueOf(ordersObj));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    double amount = 0.0;
                    Object amountObj = map.get("total_amount");
                    if (amountObj != null) {
                        if (amountObj instanceof Number) {
                            amount = ((Number) amountObj).doubleValue();
                        } else {
                            try {
                                amount = Double.parseDouble(String.valueOf(amountObj));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    entries.add(new ReportEntry(label, orders, amount));
                }
            }

            // Update filters based on returned data if it's the initial load or monthly view
            if (selectedMonth == null || selectedMonth.isEmpty()) {
                updateFilterLists(body);
            }

            if (selectedMonth == null || selectedMonth.isEmpty()) {
                currentData = ReportUtils.normalizeMonthlyData(entries);
            } else {
                currentData = entries;
            }

            updateUI();
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error processing response", e);
            Toasty.error(requireContext(), "Data parsing error").show();
        }
    }

    @SuppressWarnings("unchecked")
    private void updateFilterLists(Map<String, Object> body) {
        try {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return;

            List<Map<String, Object>> rawCustomers = (List<Map<String, Object>>) data.get("customers");
            List<Map<String, Object>> rawProducts = (List<Map<String, Object>>) data.get("products");

            if (rawCustomers != null) {
                customerList.clear();
                for (Map<String, Object> map : rawCustomers) {
                    customerList.add(new Customer(
                            String.valueOf(map.get("CustomerCode")),
                            String.valueOf(map.get("CustomerCode")),
                            String.valueOf(map.get("CustomerName"))
                    ));
                }
            }

            if (rawProducts != null) {
                productList.clear();
                for (Map<String, Object> map : rawProducts) {
                    productList.add(new Product(
                            String.valueOf(map.get("ProductCode")),
                            String.valueOf(map.get("ProductName")),
                            "", "0", "", 0, 0, "0", "0", "", "", null
                    ));
                }
            }
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error updating filters", e);
        }
    }

    private void updateUI() {
        adapter.clear();
        adapter.addAll(currentData);
        adapter.notifyDataSetChanged();

        List<BarEntry> chartEntries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        for (int i = 0; i < currentData.size(); i++) {
            ReportEntry entry = currentData.get(i);
            float value = showAmount ? (float) entry.getTotalAmount() : (float) entry.getTotalOrders();
            chartEntries.add(new BarEntry(i, value));

            String label = entry.getLabel();
            if (label.contains("-")) {
                labels.add(ReportUtils.getMonthName(label));
            } else {
                labels.add(label);
            }
        }

        BarDataSet dataSet = new BarDataSet(chartEntries, showAmount ? "Total Amount" : "Total Orders");
        dataSet.setColor(Color.parseColor("#4CAF50"));
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barChart.setData(barData);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                }
                return "";
            }
        });

        barChart.animateY(1000);
        barChart.invalidate();
    }
}
