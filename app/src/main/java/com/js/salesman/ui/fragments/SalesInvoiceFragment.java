package com.js.salesman.ui.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.utils.CurrencyFormatter;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.utils.managers.SettingsManager;

import org.json.JSONObject;

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

public class SalesInvoiceFragment extends Fragment {
    private TextView txtCreditLimit, txtOutstanding,
            txtCreditDays, tvSelectedCustomer, tvSelectedProduct;
    private Db db;
    private SettingsManager settingsManager;
    private Customer selectedCustomer;
    private int offset = 0;
    private final int limit = 20;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private String currentSearchQuery = "";
    private CustomerSelectAdapter customerAdapter;
    private ProgressBar loadProgress;
    private Timer searchTimer;

    public SalesInvoiceFragment() {
        // Required empty public constructor
    }

   @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sales_invoice, container, false);
        db = new Db(requireContext());
        settingsManager = new SettingsManager(requireContext());
        SessionManager session = new SessionManager(requireContext());
        selectedCustomer = session.getSelectedCustomer();
        txtCreditLimit = view.findViewById(R.id.txtCreditLimit);
        txtOutstanding = view.findViewById(R.id.txtOutstanding);
        txtCreditDays = view.findViewById(R.id.txtCreditDays);
        tvSelectedCustomer = view.findViewById(R.id.tvSelectedCustomer);
        tvSelectedProduct = view.findViewById(R.id.tvSelectedProduct);
        tvSelectedCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        return view;
    }

    // Methods to show customer selection dialog
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
            tvSelectedCustomer.setText(customer.toString());
            tvSelectedCustomer.setTextColor(ContextCompat.getColor(requireActivity(),
                    R.color.black));
            txtCreditLimit.setText(CurrencyFormatter.format(customer.getCreditLimit(), "Ksh"));
            txtOutstanding.setText(CurrencyFormatter.format(customer.getOutstanding(), "Ksh"));
            if (customer.getOutstanding() > 0) {
            txtOutstanding.setTextColor(ContextCompat.getColor(requireActivity(), R.color.red));
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
                            handleResponse(response);
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
                            handleResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Customer>> call,
                                              @NonNull Throwable t) {
                            handleFailure(t);
                        }
                    });
        }
    }

    private void handleResponse(Response<ApiResponse<Customer>> response) {
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
                    LogManager.logError(requireContext(), "CheckoutFragment",
                            "Error parsing errorBody", e);
                }
            } else {
                message = "Server error: " + response.code();
            }
            Toasty.error(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void handleFailure(Throwable t) {
        isLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        LogManager.logError(requireContext(), "CheckoutFragment",
                "Network call failed", t);
        if (isAdded()) {
            Toasty.error(requireContext(), "Error connecting to server",
                    Toast.LENGTH_SHORT).show();
        }
    }
}