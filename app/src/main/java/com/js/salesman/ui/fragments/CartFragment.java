package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.js.salesman.R;
import com.js.salesman.adapters.CartAdapter;
import com.js.salesman.utils.Db;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import es.dmoral.toasty.Toasty;

public class CartFragment extends Fragment implements CartAdapter.OnCartItemChangeListener {

    private RecyclerView cartRecycler;
    private TextView tvGrandTotal;
    private Db db;
    private List<HashMap<String, String>> cartItems;

    public CartFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);

        db = new Db(requireContext());
        cartRecycler = view.findViewById(R.id.cartRecycler);
        tvGrandTotal = view.findViewById(R.id.tvGrandTotal);
        ImageView btnBack = view.findViewById(R.id.btnBack);
        ImageView btnClearCart = view.findViewById(R.id.btnClearCart);
        MaterialButton btnCheckout = view.findViewById(R.id.btnCheckout);

        cartRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        loadCart();

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        
        btnClearCart.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Clear Cart")
                .setMessage("Are you sure you want to remove all items from the cart?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    db.clearCart();
                    loadCart();
                    requireActivity().invalidateOptionsMenu();
                    requireActivity().getSupportFragmentManager().popBackStack();
                })
                .setNegativeButton("No", null)
                .show());

        btnCheckout.setOnClickListener(v -> {
            if (cartItems == null || cartItems.isEmpty()) {
                Toasty.warning(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show();
            } else {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new CheckoutFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        return view;
    }

    private void loadCart() {
        cartItems = db.getCartItems();
        CartAdapter adapter = new CartAdapter(cartItems, this);
        cartRecycler.setAdapter(adapter);
        updateGrandTotal();
    }

    private void updateGrandTotal() {
        double total = 0;
        for (HashMap<String, String> item : cartItems) {
            double price = Double.parseDouble(Objects.requireNonNull(item.get("unit_price")));
            int qty = Integer.parseInt(Objects.requireNonNull(item.get("quantity")));
            total += (price * qty);
        }
        tvGrandTotal.setText(String.format(Locale.getDefault(), "KES %.2f", total));
    }

    @Override
    public void onQuantityChanged(String productCode, int newQuantity) {
        db.updateCartQuantity(productCode, newQuantity);
        loadCart();
        requireActivity().invalidateOptionsMenu();
    }

    @Override
    public void onItemRemoved(String productCode) {
        db.deleteCartItem(productCode);
        loadCart();
        requireActivity().invalidateOptionsMenu();
    }
}
