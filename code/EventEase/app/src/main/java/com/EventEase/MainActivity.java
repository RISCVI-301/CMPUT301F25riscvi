package com.EventEase;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.example.eventease.R;

public class MainActivity extends AppCompatActivity {

    private View bottomNav;
    private NavController nav;
    private View topBar;
    
    // Custom navigation button references
    private LinearLayout navButtonMyEvents;
    private LinearLayout navButtonDiscover;
    private LinearLayout navButtonAccount;
    private ImageView navIconMyEvents;
    private ImageView navIconDiscover;
    private ImageView navIconAccount;
    private android.widget.TextView navLabelMyEvents;
    private android.widget.TextView navLabelDiscover;
    private android.widget.TextView navLabelAccount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entrant_activity_mainfragments);

        // Don't draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        
        // Hide action bar initially (will show/hide based on destination)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // The <include> replaces the child's id, so the real id is include_bottom
        bottomNav = findViewById(R.id.include_bottom);
        if (bottomNav == null) {
            throw new IllegalStateException("include_bottom not found. Check activity_mainfragments.xml include tag.");
        }
        
        // Find custom navigation button views
        navButtonMyEvents = findViewById(R.id.nav_button_my_events);
        navButtonDiscover = findViewById(R.id.nav_button_discover);
        navButtonAccount = findViewById(R.id.nav_button_account);
        navIconMyEvents = findViewById(R.id.nav_icon_my_events);
        navIconDiscover = findViewById(R.id.nav_icon_discover);
        navIconAccount = findViewById(R.id.nav_icon_account);
        navLabelMyEvents = findViewById(R.id.nav_label_my_events);
        navLabelDiscover = findViewById(R.id.nav_label_discover);
        navLabelAccount = findViewById(R.id.nav_label_account);
        
        // Get top bar reference
        topBar = findViewById(R.id.include_top);
        if (topBar == null) {
            throw new IllegalStateException("include_top not found. Check activity_mainfragments.xml include tag.");
        }

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (host == null) {
            throw new IllegalStateException("nav_host_main not found or not a NavHostFragment.");
        }

        nav = host.getNavController();
        
        // Setup back press handling for authenticated screens
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavDestination destination = nav.getCurrentDestination();
                if (destination != null) {
                    int id = destination.getId();
                    // If we're on one of the authenticated main app screens, close the app
                    // instead of going back to auth screens
                    if (id == R.id.discoverFragment || id == R.id.myEventsFragment || id == R.id.accountFragment) {
                        finish();
                    } else {
                        // Use the default back handling for other screens
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                } else {
                    finish();
                }
            }
        });
        
        // Check if user is already logged in AND Remember Me is enabled (using UID for persistence)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        android.content.SharedPreferences prefs = getSharedPreferences("EventEasePrefs", MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        String savedUid = prefs.getString("savedUid", null);
        com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
        
        // Check if Remember Me is enabled and UID matches (this persists even if email/password changes)
        boolean isLoggedIn = rememberMe && savedUid != null && currentUser != null && savedUid.equals(currentUser.getUid());
        
        // If user is logged in but Remember Me is off or UID doesn't match, sign them out
        if (currentUser != null && (!rememberMe || savedUid == null || !savedUid.equals(currentUser.getUid()))) {
            auth.signOut();
            // Clear Remember Me if UID doesn't match
            if (savedUid != null && !savedUid.equals(currentUser.getUid())) {
                prefs.edit().putBoolean("rememberMe", false).remove("savedUid").apply();
            }
        }
        
        // Setup custom navigation button click listeners
        setupCustomNavigation();
        
        // Single unified listener that handles visibility and selection for all navigation
        nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination == null) return;
            
            int id = destination.getId();
            
            // Check if user is authenticated (check dynamically each time)
            FirebaseAuth authCheck = FirebaseAuth.getInstance();
            android.content.SharedPreferences prefsCheck = getSharedPreferences("EventEasePrefs", MODE_PRIVATE);
            boolean rememberMeCheck = prefsCheck.getBoolean("rememberMe", false);
            String savedUidCheck = prefsCheck.getString("savedUid", null);
            com.google.firebase.auth.FirebaseUser currentUserCheck = authCheck.getCurrentUser();
            boolean isAuthenticated = rememberMeCheck && savedUidCheck != null && currentUserCheck != null && savedUidCheck.equals(currentUserCheck.getUid());
            
            // Main app screens (discover, my events, account) - show bars if authenticated
            if (id == R.id.discoverFragment || id == R.id.myEventsFragment || id == R.id.accountFragment 
                || id == R.id.eventsSelectionFragment || id == R.id.previousEventsFragment || id == R.id.upcomingEventsFragment) {
                if (isAuthenticated) {
                    // User is authenticated, show top bar and bottom nav
                    bottomNav.setVisibility(View.VISIBLE);
                    topBar.setVisibility(View.VISIBLE);
                    updateNavigationSelection(id);
                } else {
                    // User not authenticated, hide bars
                    bottomNav.setVisibility(View.GONE);
                    topBar.setVisibility(View.GONE);
                }
                // Keep action bar hidden
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            } else if (id == R.id.welcomeFragment || id == R.id.signupFragment 
                       || id == R.id.loginFragment || id == R.id.uploadProfilePictureFragment
                       || id == R.id.forgotPasswordFragment || id == R.id.locationPermissionFragment) {
                // On auth screens, hide all bars
                bottomNav.setVisibility(View.GONE);
                topBar.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            } else {
                // Default: hide bars for any other screens
                bottomNav.setVisibility(View.GONE);
                topBar.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            }
        });
        
        if (isLoggedIn) {
            // User is logged in on startup - show bars and navigate to Discover
            topBar.setVisibility(View.VISIBLE);
            bottomNav.setVisibility(View.VISIBLE);
            // Navigate to discover after the view is ready and mark selected
            bottomNav.post(() -> {
                nav.navigate(R.id.discoverFragment);
                updateNavigationSelection(R.id.discoverFragment);
            });
        } else {
            // User not logged in on startup - hide bars initially
            bottomNav.setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
        }

        // Handle external navigation intents (from detail activities)
        handleExternalNav(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleExternalNav(intent);
    }

    private void handleExternalNav(android.content.Intent intent) {
        if (intent == null) return;
        String target = intent.getStringExtra("nav_target");
        if (target == null) return;
        bottomNav.post(() -> {
            switch (target) {
                case "discover":
                    nav.navigate(R.id.discoverFragment);
                    break;
                case "myEvents":
                    nav.navigate(R.id.eventsSelectionFragment);
                    break;
                case "account":
                    nav.navigate(R.id.accountFragment);
                    break;
            }
        });
    }
    
    private void setupCustomNavigation() {
        navButtonMyEvents.setOnClickListener(v -> {
            nav.navigate(R.id.eventsSelectionFragment);
            updateNavigationSelection(R.id.eventsSelectionFragment);
        });
        
        navButtonDiscover.setOnClickListener(v -> {
            nav.navigate(R.id.discoverFragment);
            updateNavigationSelection(R.id.discoverFragment);
        });
        
        navButtonAccount.setOnClickListener(v -> {
            nav.navigate(R.id.accountFragment);
            updateNavigationSelection(R.id.accountFragment);
        });
    }
    
    private void updateNavigationSelection(int destinationId) {
        // Dark blue for unselected items (brand color)
        int unselectedColor = android.graphics.Color.parseColor("#223C65");
        // iOS blue color for selected items
        int selectedColor = android.graphics.Color.parseColor("#446EAF");
        
        // Reset all to unselected (dark circles and gray text)
        navIconMyEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_dark);
        navIconDiscover.setImageResource(R.drawable.entrant_ic_discover_circle_dark);
        navIconAccount.setImageResource(R.drawable.entrant_ic_account_circle_dark);
        navLabelMyEvents.setTextColor(unselectedColor);
        navLabelDiscover.setTextColor(unselectedColor);
        navLabelAccount.setTextColor(unselectedColor);
        
        // Set selected (light circle and blue text) based on destination
        if (destinationId == R.id.eventsSelectionFragment || destinationId == R.id.myEventsFragment 
            || destinationId == R.id.previousEventsFragment || destinationId == R.id.upcomingEventsFragment) {
            navIconMyEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_light);
            navLabelMyEvents.setTextColor(selectedColor);
        } else if (destinationId == R.id.discoverFragment) {
            navIconDiscover.setImageResource(R.drawable.entrant_ic_discover_circle_light);
            navLabelDiscover.setTextColor(selectedColor);
        } else if (destinationId == R.id.accountFragment) {
            navIconAccount.setImageResource(R.drawable.entrant_ic_account_circle_light);
            navLabelAccount.setTextColor(selectedColor);
        }
    }
}


