package com.js.salesman.callbacks;

import androidx.recyclerview.widget.DiffUtil;

import com.js.salesman.models.Product;

import java.util.List;
import java.util.Objects;

public class ProductDiffCallback extends DiffUtil.Callback {
    private final List<Product> oldList;
    private final List<Product> newList;

    public ProductDiffCallback(List<Product> oldList, List<Product> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override public int getOldListSize() { return oldList.size(); }
    @Override public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return Objects.equals(
                oldList.get(oldPos).getProductCode(),
                newList.get(newPos).getProductCode());
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        Product a = oldList.get(oldPos);
        Product b = newList.get(newPos);
        return Objects.equals(a.getProductCode(), b.getProductCode())
                && Objects.equals(a.getProductName(), b.getProductName())
                && Objects.equals(a.getProductUnit(), b.getProductUnit())
                && Objects.equals(a.getProduct_Selling_Price(), b.getProduct_Selling_Price())
                && Objects.equals(a.getProductQuantity(), b.getProductQuantity());
    }
}
