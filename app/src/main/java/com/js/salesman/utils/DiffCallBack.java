package com.js.salesman.utils;

import androidx.recyclerview.widget.DiffUtil;

import com.js.salesman.models.Order;

import java.util.List;

public class DiffCallBack extends DiffUtil.Callback {

    private final List<Order> oldList;
    private final List<Order> newList;

    public DiffCallBack(List<Order> oldList, List<Order> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).getId() ==
                newList.get(newItemPosition).getId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
    }
}
