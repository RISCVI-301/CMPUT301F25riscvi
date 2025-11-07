package com.example.eventease.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.example.eventease.R;
import com.example.eventease.auth.UserRoleChecker;
import com.example.eventease.util.ToastUtil;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import android.content.SharedPreferences;

/**
 * Main activity for admin users.
 * Handles navigation between admin fragments and manages bottom navigation.
 */
public class AdminMainActivity extends AppCompatActivity {

    private View bottomNav;
    private NavController nav;
    private LinearLayout navButtonEvents;
    private LinearLayout navButtonProfiles;
    private LinearLayout navButtonImages;
    private LinearLayout navButtonLogs;
    private ImageView navIconEvents;
    private ImageView navIconProfiles;
    private ImageView navIconImages;
    private ImageView navIconLogs;
    private TextView navLabelEvents;
    private TextView navLabelProfiles;
    private TextView navLabelImages;
    private TextView navLabelLogs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firebase initialization
        FirebaseApp.initializeApp(this);
        Log.d("AdminMainActivity", "FirebaseApp initialized: " + (FirebaseApp.getApps(this).size() > 0));

        // Check if user is admin, if not redirect to login
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            // Not authenticated, redirect to login
            finish();
            return;
        }

        setContentView(R.layout.admin_activity_mainfragments);

        // Verify admin role
        UserRoleChecker.isAdmin().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !Boolean.TRUE.equals(task.getResult())) {
                // Not admin, redirect to main activity
                Intent intent = new Intent(AdminMainActivity.this, com.example.eventease.MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
            // User is admin - setup UI
            setupAdminUI();
        });

        // Don't draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    private void setupAdminUI() {
        // Get bottom navigation
        bottomNav = findViewById(R.id.include_bottom);
        if (bottomNav == null) {
            throw new IllegalStateException("include_bottom not found. Check admin_activity_mainfragments.xml include tag.");
        }

        // Find custom navigation button views
        navButtonEvents = findViewById(R.id.nav_button_events);
        navButtonProfiles = findViewById(R.id.nav_button_profiles);
        navButtonImages = findViewById(R.id.nav_button_images);
        navButtonLogs = findViewById(R.id.nav_button_logs);
        navIconEvents = findViewById(R.id.nav_icon_events);
        navIconProfiles = findViewById(R.id.nav_icon_profiles);
        navIconImages = findViewById(R.id.nav_icon_images);
        navIconLogs = findViewById(R.id.nav_icon_logs);
        navLabelEvents = findViewById(R.id.nav_label_events);
        navLabelProfiles = findViewById(R.id.nav_label_profiles);
        navLabelImages = findViewById(R.id.nav_label_images);
        navLabelLogs = findViewById(R.id.nav_label_logs);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (host == null) {
            throw new IllegalStateException("nav_host_main not found or not a NavHostFragment.");
        }

        nav = host.getNavController();
        androidx.navigation.NavGraph graph = nav.getGraph();

        // Setup back press handling - use destination label matching
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavDestination destination = nav.getCurrentDestination();
                if (destination != null) {
                    String label = destination.getLabel() != null ? destination.getLabel().toString() : "";
                    // Check if we're on one of the main admin screens by label
                    if (label.equals("Events") || label.equals("Profiles") || 
                        label.equals("Images") || label.equals("Logs")) {
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

        // Setup custom navigation button click listeners
        setupCustomNavigation(graph);

        // Setup navigation destination changed listener
        nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination == null) return;
            updateNavigationSelection(destination);
        });

        // Navigate to events by default - use start destination
        bottomNav.post(() -> {
            int startDestId = graph.getStartDestination();
            if (startDestId != 0) {
                androidx.navigation.NavDestination startDest = graph.findNode(startDestId);
                if (startDest != null) {
                    nav.navigate(startDestId);
                    updateNavigationSelection(startDest);
                }
            }
        });
    }

    private void setupCustomNavigation(androidx.navigation.NavGraph graph) {
        navButtonEvents.setOnClickListener(v -> navigateToDestinationByLabel(graph, "Events"));
        navButtonProfiles.setOnClickListener(v -> navigateToDestinationByLabel(graph, "Profiles"));
        navButtonImages.setOnClickListener(v -> navigateToDestinationByLabel(graph, "Images"));
        navButtonLogs.setOnClickListener(v -> navigateToDestinationByLabel(graph, "Logs"));
    }

    private void navigateToDestinationByLabel(androidx.navigation.NavGraph graph, String label) {
        // Iterate through all nodes in the graph
        for (int i = 0; i < graph.getNodes().size(); i++) {
            int id = graph.getNodes().keyAt(i);
            androidx.navigation.NavDestination dest = graph.findNode(id);
            if (dest != null && dest.getLabel() != null && dest.getLabel().toString().equals(label)) {
                nav.navigate(id);
                updateNavigationSelection(dest);
                break;
            }
        }
    }

    private void updateNavigationSelection(NavDestination destination) {
        if (destination == null) return;
        
        // Dark blue for unselected items (brand color)
        int unselectedColor = android.graphics.Color.parseColor("#223C65");
        // iOS blue color for selected items
        int selectedColor = android.graphics.Color.parseColor("#446EAF");

        // Reset all to unselected (dark circles and gray text)
        navIconEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_dark);
        navIconProfiles.setImageResource(R.drawable.entrant_ic_account_circle_dark);
        navIconImages.setImageResource(R.drawable.entrant_ic_discover_circle_dark);
        navIconLogs.setImageResource(R.drawable.entrant_ic_account_circle_dark);
        navLabelEvents.setTextColor(unselectedColor);
        navLabelProfiles.setTextColor(unselectedColor);
        navLabelImages.setTextColor(unselectedColor);
        navLabelLogs.setTextColor(unselectedColor);

        // Set selected (light circle and blue text) based on destination label
        String label = destination.getLabel() != null ? destination.getLabel().toString() : "";
        if (label.equals("Events")) {
            navIconEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_light);
            navLabelEvents.setTextColor(selectedColor);
        } else if (label.equals("Profiles")) {
            navIconProfiles.setImageResource(R.drawable.entrant_ic_account_circle_light);
            navLabelProfiles.setTextColor(selectedColor);
        } else if (label.equals("Images")) {
            navIconImages.setImageResource(R.drawable.entrant_ic_discover_circle_light);
            navLabelImages.setTextColor(selectedColor);
        } else if (label.equals("Logs")) {
            navIconLogs.setImageResource(R.drawable.entrant_ic_account_circle_light);
            navLabelLogs.setTextColor(selectedColor);
        }
    }


    /**
     * Performs logout: signs out from Firebase, clears Remember Me preferences,
     * and navigates to the welcome/login screen.
     * Public so that admin fragments can call it.
     */
    public void performLogout() {
        // Sign out from Firebase
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signOut();

        // Clear Remember Me preferences
        SharedPreferences prefs = getSharedPreferences("EventEasePrefs", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("rememberMe", false)
            .remove("savedUid")
            .remove("savedEmail")
            .remove("savedPassword")
            .apply();

        // Show logout success message
        ToastUtil.showShort(this, "Logged out successfully");

        // Navigate to MainActivity (which will show welcome/login screen)
        Intent intent = new Intent(this, com.example.eventease.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

