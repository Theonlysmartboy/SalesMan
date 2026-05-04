package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.textfield.TextInputEditText;
import com.js.salesman.R;
import com.js.salesman.adapters.ReportAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Product;
import com.js.salesman.models.ReportEntry;
import com.js.salesman.utils.managers.SessionManager;

import java.util.ArrayList;
import java.util.List;
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
    private final List<ReportEntry> currentData = new ArrayList<>();
    private final List<Customer> customerList = new ArrayList<>();
    private final List<Product> productList = new ArrayList<>();
    private Customer selectedCustomer = null;
    private Product selectedProduct = null;
    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);
        session = new SessionManager(requireContext());
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
        adapter = new ReportAdapter(requireContext(), currentData);
        listView.setAdapter(adapter);
        etMonth.setOnClickListener(v -> showMonthPicker());
        spinnerCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        spinnerProduct.setOnClickListener(v -> showProductSelectionDialog());
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
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault());
        etMonth.setText(sdf.format(new java.util.Date()));
    }

    private void showCustomerSelectionDialog() {
        String[] displayList = new String[customerList.size() + 1];
        displayList[0] = "All Customers";
        for (int i = 0; i < customerList.size(); i++) {
            displayList[i + 1] = customerList.get(i).getCustomerName();
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
        loadReports();
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
                    public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            processResponse(response.body());
                        } else {
                            Toasty.error(requireContext(), "Failed to load reports", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Toasty.error(requireContext(), "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    @SuppressWarnings("unchecked")
    private void processResponse(Map<String, Object> body) {
        try {
            if (!Boolean.TRUE.equals(body.get("success"))) return;
            currentData.clear();
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map)) {
                Log.e("ReportsFragment", "data is not a Map");
                return;
            }
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            Object monthlyObj = dataMap.get("monthly");
            if (monthlyObj instanceof List) {
                List<Map<String, Object>> monthly = (List<Map<String, Object>>) monthlyObj;
                for (Map<String, Object> item : monthly) {
                    String month = String.valueOf(item.get("month"));
                    int totalOrders = item.get("total_orders") != null
                            ? ((Number) Objects.requireNonNull(item.get("total_orders"))).intValue()
                            : 0;
                    double amount = item.get("total_amount") != null
                            ? Double.parseDouble(String.valueOf(item.get("total_amount")))
                            : 0.0;
                    currentData.add(new ReportEntry(month, totalOrders, amount));
                }
            }
            updateFilterLists(body);
            updateUI();
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error processing response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateFilterLists(Map<String, Object> body) {
        try {
            // ---------- CUSTOMERS ----------
            Object customersObj = body.get("customers");
            customerList.clear();
            if (customersObj instanceof List<?>) {
                for (Object item : (List<?>) customersObj) {
                    if (item instanceof Map) {
                        Map<String, Object> c = (Map<String, Object>) item;
                        customerList.add(new Customer(
                                String.valueOf(c.get("SrNo")),
                                String.valueOf(c.get("CustomerCode")),
                                String.valueOf(c.get("CustomerName")),
                                String.valueOf(c.get("Category"))
                        ));
                    }
                }
            } else if (customersObj instanceof Map) {
                // Handle single object case (API inconsistency)
                Map<String, Object> c = (Map<String, Object>) customersObj;
                customerList.add(new Customer(
                        String.valueOf(c.get("SrNo")),
                        String.valueOf(c.get("CustomerCode")),
                        String.valueOf(c.get("CustomerName")),
                        String.valueOf(c.get("Category"))
                ));
            } else {
                Log.w("ReportsFragment", "customers is not List/Map: " + customersObj);
            }
            // ---------- PRODUCTS ----------
            Object productsObj = body.get("products");
            productList.clear();
            if (productsObj instanceof List<?>) {
                for (Object item : (List<?>) productsObj) {
                    if (item instanceof Map) {
                        Map<String, Object> p = (Map<String, Object>) item;
                        productList.add(new Product(
                                String.valueOf(p.get("ProductCode")),
                                String.valueOf(p.get("ProductName")),
                                String.valueOf(p.get("ProductUnit")),
                                String.valueOf(p.get("Product_Selling_Price")),
                                String.valueOf(p.get("SalesmanPrice1")),
                                String.valueOf(p.get("SalesmanPrice2")),
                                String.valueOf(p.get("SalesmanPrice3")),
                                String.valueOf(p.get("Product_VAT_Code")),
                                1, 1, "0", "0", "", "", null
                        ));
                    }
                }
            } else if (productsObj instanceof Map) {
                Map<String, Object> p = (Map<String, Object>) productsObj;
                productList.add(new Product(
                        String.valueOf(p.get("ProductCode")),
                        String.valueOf(p.get("ProductName")),
                        String.valueOf(p.get("ProductUnit")),
                        String.valueOf(p.get("Product_Selling_Price")),
                        String.valueOf(p.get("SalesmanPrice1")),
                        String.valueOf(p.get("SalesmanPrice2")),
                        String.valueOf(p.get("SalesmanPrice3")),
                        String.valueOf(p.get("Product_VAT_Code")),
                        1, 1, "0", "0", "", "", null
                ));
            } else {
                Log.w("ReportsFragment", "products is not List/Map: " + productsObj);
            }
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error updating filters", e);
        }
    }

    private void updateUI() {
        adapter.notifyDataSetChanged();
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < currentData.size(); i++) {
            entries.add(new BarEntry(i, (float) currentData.get(i).getValue()));
            labels.add(currentData.get(i).getLabel());
        }
        BarDataSet dataSet = new BarDataSet(entries, "Sales Amount");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.invalidate();
    }
}
