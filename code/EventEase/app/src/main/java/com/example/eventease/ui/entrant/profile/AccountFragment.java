package com.example.eventease.ui.entrant.profile;

import android.Manifest;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.cardview.widget.CardView;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.eventease.R;
import com.example.eventease.notifications.FCMTokenManager;
import com.example.eventease.util.ToastUtil;
import com.example.eventease.ui.organizer.OrganizerMyEventActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

/**
 * Fragment for displaying user account information.
 * Shows profile details and provides navigation to edit profile and logout.
 */
public class AccountFragment extends Fragment {
    private static final String TAG = "AccountFragment";
    private TextView fullNameText;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private CardView organizerSwitchCard;
    private String organizerIdForSwitch;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private TextView notificationStatusText;
    private com.google.android.material.switchmaterial.SwitchMaterial notificationToggle;

    public AccountFragment() { }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize notification permission launcher
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Notification permission granted from account page");
                        // Enable device notifications
                        enableDeviceNotifications();
                        // Save preference
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        if (currentUser != null) {
                            saveNotificationPreference(currentUser.getUid(), true);
                        }
                    } else {
                        Log.w(TAG, "Notification permission denied from account page");
                        // Revert toggle
                        if (notificationToggle != null) {
                            notificationToggle.setOnCheckedChangeListener(null);
                            notificationToggle.setChecked(false);
                            notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                handleNotificationToggle(isChecked);
                            });
                        }
                        // Open settings to allow manual enable
                        openNotificationSettings();
                        updateNotificationStatus();
                    }
                });
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_account, container, false);
        
        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        fullNameText = root.findViewById(R.id.fullNameText);
        profileImage = root.findViewById(R.id.profileImage);
        organizerSwitchCard = root.findViewById(R.id.applyOrganizerCard);
        if (organizerSwitchCard != null) {
            organizerSwitchCard.setVisibility(View.GONE);
            organizerSwitchCard.setOnClickListener(v -> {
                if (getContext() == null) return;
                if (organizerIdForSwitch == null || organizerIdForSwitch.trim().isEmpty()) {
                    ToastUtil.showShort(getContext(), "Organizer profile not ready yet");
                    return;
                }
                Intent intent = new Intent(getContext(), OrganizerMyEventActivity.class);
                intent.putExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID, organizerIdForSwitch);
                startActivity(intent);
            });
        }

        // Set up notification toggle
        notificationToggle = root.findViewById(R.id.notificationToggle);
        if (notificationToggle != null) {
            // Load saved preference
            loadNotificationPreference();
            
            // Set up toggle listener
            notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                handleNotificationToggle(isChecked);
            });
        }
        
        // Set up notification card click listener (for requesting permission if needed)
        CardView notificationsCard = root.findViewById(R.id.notificationsCard);
        if (notificationsCard != null) {
            notificationsCard.setOnClickListener(v -> {
                // If toggle is off, turn it on (which will request permission if needed)
                if (notificationToggle != null && !notificationToggle.isChecked()) {
                    notificationToggle.setChecked(true);
                } else {
                    // If already on, just show status
                    handleNotificationPermissionClick();
                }
            });
        }

        root.findViewById(R.id.logoutButton).setOnClickListener(v -> logout());

        root.findViewById(R.id.deleteProfileButton).setOnClickListener(v -> showDeleteConfirmationDialog());

        root.findViewById(R.id.settingsButton).setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_accountFragment_to_editProfileFragment));
        
        // Load user data with real-time updates
        loadUserData();
        
        // Update notification status on view creation
        updateNotificationStatus();
        
        return root;
    }
    
    /**
     * Handles notification permission click from account page.
     * Checks current permission status and requests if needed.
     */
    private void handleNotificationPermissionClick() {
        if (getContext() == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires runtime permission
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED) {
                // Permission already granted
                ToastUtil.showShort(getContext(), "Notification permission is already enabled ✓");
                updateNotificationStatus();
            } else {
                // Request permission
                Log.d(TAG, "Requesting notification permission from account page");
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Android 12 and below - permission granted via manifest
            ToastUtil.showShort(getContext(), "Notifications are enabled (Android 12 and below)");
        }
    }
    
    /**
     * Loads the user's notification preference from Firestore.
     */
    private void loadNotificationPreference() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || notificationToggle == null) {
            return;
        }
        
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Boolean notificationsEnabled = documentSnapshot.getBoolean("notificationsEnabled");
                        // Default to true if not set
                        boolean enabled = notificationsEnabled != null ? notificationsEnabled : true;
                        
                        // Temporarily remove listener to avoid triggering on load
                        notificationToggle.setOnCheckedChangeListener(null);
                        notificationToggle.setChecked(enabled);
                        notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            handleNotificationToggle(isChecked);
                        });
                        
                        updateNotificationStatus();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load notification preference", e);
                    // Default to enabled
                    if (notificationToggle != null) {
                        notificationToggle.setOnCheckedChangeListener(null);
                        notificationToggle.setChecked(true);
                        notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            handleNotificationToggle(isChecked);
                        });
                    }
                });
    }
    
    /**
     * Handles notification toggle change.
     * This actually controls device notification settings for the app.
     */
    private void handleNotificationToggle(boolean isEnabled) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            if (notificationToggle != null) {
                notificationToggle.setChecked(false);
            }
            ToastUtil.showShort(getContext(), "Please log in to change notification settings");
            return;
        }
        
        if (isEnabled) {
            // If enabling, check and request permission if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                        != PackageManager.PERMISSION_GRANTED) {
                    // Request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    // Don't save preference yet - wait for permission result
                    return;
                }
            }
            
            // Enable notifications at device level
            enableDeviceNotifications();
            
            // Save preference to Firestore
            saveNotificationPreference(currentUser.getUid(), true);
        } else {
            // Disable notifications at device level
            disableDeviceNotifications();
            
            // Save preference to Firestore
            saveNotificationPreference(currentUser.getUid(), false);
        }
    }
    
    /**
     * Enables notifications at the device level by ensuring the notification channel is enabled.
     */
    private void enableDeviceNotifications() {
        if (getContext() == null) return;
        
        // Use centralized channel manager to ensure channel exists and is properly configured
        com.example.eventease.notifications.NotificationChannelManager.createNotificationChannel(getContext());
        
        NotificationManager notificationManager = 
            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager == null) return;
        
        // If notifications are disabled at app level, open settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!notificationManager.areNotificationsEnabled()) {
                openNotificationSettings();
                return;
            }
        }
        
        // Check if channel is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean channelEnabled = com.example.eventease.notifications.NotificationChannelManager.isChannelEnabled(getContext());
            if (!channelEnabled) {
                ToastUtil.showShort(getContext(), "Please enable 'Event Invitations' channel in device settings");
                openNotificationSettings();
                return;
            }
        }
        
        ToastUtil.showShort(getContext(), "Notifications enabled ✓");
        FCMTokenManager.getInstance().initialize();
    }
    
    /**
     * Disables notifications at the device level.
     * 
     * IMPORTANT: We do NOT set the channel to IMPORTANCE_NONE here because:
     * 1. Once set to NONE, Android prevents programmatic re-enabling
     * 2. The user must manually enable it in device settings
     * 3. This causes a poor user experience
     * 
     * Instead, we just save the preference and let the user control it via device settings.
     * The app will respect the user's Firestore preference when sending notifications.
     */
    private void disableDeviceNotifications() {
        if (getContext() == null) return;
        
        // Don't modify the channel - just save the preference
        // The user can control notifications via device settings if they want
        ToastUtil.showShort(getContext(), "Notifications preference saved. You can control notifications in device settings.");
    }
    
    /**
     * Opens device notification settings for this app.
     */
    private void openNotificationSettings() {
        if (getContext() == null) return;
        
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
        } else {
            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
        }
        
        try {
            startActivity(intent);
            ToastUtil.showShort(getContext(), "Please enable notifications in device settings");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open notification settings", e);
            ToastUtil.showShort(getContext(), "Could not open settings. Please enable notifications manually.");
        }
    }
    
    /**
     * Saves notification preference to Firestore.
     */
    private void saveNotificationPreference(String uid, boolean enabled) {
        db.collection("users").document(uid)
                .update("notificationsEnabled", enabled)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Notification preference saved: " + enabled);
                    updateNotificationStatus();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save notification preference", e);
                    ToastUtil.showShort(getContext(), "Failed to save preference. Please try again.");
                    // Revert toggle
                    if (notificationToggle != null) {
                        notificationToggle.setOnCheckedChangeListener(null);
                        notificationToggle.setChecked(!enabled);
                        notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            handleNotificationToggle(isChecked);
                        });
                    }
                });
    }
    
    /**
     * Updates the notification status display in the account page.
     */
    private void updateNotificationStatus() {
        if (getView() == null || getContext() == null) return;
        
        CardView notificationsCard = getView().findViewById(R.id.notificationsCard);
        if (notificationsCard == null) return;
        
        NotificationManager notificationManager = 
            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        
        boolean notificationsEnabled = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationsEnabled = notificationManager != null && notificationManager.areNotificationsEnabled();
        } else {
            notificationsEnabled = true; // Android 6 and below - always enabled
        }
        
        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsEnabled = notificationsEnabled && 
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        
        // Update toggle to match device state
        if (notificationToggle != null) {
            notificationToggle.setOnCheckedChangeListener(null);
            notificationToggle.setChecked(notificationsEnabled);
            notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                handleNotificationToggle(isChecked);
            });
        }
        
        // Update card appearance
        if (notificationsEnabled) {
            notificationsCard.setAlpha(1.0f);
        } else {
            notificationsCard.setAlpha(0.7f);
        }
    }
    
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Get the current user's document in the users collection
            DocumentReference userRef = db.collection("users").document(currentUser.getUid());
            
            // Listen for real-time updates
            userRef.addSnapshotListener((documentSnapshot, e) -> {
                if (e != null) {
                    // Handle any errors
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    // Get and set the user's name
                    String name = documentSnapshot.getString("name");
                    if (name != null && !name.isEmpty()) {
                        fullNameText.setText(name);
                    }
                    
                    // Get and load the profile photo
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
                        // Use Glide to load and cache the image
                        Glide.with(getContext())
                            .load(photoUrl)
                            .placeholder(R.drawable.entrant_icon)
                            .error(R.drawable.entrant_icon)
                            .into(profileImage);
                    }

                    updateOrganizerSwitchVisibility(documentSnapshot);
                }
            });
        }
    }

    private void updateOrganizerSwitchVisibility(@NonNull DocumentSnapshot documentSnapshot) {
        if (organizerSwitchCard == null) return;

        boolean hasOrganizerRole = hasRole(documentSnapshot, "organizer");
        boolean hasEntrantRole = hasRole(documentSnapshot, "entrant");

        if (hasOrganizerRole && hasEntrantRole) {
            organizerIdForSwitch = documentSnapshot.getString("organizerId");
            if (organizerIdForSwitch == null || organizerIdForSwitch.trim().isEmpty()) {
                organizerIdForSwitch = documentSnapshot.getId();
            }
            organizerSwitchCard.setVisibility(View.VISIBLE);
        } else {
            organizerIdForSwitch = null;
            organizerSwitchCard.setVisibility(View.GONE);
        }
    }

    private boolean hasRole(@NonNull DocumentSnapshot documentSnapshot, @NonNull String targetRole) {
        Object rolesObj = documentSnapshot.get("roles");
        if (rolesObj instanceof List<?>) {
            for (Object role : (List<?>) rolesObj) {
                if (role != null && targetRole.equalsIgnoreCase(role.toString())) {
                    return true;
                }
            }
        }

        String roleField = documentSnapshot.getString("role");
        if (roleField != null && roleField.toLowerCase(Locale.US).contains(targetRole.toLowerCase(Locale.US))) {
            return true;
        }

        return false;
    }

    private void logout() {
        if (getContext() == null) return;

        // Sign out from Firebase
        mAuth.signOut();

        // Clear Remember Me preferences
        SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean("rememberMe", false)
            .remove("savedUid")
            .remove("savedEmail")
            .remove("savedPassword")
            .apply();

        ToastUtil.showShort(getContext(), "Logged out successfully");

        // Hide bottom nav and top bar before navigating
        if (getActivity() != null) {
            View bottomNav = getActivity().findViewById(R.id.include_bottom);
            View topBar = getActivity().findViewById(R.id.include_top);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
            if (topBar != null) {
                topBar.setVisibility(View.GONE);
            }
        }

        // Navigate to welcome screen
        try {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_accountFragment_to_welcomeFragment);
            }
        } catch (Exception e) {
            ToastUtil.showLong(getContext(), "Navigation error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDeleteConfirmationDialog() {
        if (getContext() == null) {
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_delete_profile_confirmation);
        dialog.setCanceledOnTouchOutside(false);

        // Set window properties for full screen blur
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Capture screenshot and blur it for the background
        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }
        
        // Make the background clickable to dismiss
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }

        AppCompatButton btnNo = dialog.findViewById(R.id.btnNo);
        AppCompatButton btnYes = dialog.findViewById(R.id.btnYes);

        if (btnNo != null) {
            btnNo.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnYes != null) {
            btnYes.setOnClickListener(v -> {
                dialog.dismiss();
                deleteProfile();
            });
        }

        dialog.show();
        
        // Apply animations after dialog is shown
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }

    private Bitmap captureScreenshot() {
        try {
            if (getActivity() == null || getActivity().getWindow() == null) return null;
            android.view.View rootView = getActivity().getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null || getContext() == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(getContext());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            
            rs.destroy();
            
            // Scale back up
            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private void deleteProfile() {
        if (getContext() == null) return;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            ToastUtil.showLong(getContext(), "Not signed in");
            return;
        }

        String uid = currentUser.getUid();
        
        ToastUtil.showShort(getContext(), "Deleting profile...");

        ProfileDeletionHelper deletionHelper = new ProfileDeletionHelper(getContext());
        deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
            @Override
            public void onDeletionComplete() {
                deleteUserDocumentAndAuth(uid);
            }

            @Override
            public void onDeletionFailure(String error) {
                android.util.Log.e("AccountFragment", "Failed to delete user references: " + error);
                deleteUserDocumentAndAuth(uid);
            }
        });
    }

    private void deleteUserDocumentAndAuth(String uid) {
        // Delete user document from Firestore
        DocumentReference userRef = db.collection("users").document(uid);
        userRef.delete()
            .addOnSuccessListener(aVoid -> {
                // Delete Firebase Auth account
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null) {
                    currentUser.delete()
                        .addOnSuccessListener(aVoid1 -> {
                            // Sign out and clear preferences
                            mAuth.signOut();
                            clearPreferences();
                            ToastUtil.showShort(getContext(), "Profile deleted successfully");
                            navigateToWelcome();
                        })
                        .addOnFailureListener(e -> {
                            // Even if auth deletion fails, sign out and clear preferences
                            android.util.Log.e("AccountFragment", "Failed to delete auth account", e);
                            mAuth.signOut();
                            clearPreferences();
                            ToastUtil.showShort(getContext(), "Profile deleted (some cleanup may be pending)");
                            navigateToWelcome();
                        });
                } else {
                    // User already signed out, just clear preferences
                    clearPreferences();
                    ToastUtil.showShort(getContext(), "Profile deleted successfully");
                    navigateToWelcome();
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("AccountFragment", "Failed to delete user document", e);
                ToastUtil.showLong(getContext(), "Failed to delete profile: " + e.getMessage());
            });
    }

    private void clearPreferences() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean("rememberMe", false)
            .remove("savedUid")
            .remove("savedEmail")
            .remove("savedPassword")
            .apply();
    }

    private void navigateToWelcome() {
        try {
            // Hide bottom nav and top bar before navigating
            if (getActivity() != null) {
                View bottomNav = getActivity().findViewById(R.id.include_bottom);
                View topBar = getActivity().findViewById(R.id.include_top);
                if (bottomNav != null) {
                    bottomNav.setVisibility(View.GONE);
                }
                if (topBar != null) {
                    topBar.setVisibility(View.GONE);
                }
            }
            
            if (isAdded() && getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_accountFragment_to_welcomeFragment);
            }
        } catch (Exception e) {
            ToastUtil.showLong(getContext(), "Navigation error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
