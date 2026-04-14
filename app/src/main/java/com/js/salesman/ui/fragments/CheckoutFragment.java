package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.js.salesman.session.SessionManager;
import com.js.salesman.utils.Db;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

import es.dmoral.toasty.Toasty;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckoutFragment extends Fragment {

    private TextView tvSelectedCustomer;
    private EditText etCustomerName, etCustomerPhone, etCustomerEmail, etCustomerAddress;
    private TextView tvOrderSummary;
    private Db db;
    private Customer selectedCustomer;

    private int offset = 0;
    private final int limit = 20;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private String currentSearchQuery = "";
    private CustomerSelectAdapter customerAdapter;
    private ProgressBar loadProgress;
    private Timer searchTimer;

    public CheckoutFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_checkout, container, false);
        db = new Db(requireContext());
        tvSelectedCustomer = view.findViewById(R.id.tvSelectedCustomer);
        etCustomerName = view.findViewById(R.id.etCustomerName);
        etCustomerPhone = view.findViewById(R.id.etCustomerPhone);
        etCustomerEmail = view.findViewById(R.id.etCustomerEmail);
        etCustomerAddress = view.findViewById(R.id.etCustomerAddress);
        tvOrderSummary = view.findViewById(R.id.tvOrderSummary);
        ImageView btnBack = view.findViewById(R.id.btnBack);
        MaterialButton btnCreateCustomer = view.findViewById(R.id.btnCreateCustomer);
        MaterialButton btnSubmitOrder = view.findViewById(R.id.btnSubmitOrder);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        tvSelectedCustomer.setOnClickListener(v -> showCustomerSelectionDialog());
        updateOrderSummary();
        btnCreateCustomer.setOnClickListener(v -> createCustomer());
        btnSubmitOrder.setOnClickListener(v -> submitOrder());
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
            tvSelectedCustomer.setText(customer.toString());
            tvSelectedCustomer.setTextColor(ContextCompat.getColor(requireActivity(), R.color.black));
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
        ApiInterface api = ApiClient.getClient(getActivity()).create(ApiInterface.class);
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
                    Log.e("CheckoutFragment", "Error parsing errorBody", e);
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
        Log.e("CheckoutFragment", "Network call failed", t);
        if (isAdded()) {
            Toasty.error(requireContext(), "Error connecting to server", Toast.LENGTH_SHORT).show();
        }
    }

    private void createCustomer() {
        String name = etCustomerName.getText().toString().trim();
        String phone = etCustomerPhone.getText().toString().trim();
        String email = etCustomerEmail.getText().toString().trim();
        String address = etCustomerAddress.getText().toString().trim();
        if (name.isEmpty()) {
            Toasty.warning(requireContext(), "Customer name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        // Generate a 10-character code
        String tempCode = "A" + (System.currentTimeMillis() / 10000L);
        if (tempCode.length() > 10) tempCode = tempCode.substring(0, 10);
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

        ApiInterface api = ApiClient.getClient(getActivity()).create(ApiInterface.class);
        api.createCustomer("create", payload).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call,
                                   @NonNull Response<Map<String, Object>> response) {
                String message = "Unknown error";
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> body = response.body();
                        if (body.containsKey("message")) {
                            message = Objects.requireNonNull(body.get("message")).toString();
                        }
                        boolean success = false;
                        if (body.containsKey("success")) {
                            success = Boolean.parseBoolean(
                                    Objects.requireNonNull(body.get("success")).toString()
                            );
                        }
                        if (success) {
                            Toasty.success(requireContext(), message, Toast.LENGTH_SHORT).show();
                        } else {
                            Toasty.error(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        ResponseBody errorBody = response.errorBody();
                        if (errorBody != null) {
                            try (ResponseBody body = errorBody) {
                                String errorJson = body.string();
                                JSONObject json = new JSONObject(errorJson);
                                if (json.has("message")) {
                                    message = json.getString("message");
                                }
                            } catch (Exception e) {
                                Log.e("CheckoutFragment", "Error parsing errorBody", e);
                            }
                        } else {
                            message = "Server error: " + response.code();
                        }
                        Toasty.error(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("CheckoutFragment", "createCustomer parse error", e);
                    Toasty.error(requireContext(), "Parsing error", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call,
                                  @NonNull Throwable t) {
                Toasty.error(requireContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateOrderSummary() {
        List<HashMap<String, String>> cartItems = db.getCartItems();
        double total = 0;
        for (HashMap<String, String> item : cartItems) {
            total += Double.parseDouble(Objects.requireNonNull(item.get("unit_price")))
                    * Integer.parseInt(Objects.requireNonNull(item.get("quantity")));
        }
        tvOrderSummary.setText(getString(R.string.order_summary_val, cartItems.size(), total));
    }

    private void submitOrder() {
        if (selectedCustomer == null) {
            Toasty.warning(requireContext(), "Select customer", Toast.LENGTH_SHORT).show();
            return;
        }
        List<HashMap<String, String>> cartItems = db.getCartItems();
        if (cartItems.isEmpty()) {
            Toasty.warning(requireContext(), "Cart empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        SessionManager session = new SessionManager(requireContext());
        payload.put("sales_man_id", session.getUserId());
        payload.put("CustomerCode", selectedCustomer.getSrNo());
        payload.put("OrderDate",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date()));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (HashMap<String, String> item : cartItems) {
            Map<String, Object> line = new HashMap<>();
            line.put("ProductCode", item.get("product_code"));
            line.put("Quantity", Integer.parseInt(Objects.requireNonNull(item.get("quantity"))));
            line.put("UnitPrice", Double.parseDouble(Objects.requireNonNull(item.get("unit_price"))));
            lines.add(line);
        }
        payload.put("Lines", lines);
        ApiInterface api = ApiClient.getClient(getActivity()).create(ApiInterface.class);
        api.createOrder("create", payload).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call,
                                   @NonNull Response<Map<String, Object>> response) {
                Log.d("CREATE_ORDER", "Response: " + response.body());
                String message = "Unknown error";
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> body = response.body();
                        if (body.containsKey("message")) {
                            message = Objects.requireNonNull(body.get("message")).toString();
                        }
                        boolean success = false;
                        if (body.containsKey("success")) {
                            success = Boolean.parseBoolean(Objects.requireNonNull(body.get("success")).toString());
                        }
                        if (success) {
                            db.clearCart();
                            Toasty.success(requireContext(), message, Toast.LENGTH_LONG).show();
                            requireActivity().invalidateOptionsMenu();
                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.fragment_container, new ProductFragment())
                                    .commit();
                        } else {
                            Toasty.error(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        ResponseBody errorBody = response.errorBody();
                        if (errorBody != null) {
                            try (ResponseBody body = errorBody) {
                                String errorJson = body.string();
                                JSONObject json = new JSONObject(errorJson);
                                if (json.has("message")) {
                                    message = json.getString("message");
                                }
                            } catch (Exception e) {
                                Log.e("CheckoutFragment", "Error parsing errorBody", e);
                            }
                        } else {
                            message = "Server error: " + response.code();
                        }
                        Toasty.error(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("CheckoutFragment", "Error parsing response", e);
                    Toasty.error(requireContext(), "Parsing error", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call,
                                  @NonNull Throwable t) {
                Toasty.error(requireContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchTimer != null) searchTimer.cancel();
    }
}