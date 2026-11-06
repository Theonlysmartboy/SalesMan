package com.js.salesman.ui.activities;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.js.salesman.R;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.repository.CartRepository;
import com.js.salesman.repository.CustomerVisitRepository;
import com.js.salesman.repository.NotificationRepository;
import com.js.salesman.ui.dialogues.VisitStartDialog;
import com.js.salesman.ui.fragments.ActiveVisitFragment;
import com.js.salesman.ui.fragments.CartFragment;
import com.js.salesman.ui.fragments.CheckoutFragment;
import com.js.salesman.ui.fragments.NotificationsFragment;
import com.js.salesman.ui.fragments.ParkedCartFragment;
import com.js.salesman.ui.fragments.ProductDescriptionFragment;
import com.js.salesman.ui.fragments.ProductFragment;
import com.js.salesman.ui.fragments.ProfileFragment;
import com.js.salesman.ui.fragments.ReportsFragment;
import com.js.salesman.ui.fragments.SalesOrderFragment;
import com.js.salesman.ui.fragments.ScheduledVisitsFragment;
import com.js.salesman.ui.fragments.SettingsFragment;
import com.js.salesman.ui.views.ActiveVisitBannerView;
import com.js.salesman.utils.managers.GPSManager;
import com.js.salesman.viewmodels.HomeViewModel;

import es.dmoral.toasty.Toasty;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int BACK_PRESS_INTERVAL = 2000;
    private static final String VISIT_DIALOG_TAG = "VisitStartDialog";
    private DrawerLayout drawer;
    private BottomNavigationView bottomNav;
    private GestureDetector gestureDetector;
    private long backPressedTime;
    private ActiveVisitBannerView activeVisitBanner;
    private HomeViewModel homeViewModel;
    private Fragment pendingProtectedFragment = null;
    private int cartCount = 0;
    private int parkedCartCount = 0;
    private int unreadNotificationCount = 0;
    private final int[] bottomNavOrder = {
            R.id.nav_products,
            R.id.nav_sales_invoice,
            R.id.nav_reports
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Drawable overflowIcon = toolbar.getOverflowIcon();
        if (overflowIcon != null) {
            overflowIcon.setTint(ContextCompat.getColor(this, R.color.honeydew));
        }
        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        activeVisitBanner = findViewById(R.id.activeVisitBanner);
        activeVisitBanner.setOnClickListener(v -> getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ActiveVisitFragment())
                .addToBackStack(null)
                .commit());
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        homeViewModel.getActiveVisit().observe(this, activeVisit -> {
            activeVisitBanner.bindVisit(activeVisit);
            if (activeVisit != null && "IN_PROGRESS".equals(activeVisit.visitStatus)) {
                if (pendingProtectedFragment != null) {
                    Fragment target = pendingProtectedFragment;
                    pendingProtectedFragment = null;
                    loadFragment(target);
                }
            } else {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(
                        R.id.fragment_container);
                if (isProtectedDestination(currentFragment)) {
                    showMandatoryVisitStartDialog();
                }
            }
        });
        if (session.isSessionValid()) {
            GPSManager.startTracking(this);
            View headerView = navigationView.getHeaderCount() > 0
                    ? navigationView.getHeaderView(0) : null;
            if (headerView != null) {
                TextView tvUserName = headerView.findViewById(R.id.tvUserName);
                TextView tvUserRole = headerView.findViewById(R.id.tvUserRole);
                if (tvUserName != null) tvUserName.setText(session.getFullName());
                if (tvUserRole != null) tvUserRole.setText(session.getRole());
            }
        }
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.honeydew));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                    return;
                }
                Fragment current = getSupportFragmentManager().findFragmentById(
                        R.id.fragment_container);
                if (!(current instanceof ProductFragment)) {
                    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                        getSupportFragmentManager().popBackStack();
                    } else {
                        bottomNav.setSelectedItemId(R.id.nav_products);
                    }
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - backPressedTime < BACK_PRESS_INTERVAL) {
                    finishAffinity();
                } else {
                    backPressedTime = now;
                    Toasty.info(MainActivity.this, "Press back again to exit",
                            Toasty.LENGTH_SHORT, true).show();
                }
            }
        });
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_reports) {
                checkAndLoadFragment(ReportsFragment.newInstance("amount"));
                return true;
            } else if (item.getItemId() == R.id.nav_products) {
                checkAndLoadFragment(new ProductFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_sales_invoice) {
                checkAndLoadFragment(new SalesOrderFragment());
                return true;
            } else {
                return false;
            }
        });
        if (savedInstanceState == null) {
            checkAndLoadFragment(new ProductFragment());
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

    private boolean isProtectedDestination(Fragment fragment) {
        return fragment instanceof ProductFragment
                || fragment instanceof ProductDescriptionFragment
                || fragment instanceof CartFragment
                || fragment instanceof CheckoutFragment
                || fragment instanceof SalesOrderFragment;
    }

    private void checkAndLoadFragment(Fragment fragment) {
        if (!isProtectedDestination(fragment)) {
            loadFragment(fragment);
            return;
        }
        CustomerVisit currentActive = homeViewModel.getActiveVisit().getValue();
        if (currentActive != null && "IN_PROGRESS".equals(currentActive.visitStatus)) {
            loadFragment(fragment);
            return;
        }
        CustomerVisitRepository.getInstance(this).getActiveVisit(session.getUserId(),
                new CustomerVisitRepository.VisitCallback() {
                    @Override
                    public void onSuccess(CustomerVisit visit) {
                        if (isFinishing() || isDestroyed()) return;
                        if (visit != null && "IN_PROGRESS".equals(visit.visitStatus)) {
                            loadFragment(fragment);
                        } else {
                            pendingProtectedFragment = fragment;
                            showMandatoryVisitStartDialog();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        pendingProtectedFragment = fragment;
                        showMandatoryVisitStartDialog();
                    }
                });
    }

    private void showMandatoryVisitStartDialog() {
        FragmentManager fm = getSupportFragmentManager();
        if (isFinishing() || isDestroyed() || fm.isStateSaved()) return;
        if (fm.findFragmentByTag(VISIT_DIALOG_TAG) != null) return;
        VisitStartDialog dialog = new VisitStartDialog();
        dialog.setOnVisitStartedListener(visit -> {
            homeViewModel.refreshActiveVisit();
            if (pendingProtectedFragment != null) {
                Fragment target = pendingProtectedFragment;
                pendingProtectedFragment = null;
                loadFragment(target);
            }
        });
        dialog.show(fm, VISIT_DIALOG_TAG);
    }

    private void loadFragment(Fragment fragment) {
        if (isFinishing() || isDestroyed()) return;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction()
                .replace(R.id.fragment_container, fragment);
        if (fm.isStateSaved()) {
            transaction.commitAllowingStateLoss();
        } else {
            transaction.commit();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawer.closeDrawer(GravityCompat.START);
        int id = item.getItemId();
        if (id == R.id.nav_reports) {
            checkAndLoadFragment(ReportsFragment.newInstance("amount"));
        } else if (id == R.id.nav_products) {
            checkAndLoadFragment(new ProductFragment());
        } else if (id == R.id.nav_scheduled_visits) {
            checkAndLoadFragment(new ScheduledVisitsFragment());
        } else if (id == R.id.nav_logout) {
            confirmLogout();
        } else if (id == R.id.nav_profile) {
            checkAndLoadFragment(new ProfileFragment());
        } else if (id == R.id.nav_settings) {
            checkAndLoadFragment(new SettingsFragment());
        }
        return true;
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> logoutUser())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getIcon() != null) {
                item.getIcon().setTint(ContextCompat.getColor(this, R.color.honeydew));
            }
        }
        bindBadge(menu, R.id.action_cart, R.layout.cart_layout,
                R.id.cart_badge, cartCount);
        bindBadge(menu, R.id.action_parkedCart, R.layout.action_parked_cart_badge,
                R.id.parked_cart_badge, parkedCartCount);
        bindBadge(menu, R.id.action_notifications, R.layout.notification_badge_layout,
                R.id.notification_badge, unreadNotificationCount);
        return true;
    }

    private void bindBadge(@NonNull Menu menu, @IdRes int itemId, @LayoutRes int actionLayout,
                            @IdRes int badgeId, int count) {
        MenuItem item = menu.findItem(itemId);
        if (item == null) return;
        item.setActionView(actionLayout);
        View actionView = item.getActionView();
        if (actionView == null) return;
        TextView badge = actionView.findViewById(badgeId);
        if (badge != null) {
            badge.setText(String.valueOf(count));
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }
        actionView.setOnClickListener(v -> onOptionsItemSelected(item));
    }

    public void refreshBadgeCounts() {
        CartRepository cartRepository = CartRepository.getInstance(this);
        NotificationRepository notificationRepository = NotificationRepository
                .getInstance(this);
        cartRepository.getCartCount(count -> {
            if (count == null || count == cartCount) return;
            cartCount = count;
            rebuildMenu();
        });
        cartRepository.getParkedCartsCount(count -> {
            if (count == null || count == parkedCartCount) return;
            parkedCartCount = count;
            rebuildMenu();
        });
        notificationRepository.getUnreadNotificationsCount(count -> {
            if (count == null || count == unreadNotificationCount) return;
            unreadNotificationCount = count;
            rebuildMenu();
        });
    }

    @Override
    public void invalidateOptionsMenu() {
        super.invalidateOptionsMenu();
        refreshBadgeCounts();
    }

    private void rebuildMenu() {
        if (isFinishing() || isDestroyed()) return;
        super.invalidateOptionsMenu();
    }

    public void updateNotificationBadge() {
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_notifications) {
            checkAndLoadFragment(new NotificationsFragment());
            return true;
        } else if (id == R.id.action_profile) {
            checkAndLoadFragment(new ProfileFragment());
            return true;
        } else if (id == R.id.action_settings) {
            checkAndLoadFragment(new SettingsFragment());
            return true;
        } else if (id == R.id.action_logout) {
            confirmLogout();
            return true;
        } else if (id == R.id.action_cart) {
            checkAndLoadFragment(new CartFragment());
            return true;
        } else if (id == R.id.action_parkedCart) {
            checkAndLoadFragment(new ParkedCartFragment());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2,
                                float velocityX, float velocityY) {
            if (e1 == null) return false;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            int swipeThreshold = 100;
            int swipeVelocityThreshold = 100;
            if (Math.abs(diffX) > Math.abs(diffY)
                    && Math.abs(diffX) > swipeThreshold
                    && Math.abs(velocityX) > swipeVelocityThreshold) {
                if (diffX > 0) {
                    moveToPreviousTab();
                } else {
                    moveToNextTab();
                }
                return true;
            }
            return false;
        }
    }

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
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == 1001) {
            if (granted) {
                GPSManager.startTracking(this);
            } else {
                Toasty.error(this, "Location permission required. App will close.",
                        Toasty.LENGTH_LONG, true).show();
                finish();
            }
        } else if (requestCode == 1002 && granted) {
            GPSManager.startTracking(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homeViewModel != null) {
            homeViewModel.refreshActiveVisit();
        }
        // Also reloads the badge counts (see invalidateOptionsMenu override).
        invalidateOptionsMenu();
    }

    @Override
    protected boolean isOfflineSupported() {
        Fragment currentFragment =
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        return currentFragment instanceof ProductFragment
                || currentFragment instanceof ProductDescriptionFragment;
    }
}