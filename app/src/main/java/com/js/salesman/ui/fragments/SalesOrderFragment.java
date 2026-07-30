package com.js.salesman.ui.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.adapters.ProductSelectAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Product;
import com.js.salesman.models.ProductListResponse;
import com.js.salesman.models.SalesOrderItem;
import com.js.salesman.adapters.SalesOrderAdapter;
import com.js.salesman.utils.CurrencyFormatter;
import com.js.salesman.utils.LoadingHandler;
import com.js.salesman.utils.TrailingDotsLoader;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.utils.OrderSubmissionHandler;

import org.json.JSONObject;

import java.util.ArrayList;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import es.dmoral.toasty.Toasty;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SalesOrderFragment extends Fragment {
    private TextView tvSelectedCustomer, txtCreditLimit, txtOutstanding,
            txtCreditDays, tvSelectedProduct, txtSubTotal, txtVat, txtDiscount, txtTotal;
    private Customer selectedCustomer;
    private int offset = 0;
    private final int limit = 20;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private String currentSearchQuery = "";
    private CustomerSelectAdapter customerAdapter;
    private ProductSelectAdapter productAdapter;
    private ProgressBar loadProgress;
    private FrameLayout loaderOverlay;
    private TrailingDotsLoader loader;
    private Timer searchTimer;
    private SalesOrderAdapter salesOrderAdapter;
    private double subtotal, vat, discount, total;
    private MaterialButton btnSave, btnClear;
    private BottomSheetDialog dialog;
    private String selectionMode = "customer"; // "customer" or "product"

    public SalesOrderFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales_order, container, false);
        loaderOverlay = view.findViewById(R.id.loaderOverlay);
        loader = new TrailingDotsLoader(requireContext());
        SessionManager session = new SessionManager(requireContext());
        selectedCustomer = session.getSelectedCustomer();
        txtCreditLimit = view.findViewById(R.id.txtCreditLimit);
        txtOutstanding = view.findViewById(R.id.txtOutstanding);
        txtCreditDays = view.findViewById(R.id.txtCreditDays);
        tvSelectedCustomer = view.findViewById(R.id.tvSelectedCustomer);
        tvSelectedProduct = view.findViewById(R.id.tvSelectedProduct);
        tvSelectedCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        txtSubTotal = view.findViewById(R.id.txtSubTotal);
        subtotal = 0;
        vat = 0;
        discount = 0;
        total = 0;
        txtVat = view.findViewById(R.id.txtVat);
        txtDiscount = view.findViewById(R.id.txtDiscount);
        txtTotal = view.findViewById(R.id.txtTotal);
        btnSave = view.findViewById(R.id.btnSave);
        btnClear = view.findViewById(R.id.btnClear);
        tvSelectedProduct.setOnClickListener(v -> {
            if (selectedCustomer != null &&
                    selectedCustomer.getCreditLimit() < selectedCustomer.getOutstanding()) {
                Toasty.warning(requireContext(),
                    "Customer has an overdue outstanding balance. Cannot add products.",
                    Toast.LENGTH_LONG).show();
                return;
            }
            showProductSelectionDialog();
        });
        btnClear.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear Sales Order")
                        .setMessage("Are you sure you want to clear this Sales Order?")
                        .setPositiveButton("Yes", (dialog,
                                                    which) -> clearInvoice())
                        .setNegativeButton("No", null)
                        .show()
        );
        RecyclerView recyclerView = view.findViewById(R.id.rvSalesOrder);
        salesOrderAdapter = new SalesOrderAdapter();
        salesOrderAdapter.setOnItemRemovedListener((item, position) -> {
            salesOrderAdapter.removeItem(position);
            updateTotals();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(salesOrderAdapter);

        btnSave.setOnClickListener(v -> submitOrder());

        return view;
    }

    private void submitOrder() {
        if (selectedCustomer == null || selectedCustomer.getSrNo() == null) {
            Toasty.warning(requireContext(), "Select customer", Toast.LENGTH_SHORT).show();
            return;
        }

        List<SalesOrderItem> items = salesOrderAdapter.getItems();
        if (items.isEmpty()) {
            Toasty.warning(requireContext(), "No items added", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        for (SalesOrderItem item : items) {
            Map<String, Object> line = new HashMap<>();
            line.put("ProductCode", item.getCode());
            line.put("Quantity", item.getQuantity());
            line.put("UnitPrice", item.getPrice());
            line.put("Discount", item.getDiscount());
            line.put("VatRate", item.getVatRate());
            line.put("LineTotal", item.getLineTotal());
            lines.add(line);
        }

        OrderSubmissionHandler.submitOrder(requireContext(), selectedCustomer, lines, total,
                vat, discount, new OrderSubmissionHandler.SubmissionCallback() {
            @Override
            public void onStart() {
                btnSave.setEnabled(false);
                btnClear.setEnabled(false);
                LoadingHandler.showLoading(requireContext(), loader, loaderOverlay);
            }

            @Override
            public void onSuccess(String message) {
                Toasty.success(requireContext(), message, Toast.LENGTH_LONG).show();
                clearInvoice();
            }

            @Override
            public void onFailure(String error) {
                Toasty.error(requireContext(), error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFinish() {
                if (isAdded()) {
                    btnSave.setEnabled(true);
                    btnClear.setEnabled(true);
                    LoadingHandler.hideLoading(loaderOverlay);
                }
            }
        });
    }

    // Methods to show customer selection dialog
    private void showCustomerSelectionDialog() {
        selectionMode = "customer";
        dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_customer_select,
                (ViewGroup) requireView().getParent(), false);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.customerSelectRecycler);
        SearchView searchView = view.findViewById(R.id.customerSearchView);
        loadProgress = view.findViewById(R.id.customerLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        customerAdapter = new CustomerSelectAdapter(customer -> {
            selectedCustomer = customer;
            tvSelectedCustomer.setText(customer.toString());
            tvSelectedCustomer.setTextColor(ContextCompat.getColor(requireActivity(),
                    R.color.black));
            txtCreditLimit.setText(CurrencyFormatter.format(customer.getCreditLimit(),
                    "Ksh"));
            txtOutstanding.setText(CurrencyFormatter.format(customer.getOutstanding(),
                    "Ksh"));
            if (customer.getOutstanding() > 0) {
            txtOutstanding.setTextColor(ContextCompat.getColor(requireActivity(), R.color.red));
            }else{
                txtOutstanding.setTextColor(ContextCompat.getColor(requireActivity(),
                        R.color.gray));
            }
            txtCreditDays.setText(String.valueOf(customer.getCreditDays()));
            dialog.dismiss();
        });
        recyclerView.setAdapter(customerAdapter);
        offset = 0;
        hasMoreData = true;
        currentSearchQuery = "";
        loadCustomers(true);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null && !isLoading && hasMoreData) {
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
                currentSearchQuery = query;
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
                                currentSearchQuery = newText;
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

   private void loadCustomers(boolean reset) {
        if (isLoading) return;
        if (!reset && !hasMoreData) return;
        isLoading = true;
        if (loadProgress != null) loadProgress.setVisibility(View.VISIBLE);
        if (reset) {
            offset = 0;
            hasMoreData = true;
            if (customerAdapter != null) customerAdapter.clear();
        }
        ApiInterface api = ApiClient.getClient(requireActivity()).create(ApiInterface.class);
        if (currentSearchQuery.isEmpty()) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -10);
            String lastSync = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(cal.getTime());
            api.syncCustomers("sync", lastSync, limit, offset)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                        @NonNull Response<ApiResponse<Customer>> response) {
                            handleCustomerResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                        @NonNull Throwable t) {
                            handleFailure(t);
                        }
                    });
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", currentSearchQuery);
            payload.put("limit", limit);
            payload.put("offset", offset);
            api.searchCustomers("search", payload)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                        @NonNull Response<ApiResponse<Customer>> response) {
                            handleCustomerResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                        @NonNull Throwable t) {
                            handleFailure(t);
                        }
                    });
        }
    }

    private void handleCustomerResponse(Response<ApiResponse<Customer>> response) {
        Log.d("Handle response", "Customers: "+ response.toString());
        isLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        if (response.isSuccessful() && response.body() != null) {
            List<Customer> newCustomers = response.body().getData();
            if (newCustomers != null && !newCustomers.isEmpty()) {
                if (customerAdapter != null) {
                    customerAdapter.addCustomers(newCustomers);
                    offset += newCustomers.size();
                    if (newCustomers.size() < limit) {
                        hasMoreData = false;
                    }
                }
            } else {
                hasMoreData = false;
            }
        } else {
            hasMoreData = false;
            String message = "Unable to load customers";
            ResponseBody errorBody = response.errorBody();
            if (errorBody != null) {
                try (ResponseBody body = errorBody) {
                    String errorJson = body.string();
                    JSONObject json = new JSONObject(errorJson);
                    if (json.has("message")) {
                        message = json.getString("message");
                    }
                } catch (Exception e) {
                    LogManager.logError(requireContext(), "SalesOrderFragment",
                            "Error parsing errorBody", e);
                }
            } else {
                message = "Server error: " + response.code();
            }
            Toasty.error(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    //Methods to show product selection dialog
    private void showProductSelectionDialog() {
        selectionMode = "product";
        dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_product_select,
                (ViewGroup) requireView().getParent(), false);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.productSelectRecycler);
        SearchView searchView = view.findViewById(R.id.productSearchView);
        loadProgress = view.findViewById(R.id.productLoadProgress);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        productAdapter = new ProductSelectAdapter(this::showQuantityDialog);
        recyclerView.setAdapter(productAdapter);
        offset = 0;
        hasMoreData = true;
        currentSearchQuery = "";
        loadProducts(true);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView
                            .getLayoutManager();
                    if (lm != null && !isLoading && hasMoreData) {
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
                currentSearchQuery = query;
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
                                currentSearchQuery = newText;
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

    private void loadProducts(boolean reset) {
        if (isLoading) return;
        if (!reset && !hasMoreData) return;
        isLoading = true;
        if (loadProgress != null) loadProgress.setVisibility(View.VISIBLE);
        if (reset) {
            offset = 0;
            hasMoreData = true;
            if (productAdapter != null) productAdapter.clear();
        }
        ApiInterface api = ApiClient.getClient(requireActivity()).create(ApiInterface.class);
        if (currentSearchQuery.isEmpty()) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -10);
            String lastSync = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(cal.getTime());
            api.syncProducts("sync", lastSync, limit, offset, null, null)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ProductListResponse> call,
                                               @NonNull Response<ProductListResponse> response) {
                            handleProductResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<ProductListResponse> call,
                                              @NonNull Throwable t) {
                            handleFailure(t);
                        }
                    });
        } else {
            api.searchProductsPaged("search", currentSearchQuery, limit, offset,
                            null, null)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ProductListResponse> call,
                                               @NonNull Response<ProductListResponse> response) {
                            handleProductResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<ProductListResponse> call,
                                              @NonNull Throwable t) {
                            handleFailure(t);
                        }
                    });
        }
    }

    private void handleProductResponse(Response<ProductListResponse> response) {
        isLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        if (response.isSuccessful() && response.body() != null) {
            List<Product> newProducts = response.body().getData();
            if (newProducts != null && !newProducts.isEmpty()) {
                if (productAdapter != null) {
                    productAdapter.addProducts(newProducts);
                    offset += newProducts.size();
                    if (newProducts.size() < limit) {
                        hasMoreData = false;
                    }
                }
            } else {
                hasMoreData = false;
            }
        } else {
            hasMoreData = false;
            Toasty.error(requireContext(), "Unable to load products",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFailure(Throwable t) {
        isLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        LogManager.logError(requireContext(), "SalesOrderFragment",
                "Network call failed", t);
        if (isAdded()) {
            Toasty.error(requireContext(), "Error connecting to server",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showQuantityDialog(Product product) {
        if (dialog != null) dialog.dismiss();

        View view = getLayoutInflater().inflate(R.layout.layout_quantity_dialog, null);
        TextView tvName = view.findViewById(R.id.dialogProductName);
        TextView tvDetails = view.findViewById(R.id.dialogProductDetails);
        EditText etQty = view.findViewById(R.id.etQuantity);
        MaterialButton btnPlus = view.findViewById(R.id.btnPlus);
        MaterialButton btnMinus = view.findViewById(R.id.btnMinus);

        tvName.setText(product.getProductName());
        double price = Double.parseDouble(product.getProduct_Selling_Price());
        tvDetails.setText(String.format("Price: %s | Unit: %s | Stock: %s",
                CurrencyFormatter.format(price, "Ksh"),
                product.getProductUnit(),
                product.getProductQuantity()));

        btnPlus.setOnClickListener(v -> {
            String qStr = etQty.getText().toString();
            double q = qStr.isEmpty() ? 0 : Double.parseDouble(qStr);
            etQty.setText(String.valueOf(q + 1));
        });

        btnMinus.setOnClickListener(v -> {
            String qStr = etQty.getText().toString();
            double q = qStr.isEmpty() ? 0 : Double.parseDouble(qStr);
            if (q > 1) {
                etQty.setText(String.valueOf(q - 1));
            }
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Quantity")
                .setView(view)
                .setPositiveButton("Add Item", (d, which) -> {
                    String qStr = etQty.getText().toString();
                    if (!qStr.isEmpty()) {
                        double qty = Double.parseDouble(qStr);
                        if (qty > 0) {
                            addOrUpdateCart(product, qty);
                        } else {
                            Toasty.warning(requireContext(),
                                    "Quantity must be greater than 0").show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addOrUpdateCart(Product product, double quantity) {
        List<SalesOrderItem> items = salesOrderAdapter.getItems();
        boolean exists = false;
        double price = Double.parseDouble(product.getProduct_Selling_Price());

        for (SalesOrderItem item : items) {
            if (item.getCode().equals(product.getProductCode())) {
                item.setQuantity(item.getQuantity() + quantity);
                exists = true;
                break;
            }
        }

        if (!exists) {
            SalesOrderItem newItem = new SalesOrderItem(
                    product.getProductCode().hashCode(),
                    product.getProductCode(),
                    product.getProductName(),
                    product.getProductUnit(),
                    quantity,
                    price,
                    0,
                    0 // Default VAT rate
            );
            salesOrderAdapter.addItem(newItem);
        } else {
            salesOrderAdapter.notifyDataSetChanged();
        }

        updateTotals();
    }

    private void updateTotals() {
        subtotal = calculateSubtotal();
        vat = calculateVat();
        discount = calculateDiscount();
        total = calculateGrandTotal();

        txtSubTotal.setText(CurrencyFormatter.format(subtotal, "Ksh"));
        txtVat.setText(CurrencyFormatter.format(vat, "Ksh"));
        txtDiscount.setText(CurrencyFormatter.format(discount, "Ksh"));
        txtTotal.setText(CurrencyFormatter.format(total, "Ksh"));
    }

    private double calculateSubtotal() {
        double sum = 0;
        for (SalesOrderItem item : salesOrderAdapter.getItems()) {
            sum += item.getQuantity() * item.getPrice();
        }
        return sum;
    }

    private double calculateVat() {
        double sum = 0;
        for (SalesOrderItem item : salesOrderAdapter.getItems()) {
            sum += item.getVatAmount();
        }
        return sum;
    }

    private double calculateDiscount() {
        double sum = 0;
        for (SalesOrderItem item : salesOrderAdapter.getItems()) {
            sum += item.getDiscount();
        }
        return sum;
    }

    private double calculateGrandTotal() {
        return (subtotal - discount) + vat;
    }

    private void clearInvoice() {
        // Clear selected objects
        selectedCustomer = null;
        // Reset selectors
        tvSelectedCustomer.setText(R.string.click_to_select_customer);
        tvSelectedCustomer.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        tvSelectedProduct.setText(R.string.click_to_select_product);
        tvSelectedProduct.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        // Reset customer details
        txtCreditLimit.setText(getString(R.string.kes_0_00));
        txtOutstanding.setText(getString(R.string.kes_0_00));
        txtOutstanding.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        txtCreditDays.setText(getString(R.string._0));
        // Reset totals
        txtSubTotal.setText(getString(R.string.kes_0_00));
        txtVat.setText(getString(R.string.kes_0_00));
        txtDiscount.setText(getString(R.string.kes_0_00));
        txtTotal.setText(getString(R.string.kes_0_00));
        salesOrderAdapter.clear();
        // Reset calculated values
        subtotal = 0;
        vat = 0;
        discount = 0;
        total = 0;
    }
}