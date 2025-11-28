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
        
        // Hide content initially until profile check completes
        final View rootView = findViewById(R.id.main_root);
        if (rootView != null) {
            rootView.setVisibility(View.GONE);
        }

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

        // === DEVICE ID AUTHENTICATION (NO PASSWORDS) ===
        // Check if device has a profile
        com.example.eventease.auth.DeviceAuthManager authManager = new com.example.eventease.auth.DeviceAuthManager(this);
        
        authManager.hasProfile().addOnCompleteListener(task -> {
            if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                // Profile exists - user is "logged in"
                Log.d("MainActivity", "Device has profile, proceeding to main app");
                
                // Show content now that profile check passed
                if (rootView != null) {
                    rootView.setVisibility(View.VISIBLE);
                }
                
                // Update last seen
                authManager.updateLastSeen();
                
                // Setup navigation
                setupCustomNavigation();
                setupEntrantNavigation();
                
                // Check if user is admin and redirect accordingly
                authManager.isAdmin().addOnCompleteListener(adminTask -> {
                    if (adminTask.isSuccessful() && Boolean.TRUE.equals(adminTask.getResult())) {
                        // User is admin - redirect to admin flow
                        Log.d("MainActivity", "User is admin, redirecting to AdminMainActivity");
                        Intent adminIntent = new Intent(MainActivity.this, com.example.eventease.admin.AdminMainActivity.class);
                        startActivity(adminIntent);
                        finish();
                        return;
                    }
                    
                    // User is not admin - navigate to discover (entrant/organizer flow)
                    Log.d("MainActivity", "User is entrant/organizer, showing main app");
                    navigateToDiscover();
                });
                
            } else {
                // No profile exists - first time user, redirect to profile setup
                Log.d("MainActivity", "No profile found, redirecting to ProfileSetupActivity");
                Intent setupIntent = new Intent(MainActivity.this, com.example.eventease.auth.ProfileSetupActivity.class);
                startActivity(setupIntent);
                finish();
            }
        });

        // Handle external navigation intents (from detail activities)
        handleExternalNav(getIntent());
    }

    private void setupEntrantNavigation() {
        // Single unified listener that handles visibility and selection for all navigation
        nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination == null) return;

            int id = destination.getId();

            // Check if user is authenticated - check if device has profile
            com.example.eventease.auth.DeviceAuthManager authCheck = new com.example.eventease.auth.DeviceAuthManager(MainActivity.this);
            boolean isAuthenticated = authCheck.hasCachedProfile();

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
                        // Initialize FCM token manager
                        FCMTokenManager.getInstance().initialize();
                        
                        // DISABLED: InvitationNotificationListener
                        // Cloud Functions already send personalized FCM notifications when invitations are created.
                        // Local notifications from InvitationNotificationListener were causing duplicates.
                        // We rely exclusively on Cloud Functions for notifications to ensure consistency.
                        // if (invitationListener == null) {
                        //     FCMTokenManager.getInstance().initialize(MainActivity.this);
                        //     invitationListener = new InvitationNotificationListener(this);
                        //     invitationListener.startListening();
                        // }
                        
                        // DISABLED: Automatic entrant selection listener
                        // We rely on Cloud Function (processAutomaticEntrantSelection) instead
                        // to avoid race conditions and ensure reliability even when app is closed.
                        // Cloud Function runs every 1 minute via Cloud Scheduler.
                        // AutomaticEntrantSelectionService.setupAutomaticSelectionListener();
                        
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
     * Initializes FCM token manager.
     * Should be called after notification permission is granted (or on older Android versions).
     * Note: Notifications are sent via Cloud Functions, not local listeners, to avoid duplicates.
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
        
        // Verify notification channel is properly set up
        verifyNotificationSetup();
        
        FCMTokenManager.getInstance().initialize(this);
        
        // DISABLED: InvitationNotificationListener
        // Cloud Functions already send personalized FCM notifications when invitations are created.
        // Local notifications from InvitationNotificationListener were causing duplicates.
        // We rely exclusively on Cloud Functions for notifications to ensure consistency.
        // if (invitationListener == null) {
        //     invitationListener = new InvitationNotificationListener(this);
        //     invitationListener.startListening();
        // }
    }
    
    /**
     * Verifies that notification setup is correct and logs diagnostic information.
     * This helps debug notification issues, especially on emulators.
     */
    private void verifyNotificationSetup() {
        Log.d("MainActivity", "=== Verifying Notification Setup ===");
        
        // Check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
            Log.d("MainActivity", "POST_NOTIFICATIONS permission: " + (hasPermission ? "GRANTED" : "DENIED"));
        } else {
            Log.d("MainActivity", "Android < 13, permission granted via manifest");
        }
        
        // Check notification manager
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (nm == null) {
            Log.e("MainActivity", "NotificationManager is NULL - notifications will not work!");
            return;
        }
        
        // Check if notifications are enabled for the app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean appNotificationsEnabled = nm.areNotificationsEnabled();
            Log.d("MainActivity", "App notifications enabled: " + appNotificationsEnabled);
            if (!appNotificationsEnabled) {
                Log.w("MainActivity", "⚠ WARNING: Notifications are disabled for this app!");
                Log.w("MainActivity", "  User needs to enable in: Settings → Apps → EventEase → Notifications");
            }
        }
        
        // Check notification channel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = nm.getNotificationChannel("event_invitations");
            if (channel == null) {
                Log.e("MainActivity", "⚠ WARNING: Notification channel 'event_invitations' does not exist!");
                Log.e("MainActivity", "  Attempting to create it now...");
                com.example.eventease.notifications.NotificationChannelManager.createNotificationChannel(this);
                // Re-check after creation
                channel = nm.getNotificationChannel("event_invitations");
            }
            
            if (channel != null) {
                int importance = channel.getImportance();
                Log.d("MainActivity", "Notification channel 'event_invitations':");
                Log.d("MainActivity", "  - Importance: " + importance + " (0=NONE, 1=MIN, 2=LOW, 3=DEFAULT, 4=HIGH)");
                Log.d("MainActivity", "  - Enabled: " + (importance != android.app.NotificationManager.IMPORTANCE_NONE));
                Log.d("MainActivity", "  - Sound: " + (channel.getSound() != null ? channel.getSound().toString() : "none"));
                Log.d("MainActivity", "  - Vibration: " + channel.shouldVibrate());
                Log.d("MainActivity", "  - Lights: " + channel.shouldShowLights());
                
                if (importance == android.app.NotificationManager.IMPORTANCE_NONE) {
                    Log.e("MainActivity", "⚠ CRITICAL: Notification channel is BLOCKED (importance = NONE)!");
                    Log.e("MainActivity", "  Notifications will NOT be displayed!");
                    Log.e("MainActivity", "  Solution: Settings → Apps → EventEase → Notifications → Event Invitations");
                } else if (importance < android.app.NotificationManager.IMPORTANCE_HIGH) {
                    Log.w("MainActivity", "⚠ WARNING: Notification channel has low importance (" + importance + ")");
                    Log.w("MainActivity", "  Notifications may not show as heads-up notifications");
                }
            }
        }
        
        // Check FCM token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        Log.d("MainActivity", "FCM Token: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "NULL"));
                        Log.d("MainActivity", "FCM Token length: " + (token != null ? token.length() : 0));
                    } else {
                        Log.e("MainActivity", "Failed to get FCM token: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        Log.e("MainActivity", "  This may indicate Google Play Services is not available (common on emulators)");
                    }
                });
        
        Log.d("MainActivity", "=== Notification Setup Verification Complete ===");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // DISABLED: InvitationNotificationListener is no longer used
        // if (invitationListener != null) {
        //     invitationListener.stopListening();
        // }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleExternalNav(intent);
    }

    private void handleExternalNav(android.content.Intent intent) {
        if (intent == null) return;
        
        // Handle deep link from QR code (https://eventease.app/event/{eventId} or eventease://event/{eventId})
        android.net.Uri data = intent.getData();
        if (data != null) {
            String eventId = null;
            String scheme = data.getScheme();
            String host = data.getHost();
            String path = data.getPath();
            String fullUri = data.toString();
            
            Log.d("MainActivity", "Deep link received - Scheme: " + scheme + ", Host: " + host + ", Path: " + path + ", Full URI: " + fullUri);
            
            // Handle HTTP URL format: https://eventease.app/event/{eventId}
            if ("https".equals(scheme) && "eventease.app".equals(host)) {
                if (path != null) {
                    if (path.startsWith("/event/")) {
                        eventId = path.substring(7); // Remove leading "/event/"
                    } else if (path.startsWith("/event")) {
                        eventId = path.substring(6); // Remove leading "/event"
                        if (eventId.startsWith("/")) {
                            eventId = eventId.substring(1); // Remove any remaining "/"
                        }
                    }
                }
            }
            // Handle custom scheme format: eventease://event/{eventId}
            else if ("eventease".equals(scheme) && "event".equals(host)) {
                if (path != null) {
                    // Remove leading "/" if present
                    if (path.startsWith("/")) {
                        eventId = path.substring(1);
                    } else {
                        eventId = path;
                    }
                } else {
                    // If no path, try to get from query or use the full URI
                    // Some QR scanners might encode it differently
                    String query = data.getQuery();
                    if (query != null && query.startsWith("id=")) {
                        eventId = query.substring(3);
                    }
                }
            }
            
            // Clean up eventId - remove any trailing slashes or whitespace
            if (eventId != null) {
                eventId = eventId.trim();
                if (eventId.endsWith("/")) {
                    eventId = eventId.substring(0, eventId.length() - 1);
                }
            }
            
            Log.d("MainActivity", "Extracted eventId: " + eventId);
            
            if (eventId != null && !eventId.isEmpty()) {
                Log.d("MainActivity", "Opening EventDetailActivity from QR code - eventId: " + eventId);
                // Open EventDetailActivity directly
                Intent detailIntent = new Intent(this, com.example.eventease.ui.entrant.eventdetail.EventDetailActivity.class);
                detailIntent.putExtra("eventId", eventId);
                startActivity(detailIntent);
                return;
            } else {
                Log.w("MainActivity", "Could not extract eventId from deep link: " + fullUri);
            }
        }
        
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
