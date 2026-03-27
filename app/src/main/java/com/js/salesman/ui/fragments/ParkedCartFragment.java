package com.js.salesman.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.js.salesman.R;
import com.js.salesman.adapters.ParkedCartAdapter;
import com.js.salesman.utils.Db;

import java.util.HashMap;
import java.util.List;

import es.dmoral.toasty.Toasty;

public class ParkedCartFragment extends Fragment implements ParkedCartAdapter.OnParkedCartInteractionListener {

    private RecyclerView recyclerView;
    private Db db;
    private List<HashMap<String, String>> parkedCarts;
    private ParkedCartAdapter adapter;

    public ParkedCartFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_parked_carts, container, false);

        db = new Db(requireContext());
        recyclerView = view.findViewById(R.id.parkedCartsRecycler);
        ImageView btnBack = view.findViewById(R.id.btnBack);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadParkedCarts();

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        return view;
    }

    private void loadParkedCarts() {
        parkedCarts = db.getParkedCarts();
        if (parkedCarts.isEmpty()) {
            Toasty.info(requireContext(), "No parked carts found", Toast.LENGTH_SHORT).show();
        }
        adapter = new ParkedCartAdapter(parkedCarts, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onRestore(long cartId) {
        db.restoreParkedCart(cartId);
        Toasty.success(requireContext(), "Cart restored to main cart", Toast.LENGTH_SHORT).show();
        requireActivity().invalidateOptionsMenu();
        
        // Navigate to CartFragment to see restored items
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new CartFragment())
                .commit();
    }

    @Override
    public void onDelete(long cartId) {
        db.deleteParkedCart(cartId);
        loadParkedCarts();
        Toasty.info(requireContext(), "Parked cart deleted", Toast.LENGTH_SHORT).show();
    }
}
