package com.js.salesman.utils;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.js.salesman.R;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Product;
import com.js.salesman.ui.fragments.CartFragment;
import com.js.salesman.ui.fragments.ProductFragment;
import com.js.salesman.utils.managers.SessionManager;

import es.dmoral.toasty.Toasty;

public class OrderHelper {

    public static void addItemToOrder(Fragment fragment, Product product) {
        Context context = fragment.requireContext();
                Db db = Db.getInstance(context);
        SessionManager sessionManager = new SessionManager(context);
        Customer customer = sessionManager.getSelectedCustomer();
        String customerCategory = customer != null ? customer.getCategory() : null;

        final EditText qtyInput = new EditText(context);
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setHint("Quantity");

        int existingQty = db.getProductQuantity(product.getProductCode());
        if (existingQty > 0) {
            qtyInput.setText(String.valueOf(existingQty));
        } else {
            qtyInput.setText("1");
        }
        qtyInput.setSelection(qtyInput.getText().length());

        new AlertDialog.Builder(context)
                .setTitle("Select Quantity")
                .setView(qtyInput)
                .setPositiveButton("Add", (dialog, which) -> {
                    String qtyStr = qtyInput.getText().toString();
                    if (qtyStr.isEmpty()) return;
                    int qty = Integer.parseInt(qtyStr);

                    double price = PricingHelper.getPrice(product, customerCategory);

                    if (db.storeOrder(product.getProductCode(), product.getProductName(), price, qty)) {
                        Toasty.success(context, "Item added to cart", Toast.LENGTH_SHORT, true).show();
                        fragment.requireActivity().invalidateOptionsMenu();
                        showPostAddDialog(fragment);
                    } else {
                        Toasty.error(context, "Failed to add to cart", Toast.LENGTH_SHORT, true).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showPostAddDialog(Fragment fragment) {
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle("Item added to cart")
                .setMessage("What would you like to do next?")
                .setPositiveButton("Checkout", (dialog, which) -> fragment.requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new CartFragment())
                        .addToBackStack(null)
                        .commit())
                .setNegativeButton("Continue Shopping", (dialog, which) -> {
                    if (!(fragment instanceof ProductFragment)) {
                        fragment.requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new ProductFragment())
                                .commit();
                    }
                })
                .show();
    }
}
