package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
        ImageView btnParkCart = view.findViewById(R.id.btnParkCart);
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
                })
                .setNegativeButton("No", null)
                .show());

        if (btnParkCart != null) {
            btnParkCart.setOnClickListener(v -> showParkCartDialog());
        }

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

    private void showParkCartDialog() {
        if (cartItems.isEmpty()) {
            Toasty.warning(requireContext(), "Nothing to park", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final EditText input = new EditText(requireContext());
        input.setHint("Cart Name (Optional)");
        
        new AlertDialog.Builder(requireContext())
                .setTitle("Park Entire Cart")
                .setMessage("Move all items to a suspended order?")
                .setView(input)
                .setPositiveButton("Park", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    db.moveEntireCartToParkedCart(name.isEmpty() ? null : name);
                    Toasty.success(requireContext(), "Cart Parked", Toast.LENGTH_SHORT).show();
                    loadCart();
                    requireActivity().invalidateOptionsMenu();
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    @Override
    public void onMoveToParked(String productCode) {
        List<HashMap<String, String>> carts = db.getParkedCarts();
        
        if (carts.isEmpty()) {
            db.moveSingleItemToParkedCart(productCode, db.createParkedCart("Partial Cart"));
            Toasty.success(requireContext(), "Item moved to new parked cart", Toast.LENGTH_SHORT).show();
            loadCart();
            requireActivity().invalidateOptionsMenu();
        } else {
            String[] names = new String[carts.size() + 1];
            names[0] = "Create New Parked Cart";
            for (int i = 0; i < carts.size(); i++) {
                String name = carts.get(i).get("name");
                names[i+1] = (name != null ? name : "Cart #" + carts.get(i).get("id"));
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Parked Cart")
                    .setItems(names, (dialog, which) -> {
                        long cartId;
                        if (which == 0) {
                            cartId = db.createParkedCart("Partial Cart");
                        } else {
                            cartId = Long.parseLong(carts.get(which - 1).get("id"));
                        }
                        db.moveSingleItemToParkedCart(productCode, cartId);
                        Toasty.success(requireContext(), "Item moved", Toast.LENGTH_SHORT).show();
                        loadCart();
                        requireActivity().invalidateOptionsMenu();
                    })
                    .show();
        }
    }
}
