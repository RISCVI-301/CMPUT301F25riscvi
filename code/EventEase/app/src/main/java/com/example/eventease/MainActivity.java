package com.example.eventease;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mainfragments);

        // Don't draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // The <include> replaces the child's id, so the real id is include_bottom
        bottomNav = findViewById(R.id.include_bottom);
        if (bottomNav == null) {
            throw new IllegalStateException("include_bottom not found. Check activity_mainfragments.xml include tag.");
        }

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (host == null) {
            throw new IllegalStateException("nav_host_main not found or not a NavHostFragment.");
        }

        NavController nav = host.getNavController();
        
        // Check if user is already logged in
        FirebaseAuth auth = FirebaseAuth.getInstance();
        boolean isLoggedIn = auth.getCurrentUser() != null;
        
        if (isLoggedIn) {
            // User is logged in - setup bottom nav and navigate to discover
            NavigationUI.setupWithNavController(bottomNav, nav);
            // Navigate to discover after the view is ready
            bottomNav.post(() -> nav.navigate(R.id.discoverFragment));
        } else {
            // User not logged in - hide bottom nav initially
            bottomNav.setVisibility(View.GONE);
            
            // Track if NavigationUI has been set up
            final boolean[] navUISetup = {false};
            
            // Show/hide bottom nav based on destination
            nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (id == R.id.discoverFragment || id == R.id.myEventsFragment || id == R.id.accountFragment) {
                    // User authenticated and on main app screen, show and setup bottom nav
                    bottomNav.setVisibility(View.VISIBLE);
                    if (!navUISetup[0]) {
                        NavigationUI.setupWithNavController(bottomNav, nav);
                        navUISetup[0] = true;
                    }
                } else {
                    // On auth screens, hide bottom nav
                    bottomNav.setVisibility(View.GONE);
                }
            });
        }
    }
}
