package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.adapters.AlternateUnitAdapter;
import com.js.salesman.models.Product;
import com.js.salesman.clients.ApiClient;
import com.js.salesman.ui.views.GestureScrollView;
import com.js.salesman.utils.LoadingHandler;
import com.js.salesman.utils.OrderHelper;
import com.js.salesman.utils.PricingHelper;
import com.js.salesman.utils.TrailingDotsLoader;
import com.js.salesman.utils.managers.SessionManager;
import com.js.salesman.models.Customer;
import com.js.salesman.viewmodels.ProductViewModel;

import java.util.Locale;

public class ProductDescriptionFragment extends Fragment {
    private String code;
    private ImageView productImage;
    private TextView productName, productCode, productPrice, productStock;
    private Product product;
    private RecyclerView alternateUnitsRecycler;
    private FrameLayout loaderOverlay;
    private TrailingDotsLoader loader;
    private GestureDetector gestureDetector;
    private String customerCategory;

    public ProductDescriptionFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_description, container,
                false);
        loaderOverlay = view.findViewById(R.id.loaderOverlay);
        loader = new TrailingDotsLoader(requireContext());
        SessionManager sessionManager = new SessionManager(requireContext());
        Customer customer = sessionManager.getSelectedCustomer();
        customerCategory = customer != null ? customer.getCategory() : null;
        Bundle args = getArguments();
        if (args != null) {
            code = args.getString("code");
        }
        productImage = view.findViewById(R.id.productImage);
        productName = view.findViewById(R.id.productName);
        productCode = view.findViewById(R.id.productCode);
        productPrice = view.findViewById(R.id.productPrice);
        productStock = view.findViewById(R.id.productStock);
        ImageView btnBack = view.findViewById(R.id.btnBack);
        MaterialButton addToOrderButton = view.findViewById(R.id.addToOrderButton);
        alternateUnitsRecycler = view.findViewById(R.id.alternateUnitsRecycler);
        alternateUnitsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        setupViewModel();
        addToOrderButton.setOnClickListener(v -> {
            if (product != null) {
                OrderHelper.addItemToOrder(this, product);
            }
        });
        btnBack.setOnClickListener(v -> requireActivity()
                        .getSupportFragmentManager().popBackStack());
        GestureScrollView scrollView = view.findViewById(R.id.scrollView);
        gestureDetector = new GestureDetector(requireContext(), new GestureListener());
        scrollView.setGestureDetector(gestureDetector);
        view.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        });
        return view;
    }

    private void setupViewModel() {
        ProductViewModel viewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        if (code != null) {
            LoadingHandler.showLoading(requireContext(), loader, loaderOverlay);
            viewModel.getProductByCode(code).observe(getViewLifecycleOwner(), p -> {
                LoadingHandler.hideLoading(loaderOverlay);
                if (p != null) {
                    this.product = p;
                    displayProductDetails(p);
                }
            });
        }
    }

    private void displayProductDetails(Product product) {
        productName.setText(product.getProductName());
        productCode.setText(requireContext().getString(R.string.product_code_format,
                product.getProductCode()));
        double price = PricingHelper.getPrice(product, customerCategory);
        productPrice.setText(requireContext().getString(R.string.product_unit_price, 
                String.format(Locale.getDefault(), "%.2f", price), 
                product.getProductUnit()));
        productStock.setText(requireContext().getString(R.string.product_stock, product.getProductQuantity()));
        String img = product.getImg_src();
        if (img == null || img.isEmpty()) {
            productImage.setImageResource(R.drawable.ic_product_placeholder);
        } else {
            String imageUrl = ApiClient.getBaseUrl() + "assets/uploads/images/" + img;
            Glide.with(requireContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .into(productImage);
        }
        if(product.getAlternate_units() != null && !product.getAlternate_units().isEmpty()){
            AlternateUnitAdapter adapter = new AlternateUnitAdapter(product.getAlternate_units());
            alternateUnitsRecycler.setAdapter(adapter);
        }
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
            if (e1 == null || e2 == null) return false;
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
        requireActivity()
                .getSupportFragmentManager()
                .popBackStack();
    }

    private void swipeRight() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .addToBackStack(null)
                .commit();
    }
}
