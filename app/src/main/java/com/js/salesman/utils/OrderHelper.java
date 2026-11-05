package com.js.salesman.utils;

import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.js.salesman.R;
import com.js.salesman.models.Customer;
import com.js.salesman.models.Product;
import com.js.salesman.repository.CartRepository;
import com.js.salesman.ui.fragments.CartFragment;
import com.js.salesman.ui.fragments.ProductFragment;
import com.js.salesman.utils.managers.SessionManager;

import es.dmoral.toasty.Toasty;

public class OrderHelper {

    private static final int MAX_QUANTITY_DIGITS = 6;
    private static boolean addInProgress = false;

    private OrderHelper() {}

    public static void addItemToOrder(@NonNull Fragment fragment, @NonNull Product product) {
        if (addInProgress || !fragment.isAdded()) return;
        final FragmentActivity activity = fragment.requireActivity();
        final CartRepository cartRepo = CartRepository.getInstance(activity);
        SessionManager sessionManager = new SessionManager(activity);
        Customer customer = sessionManager.getSelectedCustomer();
        final String customerCategory = customer != null ? customer.getCategory() : null;
        addInProgress = true;
        cartRepo.getProductQuantity(product.getProductCode(), existingQty -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                addInProgress = false;
                return;
            }
            showQuantityDialog(fragment, activity, cartRepo, product, customerCategory,
                    existingQty != null ? existingQty : 0);
        });
    }

    private static void showQuantityDialog(Fragment fragment, FragmentActivity activity,
                                            CartRepository cartRepo, Product product,
                                            String customerCategory, int existingQty) {
        final EditText qtyInput = new EditText(activity);
        qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_QUANTITY_DIGITS)});
        qtyInput.setHint("Quantity");
        qtyInput.setText(String.valueOf(existingQty > 0 ? existingQty : 1));
        qtyInput.setSelection(qtyInput.getText().length());
        new AlertDialog.Builder(activity)
                .setTitle("Select Quantity")
                .setView(qtyInput)
                .setPositiveButton("Add", (dialog, which) -> {
                    int qty = parseQuantity(qtyInput.getText().toString());
                    if (qty <= 0) {
                        Toasty.warning(activity, "Enter a quantity of 1 or more",
                                Toasty.LENGTH_SHORT, true).show();
                        return;
                    }
                    double price = PricingHelper.getPrice(product, customerCategory);
                    cartRepo.storeOrder(product.getProductCode(), product.getProductName(),
                            price, qty, saved -> {
                                if (activity.isFinishing() || activity.isDestroyed()) return;
                                if (Boolean.TRUE.equals(saved)) {
                                    Toasty.success(activity, "Item added to cart",
                                            Toasty.LENGTH_SHORT, true).show();
                                    // MainActivity reloads the badge counts when this is called.
                                    activity.invalidateOptionsMenu();
                                    if (fragment.isAdded()) {
                                        showPostAddDialog(fragment, activity);
                                    }
                                } else {
                                    Toasty.error(activity, "Failed to add to cart",
                                            Toasty.LENGTH_SHORT, true).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(d -> addInProgress = false)
                .show();
    }

    private static int parseQuantity(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void showPostAddDialog(Fragment fragment, FragmentActivity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Item added to cart")
                .setMessage("What would you like to do next?")
                .setPositiveButton("Checkout", (dialog, which) -> activity
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new CartFragment())
                        .addToBackStack(null)
                        .commit())
                .setNegativeButton("Continue Shopping", (dialog, which) -> {
                    if (!(fragment instanceof ProductFragment)) {
                        activity.getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new ProductFragment())
                                .commit();
                    }
                })
                .show();
    }
}