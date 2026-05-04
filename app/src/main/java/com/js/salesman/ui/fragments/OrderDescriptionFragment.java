package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.adapters.OrderLineAdapter;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.OrderDetails;
import com.js.salesman.models.OrderDetailsResponse;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderDescriptionFragment extends Fragment {

    private static final String ARG_ORDER_NO = "order_no";
    private String orderNo;
    private ApiInterface apiInterface;
    private OrderLineAdapter adapter;
    private FrameLayout loadingLayout;
    private TextView tvOrderNo, tvCustomerName, tvOrderDate, tvStatus, tvTotalAmount;

    public static OrderDescriptionFragment newInstance(String orderNo) {
        OrderDescriptionFragment fragment = new OrderDescriptionFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORDER_NO, orderNo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            orderNo = getArguments().getString(ARG_ORDER_NO);
        }
        apiInterface = ApiClient.getApi(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_order_description, container, false);

        tvOrderNo = view.findViewById(R.id.tvDetailOrderNo);
        tvCustomerName = view.findViewById(R.id.tvDetailCustomerName);
        tvOrderDate = view.findViewById(R.id.tvDetailOrderDate);
        tvStatus = view.findViewById(R.id.tvDetailStatus);
        tvTotalAmount = view.findViewById(R.id.tvDetailTotalAmount);
        loadingLayout = view.findViewById(R.id.loadingLayout);

        RecyclerView recyclerView = view.findViewById(R.id.orderLineRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrderLineAdapter();
        recyclerView.setAdapter(adapter);

        fetchOrderDetails();

        return view;
    }

    private void fetchOrderDetails() {
        if (orderNo == null || orderNo.isEmpty()) {
            Toasty.error(requireContext(), "Invalid Order Number", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingLayout.setVisibility(View.VISIBLE);
        apiInterface.getOrderDetails("get", orderNo).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<OrderDetailsResponse> call, @NonNull Response<OrderDetailsResponse> response) {
                loadingLayout.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    OrderDetailsResponse detailsResponse = response.body();
                    if (detailsResponse.isSuccess() && detailsResponse.getData() != null) {
                        displayOrderDetails(detailsResponse.getData());
                    } else {
                        String msg = detailsResponse.getMessage() != null ? detailsResponse.getMessage() : "Failed to fetch details";
                        Toasty.error(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toasty.error(requireContext(), "Server error", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<OrderDetailsResponse> call, @NonNull Throwable t) {
                loadingLayout.setVisibility(View.GONE);
                Toasty.error(requireContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayOrderDetails(OrderDetails data) {
        tvOrderNo.setText(getString(R.string.order_number, data.getOrderNo()));
        tvCustomerName.setText(data.getCustomerName());
        tvOrderDate.setText(data.getOrderDate());
        tvStatus.setText(data.getStatus());
        tvTotalAmount.setText(getString(R.string.currency_kes, data.getTotalAmount()));

        if (data.getLines() != null) {
            adapter.setLines(data.getLines());
        }
    }
}
