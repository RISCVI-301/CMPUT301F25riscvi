package com.example.eventease;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.example.eventease.R;
import com.example.eventease.auth.UserRoleChecker;
import com.example.eventease.notifications.FCMTokenManager;
import com.example.eventease.notifications.InvitationNotificationListener;
import com.example.eventease.ui.organizer.AutomaticEntrantSelectionService;

/**
 * Main activity that hosts navigation fragments and manages bottom navigation for entrant users.
 * Handles authentication state, role-based navigation, and controls visibility of navigation bars.
 * This activity serves as the entry point for authenticated entrant users and manages navigation
 * between the Discover, My Events, and Account fragments.
 * 
 * <p>On startup, this activity checks if the user is an admin and redirects to AdminMainActivity if so.
 * Otherwise, it sets up the standard entrant navigation flow with bottom navigation.
 */
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

    private InvitationNotificationListener invitationListener;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private static boolean listenersInitialized = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //FireBase
        FirebaseApp.initializeApp(this);
        Log.d("FirebaseTest", "FirebaseApp initialized: " + (FirebaseApp.getApps(this).size() > 0));

        setContentView(R.layout.entrant_activity_mainfragments);

        // Initialize notification permission launcher
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d("MainActivity", "Notification permission granted");
                        // Initialize FCM after permission is granted
                        initializeNotifications();
                    } else {
                        Log.w("MainActivity", "Notification permission denied");
                        // Still initialize FCM - notifications may work on older Android versions
                        initializeNotifications();
                    }
                }
        );

        // Request notification permission if needed (Android 13+)
        requestNotificationPermission();

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
        
        // Setup navigation listener (always needed for entrant flow)
        setupEntrantNavigation();
        
        // If logged in, check if user is admin and redirect accordingly
        if (isLoggedIn && currentUser != null) {
            UserRoleChecker.isAdmin().addOnCompleteListener(task -> {
                if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                    // User is admin - redirect to admin flow
                    Intent adminIntent = new Intent(MainActivity.this, com.example.eventease.admin.AdminMainActivity.class);
                    startActivity(adminIntent);
                    finish();
                    return;
                }
                // User is not admin - navigate to discover
                navigateToDiscover();
            });
        } else {
            // User not logged in - hide bars initially
            bottomNav.setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
        }

        // Handle external navigation intents (from detail activities)
        handleExternalNav(getIntent());
        
        // ============================================================
        // TEMPORARY: Debug button for workflow testing
        // TODO: REMOVE BEFORE PRODUCTION
        // ============================================================
        addDebugTestButton();
    }

    /**
     * TEMPORARY: Adds a floating debug button to launch workflow test.
     * Remove this method before production deployment.
     */
    private void addDebugTestButton() {
        android.widget.Button testBtn = new android.widget.Button(this);
        testBtn.setText("🧪 Test");
        testBtn.setTextSize(10);
        testBtn.setPadding(16, 8, 16, 8);
        
        // Position in top-right corner
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        params.topMargin = 100;
        params.rightMargin = 16;
        testBtn.setLayoutParams(params);
        
        // Style
        testBtn.setBackgroundColor(0xFF4CAF50); // Green
        testBtn.setTextColor(0xFFFFFFFF); // White
        testBtn.setAlpha(0.8f);
        
        // Click handler
        testBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.eventease.test.WorkflowTestActivity.class);
            startActivity(intent);
        });
        
        // Add to root view
        ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(testBtn);
        
        Log.d("MainActivity", "🧪 Debug test button added - REMOVE BEFORE PRODUCTION");
    }
    // ============================================================
    // END TEMPORARY CODE
    // ============================================================

    private void setupEntrantNavigation() {
        // Single unified listener that handles visibility and selection for all navigation
        nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination == null) return;

            int id = destination.getId();

            // Check if user is authenticated - simply check if Firebase Auth has a current user
            // "Remember Me" only affects persistence across app restarts, not current authentication state
            FirebaseAuth authCheck = FirebaseAuth.getInstance();
            com.google.firebase.auth.FirebaseUser currentUserCheck = authCheck.getCurrentUser();
            boolean isAuthenticated = currentUserCheck != null;

            // Main app screens (discover, my events, account) - show bars if authenticated
            if (id == R.id.discoverFragment || id == R.id.myEventsFragment || id == R.id.accountFragment
                || id == R.id.eventsSelectionFragment || id == R.id.previousEventsFragment || id == R.id.upcomingEventsFragment) {
                if (isAuthenticated) {
                    // User is authenticated, show top bar and bottom nav
                    bottomNav.setVisibility(View.VISIBLE);
                    topBar.setVisibility(View.VISIBLE);
                    updateNavigationSelection(id);

                    // Initialize listeners only once to prevent duplicates
                    if (!listenersInitialized) {
                        // Initialize FCM token manager and invitation listener
                        if (invitationListener == null) {
                            FCMTokenManager.getInstance().initialize();
                            invitationListener = new InvitationNotificationListener(this);
                            invitationListener.startListening();
                        }
                        
                        // Setup automatic entrant selection listener (only once)
                        AutomaticEntrantSelectionService.setupAutomaticSelectionListener();
                        
                        // Setup automatic deadline processor service (only once)
                        com.example.eventease.ui.organizer.AutomaticDeadlineProcessorService.setupDeadlineProcessorListener();
                        
                        // Setup automatic sorry notification service (only once)
                        com.example.eventease.ui.organizer.SorryNotificationService.setupSorryNotificationListener();
                        
                        listenersInitialized = true;
                        Log.d("MainActivity", "Listeners initialized once");
                    }
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
    }

    private void navigateToDiscover() {
        // User is logged in on startup - show bars and navigate to Discover
        topBar.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
        // Navigate to discover after the view is ready and mark selected
        bottomNav.post(() -> {
            nav.navigate(R.id.discoverFragment);
            updateNavigationSelection(R.id.discoverFragment);
        });

        // Initialize notifications (permission already requested in onCreate)
        initializeNotifications();
    }
    
    /**
     * Requests notification permission for Android 13+ (API 33+).
     * On older versions, permission is granted automatically via manifest.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires runtime permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting notification permission");
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d("MainActivity", "Notification permission already granted");
                initializeNotifications();
            }
        } else {
            // Android 12 and below - permission granted via manifest
            Log.d("MainActivity", "Android version < 13, notification permission granted via manifest");
            initializeNotifications();
        }
    }
    
    /**
     * Initializes FCM token manager and invitation listener.
     * Should be called after notification permission is granted (or on older Android versions).
     */
    private void initializeNotifications() {
        // TEMPORARY FIX: Force recreate notification channel with HIGH importance
        // This fixes the issue where old channel was created with DEFAULT importance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) {
                android.app.NotificationChannel existingChannel = nm.getNotificationChannel("event_invitations");
                if (existingChannel != null && existingChannel.getImportance() < android.app.NotificationManager.IMPORTANCE_HIGH) {
                    Log.d("MainActivity", "Deleting old notification channel with low importance");
                    nm.deleteNotificationChannel("event_invitations");
                    Log.d("MainActivity", "Old channel deleted, will be recreated with HIGH importance");
                }
            }
        }
        
        // This will create the channel with HIGH importance (after our fix)
        com.example.eventease.notifications.NotificationChannelManager.createNotificationChannel(this);
        
        FCMTokenManager.getInstance().initialize();
        invitationListener = new InvitationNotificationListener(this);
        invitationListener.startListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (invitationListener != null) {
            invitationListener.stopListening();
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleExternalNav(intent);
    }

    private void handleExternalNav(android.content.Intent intent) {
        if (intent == null) return;
        
        // Check if notification contains eventId - open EventDetailActivity directly
        String eventId = intent.getStringExtra("eventId");
        if (eventId != null && !eventId.isEmpty()) {
            Log.d("MainActivity", "Opening EventDetailActivity for invitation - eventId: " + eventId);
            // Open EventDetailActivity directly where user can accept/decline
            Intent detailIntent = new Intent(this, com.example.eventease.ui.entrant.eventdetail.EventDetailActivity.class);
            detailIntent.putExtra("eventId", eventId);
            startActivity(detailIntent);
            return;
        }
        
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
