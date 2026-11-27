package com.js.salesman.callbacks;

import androidx.recyclerview.widget.DiffUtil;

import com.js.salesman.models.Customer;

import java.util.List;
import java.util.Objects;

public class CustomerDiffCallback extends DiffUtil.Callback {
    private final List<Customer> oldList;
    private final List<Customer> newList;

    public CustomerDiffCallback(List<Customer> oldList, List<Customer> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override public int getOldListSize() { return oldList.size(); }
    @Override public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return Objects.equals(
                oldList.get(oldPos).getCustomerCode(),
                newList.get(newPos).getCustomerCode());
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        Customer a = oldList.get(oldPos);
        Customer b = newList.get(newPos);
        return Objects.equals(a.getCustomerCode(), b.getCustomerCode())
                && Objects.equals(a.getCustomerName(), b.getCustomerName());
    }
}
