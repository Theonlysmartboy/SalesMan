package com.js.salesman.ui.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.js.salesman.R;

public class SalesInvoiceFragment extends Fragment {
    private TextView txtCreditLimit, txtOutstanding, txtCreditDays;

    public SalesInvoiceFragment() {
        // Required empty public constructor
    }

   @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sales_invoice, container, false);
        txtCreditLimit = view.findViewById(R.id.txtCreditLimit);
        txtOutstanding = view.findViewById(R.id.txtOutstanding);
        txtCreditDays = view.findViewById(R.id.txtCreditDays);

        //txtCreditLimit.setText(currencyFormat.format(customer.getCreditLimit()));
        //txtOutstanding.setText(currencyFormat.format(customer.getOutstanding()));
        //txtCreditDays.setText(String.valueOf(customer.getCreditDays()));
        return view;
    }
}