package com.js.salesman.callbacks;

import androidx.recyclerview.widget.DiffUtil;

import com.js.salesman.models.OrderLine;

import java.util.List;
import java.util.Objects;

public class LineDiffCallback extends DiffUtil.Callback {
    private final List<OrderLine> oldList;
    private final List<OrderLine> newList;

    public LineDiffCallback(List<OrderLine> oldList, List<OrderLine> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override public int getOldListSize() { return oldList.size(); }
    @Override public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        // See "Stable identity" below — pick something genuinely unique per line
        return Objects.equals(
                oldList.get(oldPos).getProductCode(),
                newList.get(newPos).getProductCode());
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        OrderLine a = oldList.get(oldPos);
        OrderLine b = newList.get(newPos);
        return Objects.equals(a.getProductCode(), b.getProductCode())
                && Objects.equals(a.getProductName(), b.getProductName())
                && Objects.equals(a.getQuantity(),   b.getQuantity())
                && Objects.equals(a.getUnitPrice(),  b.getUnitPrice())
                && Objects.equals(a.getLineTotal(),  b.getLineTotal())
                && Objects.equals(a.getVatAmount(),  b.getVatAmount())
                && Objects.equals(a.getVatRate(),    b.getVatRate());
    }
}
