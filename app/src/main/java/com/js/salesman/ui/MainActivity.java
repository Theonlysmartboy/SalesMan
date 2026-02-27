package com.js.salesman.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.js.salesman.R;
import com.js.salesman.fragments.CustomerFragment;
import com.js.salesman.fragments.HomeFragment;
import com.js.salesman.fragments.ProductFragment;
import com.js.salesman.fragments.ProfileFragment;
import com.js.salesman.fragments.ReportsFragment;
import com.js.salesman.fragments.SalesFragment;
import com.js.salesman.fragments.SettingsFragment;
import com.js.salesman.session.SessionManager;
import com.js.salesman.ui.auth.LoginActivity;
import com.js.salesman.utils.Db;

import es.dmoral.toasty.Toasty;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawer;
    private BottomNavigationView bottomNav;
    private SessionManager session;
    private Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        session = new SessionManager(this);
        db = new Db(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        // set username & role
        // 🔥 get header view safely
        var headerView = navigationView.getHeaderView(0);
        TextView tvUserName = headerView.findViewById(R.id.tvUserName);
        TextView tvUserRole = headerView.findViewById(R.id.tvUserRole);
        //get from session
        SessionManager session = new SessionManager(this);
        if (session.isSessionValid()) {
            tvUserName.setText(session.getFullName());
            tvUserRole.setText(session.getRole());
        }
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If drawer is open → close it
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                }
                // If not on Home fragment → go back to Home
                else if (!(getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container) instanceof HomeFragment)) {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                }
                // Otherwise → exit activity
                else {
                    finish();
                }
            }
        });
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if(item.getItemId() == R.id.nav_home){
                loadFragment(new HomeFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_sales) {
                loadFragment(new SalesFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_reports) {
                loadFragment(new ReportsFragment());
                return true;
            }else if(item.getItemId() == R.id.nav_customers) {
                loadFragment(new CustomerFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_products) {
                loadFragment(new ProductFragment());
                return true;
            } else {
                return false;
            }
        });
        // default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }
    private void loadFragment(androidx.fragment.app.Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawer.closeDrawer(GravityCompat.START);
        if(item.getItemId() == R.id.nav_logout){
            logoutUser();
        }else if(item.getItemId() == R.id.nav_profile){
            loadFragment(new ProfileFragment());
        } else if (item.getItemId() == R.id.nav_settings) {
            loadFragment(new SettingsFragment());
        }
        return true;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_notifications) {
            Toasty.info(this, "Coming soon", Toasty.LENGTH_SHORT, true).show();
            return true;
        } else if (id == R.id.action_profile) {
            loadFragment(new ProfileFragment());
            return true;
        } else if (id == R.id.action_settings) {
            loadFragment(new SettingsFragment());
            return true;
        } else if (id == R.id.action_logout) {
            logoutUser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void logoutUser() {
        SessionManager session = new SessionManager(this);
        session.clearSession();
        Db db = new Db(this);
        db.deleteUser();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}