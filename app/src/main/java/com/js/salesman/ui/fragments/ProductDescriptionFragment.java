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
import com.js.salesman.clients.ApiClient;
import com.js.salesman.interfaces.ApiInterface;
import com.js.salesman.models.ProductResponse;
import com.js.salesman.ui.views.GestureScrollView;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.LocationUtils;
import com.js.salesman.utils.managers.SessionManager;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

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
    private Db db;
    private SessionManager sessionManager;

    public ProductDescriptionFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_description, container, false);
        db = new Db(requireContext());
        sessionManager = new SessionManager(requireContext());
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
            if (product != null) {
                showQuantityDialog(product);
            } else {
                Toasty.warning(requireContext(), "Product details not loaded", Toast.LENGTH_SHORT).show();
            }
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
        LocationUtils.getUserLocation(requireContext(), requireActivity(), new LocationUtils.LocationResultCallback() {
            @Override
            public void onSuccess(double lat, double lng) {
                sessionManager.saveLastLocation(lat, lng);
                executeFetchProductDetails(action, code, lat, lng);
            }

            @Override
            public void onFailure(String error) {
                Double cachedLat = sessionManager.getCachedLat();
                Double cachedLng = sessionManager.getCachedLng();
                if (cachedLat != null && cachedLng != null) {
                    executeFetchProductDetails(action, code, cachedLat, cachedLng);
                } else {
                    Toasty.error(requireContext(), "GPS is required for accurate pricing. Please enable location services.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void executeFetchProductDetails(String action, String code, double lat, double lng) {
        ApiInterface api = ApiClient.getClient(requireActivity()).create(ApiInterface.class);
        Call<ProductResponse> call = api.getProductDetails(action, code, lat, lng);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProductResponse> call,
                                @NonNull Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        product = response.body().getData();
                        productName.setText(product.getProductName());
                        productCode.setText(requireContext().getString(R.string.product_code_format, product.getProductCode()));
                        productPrice.setText(requireContext().getString(R.string.product_unit_price, product.getProduct_Selling_Price(), product.getProductUnit()));
                        productStock.setText(requireContext().getString(R.string.product_stock, product.getProductQuantity()));
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
                    } else {
                        Toasty.error(requireContext(), response.body().getMessage(), Toast.LENGTH_LONG).show();
                    }
                }else {
                    Log.d("ProductDescriptionFragment", "Response not successful: " + response.code());
                    Toasty.warning(requireContext(), "Error fetching product details", Toast.LENGTH_SHORT, true).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ProductResponse> call, @NonNull Throwable t) {
                Log.d("ProductDescriptionFragment", "Load Error: " + t.getMessage());
                if (t instanceof UnknownHostException || t instanceof SocketTimeoutException) {
                    Toasty.error(requireContext(), "Unable to reach server. Check your internet connection.", Toast.LENGTH_LONG).show();
                } else {
                    Toasty.error(requireContext(), "Error fetching product details", Toast.LENGTH_SHORT, true).show();
                }
            }
        });
    }

    private void showQuantityDialog(Product product) {
        final EditText qtyInput = new EditText(getContext());
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setHint("Quantity");
        
        int existingQty = db.getProductQuantity(product.getProductCode());
        if (existingQty > 0) {
            qtyInput.setText(String.valueOf(existingQty));
        } else {
            qtyInput.setText("1");
        }
        qtyInput.setSelection(qtyInput.getText().length());

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Quantity")
                .setView(qtyInput)
                .setPositiveButton("Add", (dialog, which) -> {
                    String qtyStr = qtyInput.getText().toString();
                    if (qtyStr.isEmpty()) return;
                    int qty = Integer.parseInt(qtyStr);
                    
                    double price = 0;
                    try {
                        price = Double.parseDouble(product.getProduct_Selling_Price());
                    } catch (Exception ignored) {}

                    if (db.storeOrder(product.getProductCode(), product.getProductName(), price, qty)) {
                        Toasty.success(requireContext(), "Item added to cart", Toast.LENGTH_SHORT, true).show();
                        requireActivity().invalidateOptionsMenu();
                        showPostAddDialog();
                    } else {
                        Toasty.error(requireContext(), "Failed to add to cart", Toast.LENGTH_SHORT, true).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPostAddDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Item added to cart")
                .setMessage("What would you like to do next?")
                .setPositiveButton("Checkout", (dialog, which) -> requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new CartFragment())
                        .addToBackStack(null)
                        .commit())
                .setNegativeButton("Continue Shopping", (dialog, which) -> requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ProductFragment())
                        .commit())
                .show();
    }
}
