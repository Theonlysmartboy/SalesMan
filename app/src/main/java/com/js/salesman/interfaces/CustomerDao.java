package com.js.salesman.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.js.salesman.models.Customer;

import java.util.List;

@Dao
public interface CustomerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCustomers(List<Customer> customers);

    @Query("SELECT * FROM customers ORDER BY customer_name ASC")
    List<Customer> getAllCustomers();

    @Query("SELECT * FROM customers WHERE customer_name LIKE '%' || :query || '%' OR customer_code LIKE '%' || :query || '%' ORDER BY customer_name ASC")
    List<Customer> searchCustomers(String query);

    @Query("SELECT COUNT(*) FROM customers")
    int getCount();
}
