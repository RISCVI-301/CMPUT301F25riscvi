package com.example.eventease;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private NavController nav;
    private View topBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mainfragments);

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
        
        // Check if user is already logged in AND Remember Me is enabled
        FirebaseAuth auth = FirebaseAuth.getInstance();
        android.content.SharedPreferences prefs = getSharedPreferences("EventEasePrefs", MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        boolean isLoggedIn = auth.getCurrentUser() != null && rememberMe;
        
        // If user is logged in but Remember Me is off, sign them out
        if (auth.getCurrentUser() != null && !rememberMe) {
            auth.signOut();
        }
        
        if (isLoggedIn) {
            // User is logged in and Remember Me is enabled - setup bottom nav and navigate to discover
            NavigationUI.setupWithNavController(bottomNav, nav);
            topBar.setVisibility(View.VISIBLE);
            // Navigate to discover after the view is ready
            bottomNav.post(() -> nav.navigate(R.id.discoverFragment));
        } else {
            // User not logged in - hide bottom nav and top bar initially
            bottomNav.setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
            
            // Track if NavigationUI has been set up
            final boolean[] navUISetup = {false};
            
            // Show/hide bottom nav, top bar, and action bar based on destination
            nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (id == R.id.discoverFragment || id == R.id.myEventsFragment || id == R.id.accountFragment 
                    || id == R.id.eventsSelectionFragment || id == R.id.previousEventsFragment) {
                    // User authenticated and on main app screen, show top bar and bottom nav
                    bottomNav.setVisibility(View.VISIBLE);
                    topBar.setVisibility(View.VISIBLE);
                    if (!navUISetup[0]) {
                        NavigationUI.setupWithNavController(bottomNav, nav);
                        navUISetup[0] = true;
                    }
                    // Keep action bar hidden
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().hide();
                    }
                } else if (id == R.id.welcomeFragment || id == R.id.signupFragment 
                           || id == R.id.loginFragment || id == R.id.uploadProfilePictureFragment
                           || id == R.id.forgotPasswordFragment || id == R.id.locationPermissionFragment) {
                    // On auth screens (welcome, signup, login, forgot password, upload picture, location permission), hide all bars
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
        }
    }
}
