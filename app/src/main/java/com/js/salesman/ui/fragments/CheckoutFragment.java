package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.api.client.ApiClient;
import com.js.salesman.api.service.ApiService;
import com.js.salesman.models.Customer;
import com.js.salesman.utils.Db;

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

public class CheckoutFragment extends Fragment {

    private AutoCompleteTextView customerAutoComplete;
    private EditText etCustomerName, etCustomerPhone, etCustomerEmail, etCustomerAddress;
    private TextView tvOrderSummary;
    private Db db;
    private final List<Customer> customers = new ArrayList<>();
    private ArrayAdapter<Customer> customerAdapter;
    private Customer selectedCustomer;
    private Timer timer;

    public CheckoutFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_checkout, container, false);

        db = new Db(requireContext());
        customerAutoComplete = view.findViewById(R.id.customerAutoComplete);
        etCustomerName = view.findViewById(R.id.etCustomerName);
        etCustomerPhone = view.findViewById(R.id.etCustomerPhone);
        etCustomerEmail = view.findViewById(R.id.etCustomerEmail);
        etCustomerAddress = view.findViewById(R.id.etCustomerAddress);
        tvOrderSummary = view.findViewById(R.id.tvOrderSummary);
        ImageView btnBack = view.findViewById(R.id.btnBack);
        MaterialButton btnCreateCustomer = view.findViewById(R.id.btnCreateCustomer);
        MaterialButton btnSubmitOrder = view.findViewById(R.id.btnSubmitOrder);

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        setupAutoComplete();
        updateOrderSummary();

        btnCreateCustomer.setOnClickListener(v -> createCustomer());
        btnSubmitOrder.setOnClickListener(v -> submitOrder());

        return view;
    }

    private void setupAutoComplete() {
        customerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, customers);
        customerAutoComplete.setAdapter(customerAdapter);

        // Make it behave like a spinner (show dropdown on click)
        customerAutoComplete.setOnClickListener(v -> customerAutoComplete.showDropDown());

        customerAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            selectedCustomer = customerAdapter.getItem(position);
            customerAutoComplete.setError(null);
        });

        customerAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (timer != null) timer.cancel();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 1) {
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> searchCustomers(s.toString()));
                            }
                        }
                    }, 500); // 500ms delay
                }
            }
        });
    }

    private void searchCustomers(String query) {
        ApiService api = ApiClient.getClient(getActivity()).create(ApiService.class);
        api.searchCustomers("search", query).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) response.body().get("data");
                    if (data != null) {
                        customers.clear();
                        for (Map<String, Object> item : data) {
                            String srNo = String.valueOf(item.get("SrNo"));
                            String code = (String) item.get("CustomerCode");
                            String name = (String) item.get("CustomerName");
                            customers.add(new Customer(srNo, code, name));
                        }
                        customerAdapter.notifyDataSetChanged();
                        if (customerAutoComplete.isFocused()) {
                            customerAutoComplete.showDropDown();
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                Log.e("CheckoutFragment", "Search failed: " + t.getMessage());
            }
        });
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
        // Ensure CustomerCode length is 10
        String tempCode = "C" + (System.currentTimeMillis() / 10000L); // Approx 10 chars
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

        ApiService api = ApiClient.getClient(getActivity()).create(ApiService.class);
        api.createCustomer("create", payload).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toasty.success(requireContext(), "Customer created successfully", Toast.LENGTH_SHORT).show();
                    
                    Map<String, Object> responseData = (Map<String, Object>) response.body().get("data");
                    String srNo = (responseData != null && responseData.get("SrNo") != null) ? 
                            String.valueOf(responseData.get("SrNo")) : "";
                    
                    selectedCustomer = new Customer(srNo, (String) payload.get("CustomerCode"), name);
                    customerAutoComplete.setText(selectedCustomer.toString(), false);
                    customerAutoComplete.setError(null);

                    etCustomerName.setText("");
                    etCustomerPhone.setText("");
                    etCustomerEmail.setText("");
                    etCustomerAddress.setText("");
                } else {
                    Toasty.error(requireContext(), "Failed to create customer", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                Toasty.error(requireContext(), "Error connecting to server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateOrderSummary() {
        List<HashMap<String, String>> cartItems = db.getCartItems();
        double total = 0;
        for (HashMap<String, String> item : cartItems) {
            double price = Double.parseDouble(Objects.requireNonNull(item.get("unit_price")));
            int qty = Integer.parseInt(Objects.requireNonNull(item.get("quantity")));
            total += (price * qty);
        }
        tvOrderSummary.setText(String.format(Locale.getDefault(), "Items: %d\nTotal: KES %.2f", cartItems.size(), total));
    }

    private void submitOrder() {
        if (selectedCustomer == null) {
            customerAutoComplete.setError("Please select a customer");
            Toasty.warning(requireContext(), "Please select a customer", Toast.LENGTH_SHORT).show();
            return;
        }

        List<HashMap<String, String>> cartItems = db.getCartItems();
        if (cartItems.isEmpty()) {
            Toasty.warning(requireContext(), "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("CustomerCode", selectedCustomer.getSrNo());
        payload.put("OrderDate", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));

        List<Map<String, Object>> lines = new ArrayList<>();
        for (HashMap<String, String> item : cartItems) {
            Map<String, Object> line = new HashMap<>();
            line.put("ProductCode", item.get("product_code"));
            line.put("Quantity", Integer.parseInt(Objects.requireNonNull(item.get("quantity"))));
            line.put("UnitPrice", Double.parseDouble(Objects.requireNonNull(item.get("unit_price"))));
            lines.add(line);
        }
        payload.put("Lines", lines);

        ApiService api = ApiClient.getClient(getActivity()).create(ApiService.class);
        api.createOrder("create", payload).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    db.clearCart();
                    Toasty.success(requireContext(), "Order submitted successfully", Toast.LENGTH_LONG).show();
                    requireActivity().invalidateOptionsMenu();
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new ProductFragment())
                            .commit();
                } else {
                    Toasty.error(requireContext(), "Order submission failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                Toasty.error(requireContext(), "Failed to submit order", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
