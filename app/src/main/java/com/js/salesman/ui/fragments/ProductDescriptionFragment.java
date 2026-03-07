package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.adapters.AlternateUnitAdapter;
import com.js.salesman.models.Product;
import com.js.salesman.api.client.ApiClient;
import com.js.salesman.api.service.ApiService;
import com.js.salesman.models.ProductResponse;
import com.js.salesman.ui.views.GestureScrollView;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductDescriptionFragment extends Fragment {
    private String action;
    private String code;
    private ImageView productImage;
    private TextView productName, productCode, productPrice, productStock;
    Product product;
    private RecyclerView alternateUnitsRecycler;
    private GestureDetector gestureDetector;

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
        productPrice = view.findViewById(R.id.productPrice);
        productStock = view.findViewById(R.id.productStock);
        ImageView btnBack = view.findViewById(R.id.btnBack);
        MaterialButton addToOrderButton = view.findViewById(R.id.addToOrderButton);
        // Load product info from API
        if (action != null && code != null) {
            fetchProductDetails(action, code);
        }
        addToOrderButton.setOnClickListener(v -> {
            Toasty.info(requireActivity(), "Coming soon", Toasty.LENGTH_SHORT, true).show();
            /*showQuantityDialog(product);
            /requireActivity().invalidateOptionsMenu();*/
        });
            btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });
        alternateUnitsRecycler = view.findViewById(R.id.alternateUnitsRecycler);
        alternateUnitsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        GestureScrollView scrollView = view.findViewById(R.id.scrollView);
        scrollView.setGestureDetector(gestureDetector);
        gestureDetector = new GestureDetector(requireContext(), new GestureListener());
        view.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        });
        return view;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            return true;
        }
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float velocityX, float velocityY) {
            assert e1 != null;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        swipeRight();
                    } else {
                        swipeLeft();
                    }
                    return true;
                }
            }
            return false;
        }
    }

    private void swipeLeft() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ProductFragment())
                .addToBackStack(null)
                .commit();
    }

    private void swipeRight() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .addToBackStack(null)
                .commit();
    }

    private void fetchProductDetails(String action, String code) {
        ApiService api = ApiClient.getClient(getActivity()).create(ApiService.class);
        Call<ProductResponse> call = api.getProductDetails(action, code);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductResponse> call,
                                @NonNull Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    product = response.body().getData();
                    productName.setText(product.getProductName());
                    productCode.setText(requireContext().getString(R.string.product_code_format, product.getProductCode()));
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
                    if(product.getAlternate_units() != null && !product.getAlternate_units().isEmpty()){
                        AlternateUnitAdapter adapter =
                                new AlternateUnitAdapter(product.getAlternate_units());
                        alternateUnitsRecycler.setAdapter(adapter);
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

    private void showQuantityDialog(Product product) {
        final EditText qtyInput = new EditText(getContext());
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setHint("Quantity");
        new AlertDialog.Builder(requireContext())
                .setTitle("Select Quantity")
                .setView(qtyInput)
                .setPositiveButton("Add", (dialog, which) -> {
                    int qty = Integer.parseInt(qtyInput.getText().toString());
                    //CartManager.addToCart(requireContext(), product, qty);
                    Toasty.success(requireContext(),
                            "Added to cart",
                            Toast.LENGTH_SHORT,
                            true).show();
                    //updateCartBadge();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}