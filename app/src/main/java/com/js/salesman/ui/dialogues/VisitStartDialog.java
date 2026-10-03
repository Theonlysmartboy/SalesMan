package com.js.salesman.ui.dialogues;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.js.salesman.R;
import com.js.salesman.adapters.CustomerSelectAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.ApiResponse;
import com.js.salesman.models.Customer;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.CustomerVisitRepository;
import com.js.salesman.ui.activities.BaseActivity;
import com.js.salesman.utils.LocationUtils;
import com.js.salesman.utils.managers.SessionManager;

import java.util.List;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VisitStartDialog extends DialogFragment {

    private CustomerVisitRepository visitRepository;
    private SessionManager sessionManager;
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private boolean actionInProgress = false;

    private MaterialButton btnExistingCustomer;
    private MaterialButton btnNewCustomer;
    private MaterialButton btnScheduledVisit;
    private MaterialButton btnLogoutApp;
    private ProgressBar progressBar;

    public interface OnVisitStartedListener {
        void onVisitStarted(CustomerVisit visit);
    }

    private OnVisitStartedListener listener;

    public void setOnVisitStartedListener(OnVisitStartedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_start_visit, container, false);
        visitRepository = CustomerVisitRepository.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        btnExistingCustomer = view.findViewById(R.id.btnExistingCustomer);
        btnNewCustomer = view.findViewById(R.id.btnNewCustomer);
        btnScheduledVisit = view.findViewById(R.id.btnScheduledVisit);
        btnLogoutApp = view.findViewById(R.id.btnLogoutApp);
        progressBar = view.findViewById(R.id.progressBar);

        obtainLocation();

        btnExistingCustomer.setOnClickListener(v -> {
            if (!actionInProgress) {
                showExistingCustomerFlow();
            }
        });
        btnNewCustomer.setOnClickListener(v -> {
            if (!actionInProgress) {
                showNewCustomerFlow();
            }
        });
        btnScheduledVisit.setOnClickListener(v -> {
            if (!actionInProgress) {
                showScheduledVisitFlow();
            }
        });
        btnLogoutApp.setOnClickListener(v -> {
            if (!actionInProgress) {
                dismiss();
                if (getActivity() instanceof BaseActivity) {
                    ((BaseActivity) getActivity()).checkSessionAndIdle();
                }
            }
        });
        return view;
    }

    private void setUiEnabled(boolean enabled) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (btnExistingCustomer != null) btnExistingCustomer.setEnabled(enabled);
            if (btnNewCustomer != null) btnNewCustomer.setEnabled(enabled);
            if (btnScheduledVisit != null) btnScheduledVisit.setEnabled(enabled);
            if (btnLogoutApp != null) btnLogoutApp.setEnabled(enabled);
            if (progressBar != null) {
                progressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void obtainLocation() {
        LocationUtils.getUserLocation(requireContext(), requireActivity(),
                new LocationUtils.LocationResultCallback() {
            @Override
            public void onSuccess(double lat, double lng) {
                currentLat = lat;
                currentLng = lng;
                sessionManager.saveLastLocation(lat, lng);
            }
            @Override
            public void onFailure(String error) {
                Double cachedLat = sessionManager.getCachedLat();
                Double cachedLng = sessionManager.getCachedLng();
                if (cachedLat != null && cachedLng != null) {
                    currentLat = cachedLat;
                    currentLng = cachedLng;
                }
            }
        });
    }

    private void showExistingCustomerFlow() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_customer_select, null);
        dialog.setContentView(view);
        RecyclerView recyclerView = view.findViewById(R.id.customerSelectRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        CustomerSelectAdapter adapter = new CustomerSelectAdapter(customer -> {
            dialog.dismiss();
            confirmAndStartVisit(customer.getSrNo(), "REGISTERED", customer.getCustomerName());
        });
        recyclerView.setAdapter(adapter);
        ApiInterface api = ApiClient.getClient(requireActivity()).create(ApiInterface.class);
        api.syncCustomers("sync", "2010-01-01", 100, 0).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Customer>> call,
                                @NonNull Response<ApiResponse<Customer>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Customer> list = response.body().getData();
                    if (list != null) adapter.addCustomers(list);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Customer>> call, @NonNull Throwable t) {
                Toasty.error(requireContext(), "Offline: selecting from available cache.",
                        Toasty.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void confirmAndStartVisit(String customerId, String customerType, String businessName) {
        if (actionInProgress) return;
        actionInProgress = true;
        setUiEnabled(false);

        if (currentLat == 0.0 || currentLng == 0.0) {
            LocationUtils.getUserLocation(requireContext(), requireActivity(),
                    new LocationUtils.LocationResultCallback() {
                @Override
                public void onSuccess(double lat, double lng) {
                    currentLat = lat;
                    currentLng = lng;
                    sessionManager.saveLastLocation(lat, lng);
                    executeStartVisit(customerId, customerType, businessName);
                }

                @Override
                public void onFailure(String error) {
                    Double cachedLat = sessionManager.getCachedLat();
                    Double cachedLng = sessionManager.getCachedLng();
                    if (cachedLat != null && cachedLng != null) {
                        currentLat = cachedLat;
                        currentLng = cachedLng;
                        executeStartVisit(customerId, customerType, businessName);
                    } else {
                        actionInProgress = false;
                        setUiEnabled(true);
                        Toasty.error(requireContext(), "GPS location is required to start a visit.", Toasty.LENGTH_LONG).show();
                    }
                }
            });
            return;
        }

        executeStartVisit(customerId, customerType, businessName);
    }

    private void executeStartVisit(String customerId, String customerType, String businessName) {
        String userId = sessionManager.getUserId();
        visitRepository.startVisit(userId, customerId, customerType, businessName, currentLat,
                currentLng, "GPS_DETECTED", "Started visit",
                new CustomerVisitRepository.VisitCallback() {
            @Override
            public void onSuccess(CustomerVisit visit) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toasty.success(requireContext(), "Visit started successfully.",
                                Toasty.LENGTH_SHORT).show();
                        if (listener != null) listener.onVisitStarted(visit);
                        dismiss();
                    });
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        actionInProgress = false;
                        setUiEnabled(true);
                        Toasty.error(requireContext(), message, Toasty.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void showNewCustomerFlow() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText etName = view.findViewById(R.id.editText);
        etName.setHint("Business / Customer Name");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Customer Walk-in").setView(view)
                .setPositiveButton("Start Visit", (d, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toasty.warning(requireContext(), "Business Name is required.",
                                Toasty.LENGTH_SHORT).show();
                        return;
                    }
                    confirmAndStartVisit(null, "NEW", name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showScheduledVisitFlow() {
        if (actionInProgress) return;
        String userId = sessionManager.getUserId();
        if (userId == null) return;
        visitRepository.getScheduledVisits(userId, new CustomerVisitRepository.VisitListCallback() {
            @Override
            public void onSuccess(List<CustomerVisit> visits) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (visits == null || visits.isEmpty()) {
                        Toasty.info(requireContext(), "No scheduled visits found.",
                                Toasty.LENGTH_SHORT).show();
                        return;
                    }
                    String[] items = new String[visits.size()];
                    for (int i = 0; i < visits.size(); i++) {
                        CustomerVisit v = visits.get(i);
                        String displayId = (v.serverId != null && !v.serverId.isEmpty()) ? v.serverId : v.visitId;
                        items[i] = (v.businessName != null ? v.businessName : "Scheduled Visit") +
                                " (#" + displayId + ")";
                    }
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Select Scheduled Visit")
                            .setItems(items, (d, which) -> {
                                CustomerVisit selected = visits.get(which);
                                executeStartScheduledVisit(selected.visitId);
                            })
                            .show();
                });
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> Toasty.error(requireContext(), message,
                            Toasty.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void executeStartScheduledVisit(String visitId) {
        if (actionInProgress) return;
        actionInProgress = true;
        setUiEnabled(false);

        visitRepository.startScheduledVisit(visitId, currentLat, currentLng,
                new CustomerVisitRepository.VisitCallback() {
            @Override
            public void onSuccess(CustomerVisit visit) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toasty.success(requireContext(), "Scheduled visit started.", Toasty.LENGTH_SHORT).show();
                        if (listener != null) listener.onVisitStarted(visit);
                        dismiss();
                    });
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        actionInProgress = false;
                        setUiEnabled(true);
                        Toasty.error(requireContext(), message, Toasty.LENGTH_LONG).show();
                    });
                }
            }
        });
    }
}
