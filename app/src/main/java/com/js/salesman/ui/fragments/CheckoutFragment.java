package com.js.salesman.ui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.repository.CartRepository;
import com.js.salesman.ui.activities.auth.LockActivity;
import com.js.salesman.utils.AppConstants;
import com.js.salesman.utils.LoadingHandler;
import com.js.salesman.utils.OrderSubmissionHandler;
import com.js.salesman.utils.TrailingDotsLoader;
import com.js.salesman.utils.managers.LogManager;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.utils.managers.SettingsManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import es.dmoral.toasty.Toasty;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckoutFragment extends Fragment {
    private static final String TAG = "CheckoutFragment";
    private static final long SEARCH_DELAY_MS = 600;
    private TextView tvSelectedCustomer;
    private EditText etCustomerName, etCustomerPhone, etCustomerEmail, etCustomerAddress;
    private TextView tvOrderSummary;
    private MaterialButton btnSubmitOrder, btnCreateCustomer;
    private FrameLayout loaderOverlay;
    private TrailingDotsLoader loader;
    private ProgressBar loadProgress;
    private CustomerSelectAdapter customerAdapter;
    private CartRepository cartRepository;
    private SettingsManager settingsManager;
    private Customer selectedCustomer;
    private Context appContext;
    private final int limit = AppConstants.batchSize;
    private int offset = 0;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private String currentSearchQuery = "";
    private int customerRequestId = 0;
    private Call<ApiResponse<Customer>> customerCall;
    private boolean isSubmitting = false;
    private boolean isCreatingCustomer = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> authLauncher;

    public CheckoutFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authLauncher = registerForActivityResult(new ActivityResultContracts
                        .StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        processOrderSubmission();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_checkout, container, false);
        appContext = requireContext().getApplicationContext();
        cartRepository = CartRepository.getInstance(appContext);
        settingsManager = new SettingsManager(requireContext());
        selectedCustomer = new SessionManager(requireContext()).getSelectedCustomer();
        tvSelectedCustomer = view.findViewById(R.id.tvSelectedCustomer);
        etCustomerName = view.findViewById(R.id.etCustomerName);
        etCustomerPhone = view.findViewById(R.id.etCustomerPhone);
        etCustomerEmail = view.findViewById(R.id.etCustomerEmail);
        etCustomerAddress = view.findViewById(R.id.etCustomerAddress);
        tvOrderSummary = view.findViewById(R.id.tvOrderSummary);
        loaderOverlay = view.findViewById(R.id.loaderOverlay);
        loader = new TrailingDotsLoader(requireContext());
        ImageView btnBack = view.findViewById(R.id.btnBack);
        btnCreateCustomer = view.findViewById(R.id.btnCreateCustomer);
        btnSubmitOrder = view.findViewById(R.id.btnSubmitOrder);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager()
                .popBackStack());
        if (selectedCustomer != null) {
            tvSelectedCustomer.setText(selectedCustomer.toString());
        }
        tvSelectedCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        updateOrderSummary();
        btnCreateCustomer.setOnClickListener(v -> createCustomer());
        btnSubmitOrder.setOnClickListener(v -> submitOrder());
        return view;
    }

    @Override
    public void onDestroyView() {
        cancelPendingSearch();
        cancelCustomerCall();
        customerRequestId++; // invalidate any response still in flight
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private void updateOrderSummary() {
        cartRepository.getCartItems(items -> {
            if (!isAdded() || tvOrderSummary == null) return;
            double total = 0;
            for (HashMap<String, String> item : items) {
                total += lineTotal(item);
            }
            tvOrderSummary.setText(getString(R.string.order_summary_val, items.size(), total));
        });
    }

    private static double lineTotal(HashMap<String, String> item) {
        try {
            return Double.parseDouble(Objects.requireNonNull(item.get("unit_price")))
                    * Integer.parseInt(Objects.requireNonNull(item.get("quantity")));
        } catch (Exception e) {
            Log.w(TAG, "Skipping malformed cart line: " + item, e);
            return 0;
        }
    }

    private void submitOrder() {
        if (isSubmitting) return;
        if (selectedCustomer == null || "0".equals(selectedCustomer.getCustomerCode())) {
            Toasty.warning(requireContext(), "Select customer", Toasty.LENGTH_SHORT).show();
            return;
        }
        cartRepository.getCartItems(items -> {
            if (!isAdded()) return;
            if (items.isEmpty()) {
                Toasty.warning(requireContext(), "Cart empty", Toasty.LENGTH_SHORT,
                        true).show();
                return;
            }
            if (settingsManager.isAuthRequiredForOrder()) {
                Intent intent = new Intent(requireContext(), LockActivity.class);
                intent.putExtra("is_auth_for_action", true);
                authLauncher.launch(intent);
            } else {
                processOrderSubmission();
            }
        });
    }

    private void processOrderSubmission() {
        if (isSubmitting) return;
        isSubmitting = true; // blocks double taps while the cart is being read
        cartRepository.getCartItems(items -> {
            if (!isAdded()) {
                isSubmitting = false;
                return;
            }
            List<Map<String, Object>> lines = new ArrayList<>();
            double total = 0;
            for (HashMap<String, String> item : items) {
                try {
                    int qty = Integer.parseInt(Objects.requireNonNull(item.get("quantity")));
                    double price = Double.parseDouble(Objects.requireNonNull(item.get("unit_price")));
                    Map<String, Object> line = new HashMap<>();
                    line.put("ProductCode", item.get("product_code"));
                    line.put("Quantity", qty);
                    line.put("UnitPrice", price);
                    lines.add(line);
                    total += qty * price;
                } catch (Exception e) {
                    Log.w(TAG, "Skipping malformed cart line: " + item, e);
                }
            }
            if (lines.isEmpty()) {
                isSubmitting = false;
                Toasty.warning(requireContext(), "Cart empty", Toasty.LENGTH_SHORT,
                        true).show();
                return;
            }
            sendOrder(lines, total);
        });
    }

    private void sendOrder(List<Map<String, Object>> lines, double total) {
        OrderSubmissionHandler.submitOrder(requireContext(), selectedCustomer, lines, total,
                0, 0, new OrderSubmissionHandler.SubmissionCallback() {
                    @Override
                    public void onStart() {
                        if (!isAdded()) return;
                        btnSubmitOrder.setEnabled(false);
                        LoadingHandler.showLoading(requireContext(), loader, loaderOverlay);
                    }

                    @Override
                    public void onSuccess(String message) {
                        cartRepository.clearCart(null);
                        Toasty.success(appContext, message, Toasty.LENGTH_LONG).show();
                        if (isAdded()) {
                            requireActivity().invalidateOptionsMenu();
                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.fragment_container, new ProductFragment())
                                    .commit();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        Toasty.error(appContext, error, Toasty.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFinish() {
                        isSubmitting = false;
                        if (isAdded()) {
                            btnSubmitOrder.setEnabled(true);
                            LoadingHandler.hideLoading(loaderOverlay);
                        }
                    }
                });
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
            tvSelectedCustomer.setText(customer.toString());
            tvSelectedCustomer.setTextColor(
                    ContextCompat.getColor(requireActivity(), R.color.black));
            dialog.dismiss();
        });
        recyclerView.setAdapter(customerAdapter);
        currentSearchQuery = "";
        loadCustomers(true);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && !isLoading && hasMoreData
                        && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 2) {
                    loadCustomers(false);
                }
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                cancelPendingSearch();
                currentSearchQuery = query == null ? "" : query;
                loadCustomers(true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                cancelPendingSearch();
                searchRunnable = () -> {
                    currentSearchQuery = newText == null ? "" : newText;
                    loadCustomers(true);
                };
                mainHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                return true;
            }
        });
        dialog.setOnDismissListener(d -> {
            cancelPendingSearch();
            cancelCustomerCall();
            customerRequestId++;
            isLoading = false;
            loadProgress = null;
        });
        dialog.show();
    }

    private void loadCustomers(boolean reset) {
        if (!isAdded()) return;
        if (!reset && (isLoading || !hasMoreData)) return;
        if (reset) {
            cancelCustomerCall();
            offset = 0;
            hasMoreData = true;
            if (customerAdapter != null) customerAdapter.clear();
        }
        isLoading = true;
        if (loadProgress != null) loadProgress.setVisibility(View.VISIBLE);
        final int requestId = ++customerRequestId;
        final String query = currentSearchQuery;
        final int requestOffset = offset;
        withApi(api -> {
            if (requestId != customerRequestId || !isAdded()) return;

            Call<ApiResponse<Customer>> call;
            if (query.isEmpty()) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.YEAR, -10);
                String lastSync = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(cal.getTime());
                call = api.syncCustomers("sync", lastSync, limit, requestOffset);
            } else {
                Map<String, Object> payload = new HashMap<>();
                payload.put("query", query);
                payload.put("limit", limit);
                payload.put("offset", requestOffset);
                call = api.searchCustomers("search", payload);
            }
            customerCall = call;
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Customer>> c,
                                        @NonNull Response<ApiResponse<Customer>> response) {
                    if (requestId != customerRequestId || !isAdded()) return;
                    handleResponse(response);
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Customer>> c,
                                        @NonNull Throwable t) {
                    if (c.isCanceled() || requestId != customerRequestId || !isAdded()) return;
                    handleFailure(t);
                }
            });
        }, error -> {
            if (requestId != customerRequestId || !isAdded()) return;
            handleFailure(error);
        });
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
                    if (newCustomers.size() < limit) hasMoreData = false;
                }
            } else {
                hasMoreData = false;
            }
        } else {
            hasMoreData = false;
            String message = errorMessage(response, "Unable to load customers");
            Log.e(TAG, "Error loading customers: " + message);
            Toasty.error(requireContext(), message, Toasty.LENGTH_LONG, true).show();
        }
    }

    private void handleFailure(Throwable t) {
        isLoading = false;
        if (loadProgress != null) loadProgress.setVisibility(View.GONE);
        Log.e(TAG, "Network call failed", t);
        LogManager.logError(appContext, TAG, "Network call failed", t);
        Toasty.error(requireContext(), "Error connecting to server",
                Toasty.LENGTH_SHORT, true).show();
    }

    private void createCustomer() {
        if (isCreatingCustomer) return;
        String name = etCustomerName.getText().toString().trim();
        String phone = etCustomerPhone.getText().toString().trim();
        String email = etCustomerEmail.getText().toString().trim();
        String address = etCustomerAddress.getText().toString().trim();
        if (name.isEmpty()) {
            Toasty.warning(requireContext(), "Customer name is required",
                    Toasty.LENGTH_SHORT).show();
            return;
        }
        String tempCode = "A" + (System.currentTimeMillis() / 10000L);
        if (tempCode.length() > 10) tempCode = tempCode.substring(0, 10);
        Map<String, Object> payload = new HashMap<>();
        payload.put("CustomerCode", tempCode);
        payload.put("CustomerName", name);
        payload.put("Address1", address);
        payload.put("City", "Nairobi");
        payload.put("Country", "Kenya");
        payload.put("Phone", phone);
        payload.put("Email", email);
        payload.put("CreditDays", 30);
        payload.put("CreditAmount", 50000);
        payload.put("OpeningBalance", 0);
        payload.put("SubRouteCode", "DEFAULT");
        payload.put("WHTaxApplicable", 0);
        payload.put("CreatedBy", "api");
        setCreatingCustomer(true);
        withApi(api -> {
            if (!isAdded()) return;
            api.createCustomer("create", payload).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<Map<String, Object>> call,
                                        @NonNull Response<Map<String, Object>> response) {
                    if (!isAdded()) return;
                    setCreatingCustomer(false);
                    handleCreateResponse(response);
                }

                @Override
                public void onFailure(@NonNull Call<Map<String, Object>> call,
                                        @NonNull Throwable t) {
                    if (call.isCanceled() || !isAdded()) return;
                    setCreatingCustomer(false);
                    LogManager.logError(appContext, TAG, "Network call failed", t);
                    Log.e(TAG, "Network call failed", t);
                    Toasty.error(requireContext(), "Network error",
                            Toasty.LENGTH_SHORT, true).show();
                }
            });
        }, error -> {
            if (!isAdded()) return;
            setCreatingCustomer(false);
            Log.e(TAG, "Unable to create API client", error);
            Toasty.error(requireContext(), "Network error", Toasty.LENGTH_SHORT,
                    true).show();
        });
    }

    private void handleCreateResponse(Response<Map<String, Object>> response) {
        try {
            if (response.isSuccessful() && response.body() != null) {
                Map<String, Object> body = response.body();
                Object msg = body.get("message");
                String message = msg != null ? msg.toString() : "Unknown error";
                boolean success = Boolean.parseBoolean(String.valueOf(body.get("success")));
                if (success) {
                    Toasty.success(requireContext(), message, Toasty.LENGTH_SHORT, true).show();
                } else {
                    Toasty.error(requireContext(), message, Toasty.LENGTH_LONG, true).show();
                }
            } else {
                String message = errorMessage(response, "Unknown error");
                Log.e(TAG, "Error creating customer: " + message);
                Toasty.error(requireContext(), message, Toasty.LENGTH_LONG, true).show();
            }
        } catch (Exception e) {
            LogManager.logError(appContext, TAG, "Error parsing response", e);
            Log.e(TAG, "Error parsing response", e);
            Toasty.error(requireContext(), "Parsing error",
                    Toasty.LENGTH_SHORT, true).show();
        }
    }

    private void setCreatingCustomer(boolean creating) {
        isCreatingCustomer = creating;
        if (btnCreateCustomer != null) btnCreateCustomer.setEnabled(!creating);
    }

    private void withApi(@NonNull Consumer<ApiInterface> onReady,
                            @NonNull Consumer<Throwable> onError) {
        final Context context = appContext;
        try {
            ioExecutor.execute(() -> {
                try {
                    ApiInterface api = ApiClient.getApi(context);
                    mainHandler.post(() -> onReady.accept(api));
                } catch (Exception e) {
                    mainHandler.post(() -> onError.accept(e));
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "withApi ignored: fragment destroyed", e);
        }
    }

    private String errorMessage(Response<?> response, String fallback) {
        ResponseBody errorBody = response.errorBody();
        if (errorBody == null) return "Server error: " + response.code();
        try (ResponseBody body = errorBody) {
            JSONObject json = new JSONObject(body.string());
            if (json.has("message")) return json.getString("message");
        } catch (Exception e) {
            LogManager.logError(appContext, TAG, "Error parsing errorBody", e);
        }
        return fallback;
    }

    private void cancelPendingSearch() {
        if (searchRunnable != null) {
            mainHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }

    private void cancelCustomerCall() {
        if (customerCall != null) {
            customerCall.cancel();
            customerCall = null;
        }
    }
}