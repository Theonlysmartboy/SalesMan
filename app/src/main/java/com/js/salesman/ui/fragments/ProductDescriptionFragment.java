package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.js.salesman.R;
import com.js.salesman.models.Product;
import com.js.salesman.api.client.ApiClient;
import com.js.salesman.api.service.ApiService;
import com.js.salesman.models.ProductResponse;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductDescriptionFragment extends Fragment {

    private String action;
    private String code;

    private ImageView productImage, btnBack;
    private TextView productName, productCode, productUnit, productPrice, productStock;

    public ProductDescriptionFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_description, container, false);

        // Get arguments from adapter
        Bundle args = getArguments();
        if (args != null) {
            action = args.getString("action");
            code = args.getString("code");
            android.util.Log.d("PRODUCT_DEBUG", "Action: " + action);
            android.util.Log.d("PRODUCT_DEBUG", "Code: " + code);
        }

        // Bind views
        productImage = view.findViewById(R.id.productImage);
        productName = view.findViewById(R.id.productName);
        productCode = view.findViewById(R.id.productCode);
        productUnit = view.findViewById(R.id.productUnit);
        productPrice = view.findViewById(R.id.productPrice);
        productStock = view.findViewById(R.id.productStock);
        btnBack = view.findViewById(R.id.btnBack);
        Button addToOrderButton = view.findViewById(R.id.addToOrderButton);
        // Load product info from API
        if (action != null && code != null) {
            fetchProductDetails(action, code);
        }
        addToOrderButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Added " + productName.getText() + " to order", Toast.LENGTH_SHORT).show();
            // TODO: Add product to order list logic here
        });
            btnBack.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        return view;
    }

    private void fetchProductDetails(String action, String code) {
        ApiService api = ApiClient.getClient(getActivity()).create(ApiService.class);
        Call<ProductResponse> call = api.getProductDetails(action, code);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductResponse> call,
                                @NonNull Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Product product = response.body().getData();
                    productName.setText(product.getProductName());
                    productCode.setText(requireContext().getString(R.string.product_code_format, product.getProductCode()));
                    productUnit.setText(product.getProductUnit());
                    productPrice.setText(requireContext().getString(R.string.product_unit_price, product.getProduct_Selling_Price(), product.getProductUnit()));
                    productStock.setText(requireContext().getString(R.string.product_stock, product.getStockQty()));
                    String img = product.getImg_src();
                    if (img == null || img.isEmpty()) {
                        productImage.setImageResource(R.drawable.ic_product_placeholder);
                    } else {
                        String imageUrl =
                                ApiClient.getBaseUrl() + "assets/uploads/images/" + img;
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_product_placeholder)
                                .error(R.drawable.ic_product_placeholder)
                                .into(productImage);
                    }
                }else {
                    Log.d("ProductDescriptionFragment", "Response not successful: " + response.code());
                    Toasty.warning(requireContext(), "Error fetching product details", Toast.LENGTH_SHORT, true).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ProductResponse> call, @NonNull Throwable t) {
                Log.d("ProductDescriptionFragment", "Load Error: " + t.getMessage());
                Toasty.error(requireContext(), "Error fetching product details", Toast.LENGTH_SHORT, true).show();
            }
        });
    }
}