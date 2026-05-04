package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.NumberPicker;
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
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
import java.util.Calendar;
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
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroup);
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                updateUI();
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
        final Calendar cal = Calendar.getInstance();
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(String.valueOf(etMonth.getText()));
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
        monthPicker.setDisplayedValues(new java.text.DateFormatSymbols().getShortMonths());
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
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Select Period")
                .setView(view)
                .setPositiveButton("OK", (dialog, which) -> {
                    int year = yearPicker.getValue();
                    int month = monthPicker.getValue();
                    java.text.SimpleDateFormat sdf;
                    if (!switchMode.isChecked()) {
                        // Year only
                        sdf = new java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault());
                        cal.set(Calendar.YEAR, year);
                        etMonth.setText(sdf.format(cal.getTime()));
                    } else {
                        // Month + Year
                        sdf = new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault());
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
            if (reportListObj == null || (reportListObj instanceof List && ((List<?>) reportListObj).isEmpty())) {
                reportListObj = dataMap.get("monthly");
            }

            if (reportListObj instanceof List) {
                List<Map<String, Object>> reports = (List<Map<String, Object>>) reportListObj;
                for (Map<String, Object> item : reports) {
                    String label = item.containsKey("day") ? String.valueOf(item.get("day")) : String.valueOf(item.get("month"));
                    if (item.containsKey("date")) label = String.valueOf(item.get("date"));

                    int totalOrders = 0;
                    if (item.get("total_orders") != null) {
                        try {
                            totalOrders = ((Number) Objects.requireNonNull(item.get("total_orders"))).intValue();
                        } catch (Exception e) {
                            totalOrders = Integer.parseInt(String.valueOf(item.get("total_orders")));
                        }
                    }
                    double amount = 0.0;
                    if (item.get("total_amount") != null) {
                        try {
                            amount = Double.parseDouble(String.valueOf(item.get("total_amount")));
                        } catch (Exception e) {
                            Log.e("ReportsFragment", "Error parsing amount", e);
                        }
                    }
                    currentData.add(new ReportEntry(label, totalOrders, amount));
                }
            } else {
                Log.w("ReportsFragment", "Report data is not a List: " + reportListObj);
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
                                null  // Category
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
                                null, null, null, null, null, null,
                                1, 1, "0", "0", "", "", null
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ReportsFragment", "Error updating filters", e);
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
            float value = showAmount ? (float) entry.getTotalAmount() : (float) entry.getTotalOrders();
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
}
