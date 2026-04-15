package com.js.salesman.ui.activities;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.js.salesman.R;
import com.js.salesman.ui.fragments.CartFragment;
import com.js.salesman.ui.fragments.ParkedCartFragment;
import com.js.salesman.ui.fragments.ProductFragment;
import com.js.salesman.ui.fragments.ProfileFragment;
import com.js.salesman.ui.fragments.ReportsFragment;
import com.js.salesman.ui.fragments.SalesFragment;
import com.js.salesman.ui.fragments.SettingsFragment;
import com.js.salesman.utils.Db;
import com.js.salesman.utils.GPSManager;

import java.util.Objects;

import es.dmoral.toasty.Toasty;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawer;
    private BottomNavigationView bottomNav;
    private Db db;
    private GestureDetector gestureDetector;
    private long backPressedTime;
    private static final int BACK_PRESS_INTERVAL = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new Db(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(toolbar.getOverflowIcon()).setTint(
                ContextCompat.getColor(this, R.color.honeydew)
        );
        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        // set username & role
        // get header view safely
        View headerView = navigationView.getHeaderCount() > 0
                ? navigationView.getHeaderView(0)
                : null;
        assert headerView != null;
        TextView tvUserName = headerView.findViewById(R.id.tvUserName);
        TextView tvUserRole = headerView.findViewById(R.id.tvUserRole);
        //get from session
        if (session.isSessionValid()) {
            GPSManager.startTracking(this);
            tvUserName.setText(session.getFullName());
            tvUserRole.setText(session.getRole());
        }
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(
                ContextCompat.getColor(this, R.color.honeydew));
        Log.d("TASK_CHECK_MAIN", "isTaskRoot: " + isTaskRoot());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Close drawer if open
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                    return;
                }
                // Go to default fragment if not already there
                if (!(getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container) instanceof ProductFragment)) {
                    // Check if we can pop backstack first
                    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                        getSupportFragmentManager().popBackStack();
                    } else {
                        bottomNav.setSelectedItemId(R.id.nav_products);
                    }
                    return;
                }
                // Double back to exit
                long now = System.currentTimeMillis();
                if (now - backPressedTime < BACK_PRESS_INTERVAL) {
                    finishAffinity(); // exit app
                    android.os.Process.killProcess(android.os.Process.myPid());
                } else {
                    backPressedTime = now;
                    Toasty.info(MainActivity.this, "Press back again to exit", Toasty.LENGTH_SHORT, true).show();
                }
            }
        });

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            /*if(item.getItemId() == R.id.nav_home){
                loadFragment(new HomeFragment());
                return true;
            } else*/ if (item.getItemId() == R.id.nav_sales) {
                loadFragment(new SalesFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_reports) {
                loadFragment(new ReportsFragment());
                return true;
            }/*else if(item.getItemId() == R.id.nav_customers) {
                loadFragment(new CustomerFragment());
                return true;
            }*/ else if (item.getItemId() == R.id.nav_products) {
                loadFragment(new ProductFragment());
                return true;
            } else {
                return false;
            }
        });
        // default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_products);
        }
        gestureDetector = new GestureDetector(this, new GestureListener());
        findViewById(R.id.fragment_container).setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });
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
        /*if(item.getItemId() == R.id.nav_home){
            loadFragment(new HomeFragment());
        } else*/ if (item.getItemId() == R.id.nav_sales) {
            loadFragment(new SalesFragment());
        } else if (item.getItemId() == R.id.nav_reports) {
            loadFragment(new ReportsFragment());
        } /*else if(item.getItemId() == R.id.nav_customers){
            loadFragment(new CustomerFragment());
        }*/ else if (item.getItemId() == R.id.nav_products) {
            loadFragment(new ProductFragment());
        } else if(item.getItemId() == R.id.nav_logout){
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
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getIcon() != null) {
                    item.getIcon().setTint(
                            ContextCompat.getColor(this, R.color.honeydew)
                    );
                }
            }
        
        // Main Cart Badge (RED)
        MenuItem cartItem = menu.findItem(R.id.action_cart);
        if (cartItem != null) {
            cartItem.setActionView(R.layout.cart_layout);
            View cartView = cartItem.getActionView();
            if (cartView != null) {
                TextView cartBadge = cartView.findViewById(R.id.cart_badge);
                int count = db.getCartCount();
                if (cartBadge != null) {
                    cartBadge.setText(String.valueOf(count));
                    cartBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                }
                cartView.setOnClickListener(v -> onOptionsItemSelected(cartItem));
            }
        }

        // Parked Cart Badge (BLUE)
        MenuItem parkedItem = menu.findItem(R.id.action_parkedCart);
        if (parkedItem != null) {
            parkedItem.setActionView(R.layout.action_parked_cart_badge);
            View parkedView = parkedItem.getActionView();
            if (parkedView != null) {
                TextView parkedBadge = parkedView.findViewById(R.id.parked_cart_badge);
                int count = db.getParkedCartsCount();
                if (parkedBadge != null) {
                    parkedBadge.setText(String.valueOf(count));
                    parkedBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                }
                parkedView.setOnClickListener(v -> onOptionsItemSelected(parkedItem));
            }
        }

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
        } else if (id == R.id.action_cart) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new CartFragment())
                    .addToBackStack(null)
                    .commit();
            return true;
        }
        else if (id == R.id.action_parkedCart) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new ParkedCartFragment())
                    .addToBackStack(null)
                    .commit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX,
                            float velocityY) {
            if (e1 == null) return false;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            int SWIPE_THRESHOLD = 100;
            int SWIPE_VELOCITY_THRESHOLD = 100;
            if (Math.abs(diffX) > Math.abs(diffY)
                    && Math.abs(diffX) > SWIPE_THRESHOLD
                    && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    // Swipe Right
                    moveToPreviousTab();
                } else {
                    // Swipe Left
                    moveToNextTab();
                }
                return true;
            }
            return false;
        }
    }
    private final int[] bottomNavOrder = {
            //R.id.nav_customers,
            R.id.nav_products,
            //R.id.nav_home,
            R.id.nav_sales,
            R.id.nav_reports
    };
    private void moveToNextTab() {
        int currentId = bottomNav.getSelectedItemId();
        for (int i = 0; i < bottomNavOrder.length; i++) {
            if (bottomNavOrder[i] == currentId) {
                if (i < bottomNavOrder.length - 1) {
                    bottomNav.setSelectedItemId(bottomNavOrder[i + 1]);
                }
                return;
            }
        }
    }
    private void moveToPreviousTab() {
        int currentId = bottomNav.getSelectedItemId();
        for (int i = 0; i < bottomNavOrder.length; i++) {
            if (bottomNavOrder[i] == currentId) {
                if (i > 0) {
                    bottomNav.setSelectedItemId(bottomNavOrder[i - 1]);
                }
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) { // fine location
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GPSManager.startTracking(this);
            } else {
                Toasty.error(this,
                        "Location permission required. App will close.",
                        Toasty.LENGTH_LONG,
                        true).show();
                finish();
            }
        }
        if (requestCode == 1002) { // background location
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GPSManager.startTracking(this);
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
