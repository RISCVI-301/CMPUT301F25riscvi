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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.eventease.R;
import com.example.eventease.auth.ProfileSetupActivity;
import com.example.eventease.notifications.FCMTokenManager;
import com.example.eventease.util.ToastUtil;
import com.example.eventease.ui.organizer.OrganizerMyEventActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private CardView organizerSwitchCard;
    private String organizerIdForSwitch;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private View notificationBadge;
    private ListenerRegistration notificationBadgeListener;

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
                        // Show preferences dialog after permission is granted
                        showNotificationPreferencesDialog();
                    } else {
                        Log.w(TAG, "Notification permission denied from account page");
                        // Show message
                        ToastUtil.showShort(getContext(), "Notification permission is required to enable notifications");
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

        // Set up notification preferences card click listener
        CardView notificationPreferencesCard = root.findViewById(R.id.notificationPreferencesCard);
        if (notificationPreferencesCard != null) {
            notificationPreferencesCard.setOnClickListener(v -> {
                // First check/request permission, then show preferences dialog
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                            != PackageManager.PERMISSION_GRANTED) {
                        // Request permission first
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                        return;
                    }
                }
                // Permission granted or Android < 13, show preferences dialog
                showNotificationPreferencesDialog();
            });
        }

        root.findViewById(R.id.deleteProfileButton).setOnClickListener(v -> showDeleteConfirmationDialog());

        root.findViewById(R.id.settingsButton).setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_accountFragment_to_editProfileFragment));
        
        // Set up notification bell button
        android.widget.ImageView notificationBellButton = root.findViewById(R.id.notificationBellButton);
        notificationBadge = root.findViewById(R.id.notificationBadge);
        
        if (notificationBellButton != null) {
            notificationBellButton.setOnClickListener(v -> {
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), com.example.eventease.ui.entrant.notifications.NotificationsActivity.class);
                    startActivity(intent);
                }
            });
        }
        
        // Check for new notifications and update badge
        checkForNewNotifications();
        
        // Load user data with real-time updates
        loadUserData();
        
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
                // Enable notifications and save preference
                enableDeviceNotifications();
                String uid = com.example.eventease.auth.AuthHelper.getUid(requireContext());
                if (uid != null && !uid.isEmpty()) {
                    saveNotificationPreference(uid, true);
                }
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
        if (getContext() != null) {
            FCMTokenManager.getInstance().initialize(getContext());
        }
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
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save notification preference", e);
                    ToastUtil.showShort(getContext(), "Failed to save preference. Please try again.");
                });
    }
    
    /**
     * Shows the notification preferences dialog with toggles for invited and not invited notifications.
     */
    private void showNotificationPreferencesDialog() {
        if (getContext() == null) {
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_notification_preferences);
        dialog.setCanceledOnTouchOutside(true);

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

        // Get switches
        com.google.android.material.switchmaterial.SwitchMaterial switchInvited = dialog.findViewById(R.id.switchInvited);
        com.google.android.material.switchmaterial.SwitchMaterial switchNotInvited = dialog.findViewById(R.id.switchNotInvited);
        AppCompatButton btnSave = dialog.findViewById(R.id.btnDialogSave);

        // Load current preferences
        loadNotificationPreferences(switchInvited, switchNotInvited);

        // Set up save button
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (switchInvited != null && switchNotInvited != null) {
                    boolean invitedEnabled = switchInvited.isChecked();
                    boolean notInvitedEnabled = switchNotInvited.isChecked();
                    saveNotificationPreferences(invitedEnabled, notInvitedEnabled);
                    dialog.dismiss();
                }
            });
        }

        dialog.show();

        // Apply animations after dialog is shown
        androidx.cardview.widget.CardView card = dialog.findViewById(R.id.dialogCardView);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);

            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }

    /**
     * Loads notification preferences from Firestore and sets the switches.
     */
    private void loadNotificationPreferences(com.google.android.material.switchmaterial.SwitchMaterial switchInvited,
                                           com.google.android.material.switchmaterial.SwitchMaterial switchNotInvited) {
        String currentUserId = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (currentUserId == null || currentUserId.isEmpty() || switchInvited == null || switchNotInvited == null) {
            // Default to both enabled if no user ID
            if (switchInvited != null) {
                switchInvited.setChecked(true);
            }
            if (switchNotInvited != null) {
                switchNotInvited.setChecked(true);
            }
            return;
        }

        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        // Default to true if not set (receive all notifications)
                        Boolean invitedEnabled = documentSnapshot.getBoolean("notificationPreferenceInvited");
                        Boolean notInvitedEnabled = documentSnapshot.getBoolean("notificationPreferenceNotInvited");

                        boolean invited = invitedEnabled != null ? invitedEnabled : true;
                        boolean notInvited = notInvitedEnabled != null ? notInvitedEnabled : true;

                        switchInvited.setChecked(invited);
                        switchNotInvited.setChecked(notInvited);
                    } else {
                        // Default to both enabled
                        if (switchInvited != null) {
                            switchInvited.setChecked(true);
                        }
                        if (switchNotInvited != null) {
                            switchNotInvited.setChecked(true);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load notification preferences", e);
                    // Default to both enabled on error
                    if (switchInvited != null) {
                        switchInvited.setChecked(true);
                    }
                    if (switchNotInvited != null) {
                        switchNotInvited.setChecked(true);
                    }
                });
    }

    /**
     * Saves notification preferences to Firestore.
     */
    private void saveNotificationPreferences(boolean invitedEnabled, boolean notInvitedEnabled) {
        String currentUserId = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (currentUserId == null || currentUserId.isEmpty()) {
            ToastUtil.showShort(getContext(), "Please complete your profile to save preferences");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationPreferenceInvited", invitedEnabled);
        updates.put("notificationPreferenceNotInvited", notInvitedEnabled);

        db.collection("users").document(currentUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Notification preferences saved: invited=" + invitedEnabled + ", notInvited=" + notInvitedEnabled);
                    ToastUtil.showShort(getContext(), "Notification preferences saved");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save notification preferences", e);
                    ToastUtil.showShort(getContext(), "Failed to save preferences. Please try again.");
                });
    }
    
    /**
     * Checks for new notifications and updates the badge visibility.
     * Listens to notificationRequests and invitations collections in real-time.
     */
    private void checkForNewNotifications() {
        String currentUserId = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (currentUserId == null || currentUserId.isEmpty() || getContext() == null) {
            return;
        }

        String uid = currentUserId;
        SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        long lastSeenTime = prefs.getLong("lastNotificationSeenTime", 0);
        
        Log.d(TAG, "Setting up real-time notification badge listener for user: " + uid);
        Log.d(TAG, "Initial last seen time: " + lastSeenTime);

        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
        }

        notificationBadgeListener = db.collection("notificationRequests")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error in notification badge listener", e);
                        return;
                    }
                    
                    if (querySnapshot == null) {
                        Log.w(TAG, "Query snapshot is null");
                        return;
                    }
                    
                    if (getView() == null) {
                        Log.w(TAG, "View is null, cannot update badge");
                        return;
                    }

                    Log.d(TAG, "Real-time update: notificationRequests changed, checking " + querySnapshot.size() + " documents");

                    SharedPreferences currentPrefs = getContext() != null ? 
                        getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE) : null;
                    if (currentPrefs == null) {
                        Log.w(TAG, "Cannot get SharedPreferences");
                        return;
                    }
                    
                    long currentLastSeenTime = currentPrefs.getLong("lastNotificationSeenTime", 0);
                    Log.d(TAG, "Current last seen time from prefs: " + currentLastSeenTime);

                    boolean hasNewNotifications = false;
                    int matchingCount = 0;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        Object userIdsObj = doc.get("userIds");
                        Long createdAt = doc.getLong("createdAt");
                        
                        if (createdAt != null && createdAt > currentLastSeenTime && userIdsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> userIds = (List<String>) userIdsObj;
                            if (userIds.contains(uid)) {
                                hasNewNotifications = true;
                                matchingCount++;
                                Log.d(TAG, "Found new notificationRequest: " + doc.getId() + 
                                    " created at " + createdAt + " (newer than " + currentLastSeenTime + ")");
                            }
                        }
                    }

                    Log.d(TAG, "Found " + matchingCount + " new notificationRequests for this user");

                    final boolean hasNotificationRequests = hasNewNotifications;
                    
                    db.collection("invitations")
                            .whereEqualTo("uid", uid)
                            .whereEqualTo("status", "PENDING")
                            .get()
                            .addOnSuccessListener(invitationSnapshot -> {
                                Log.d(TAG, "Checking " + invitationSnapshot.size() + " pending invitations");
                                
                                boolean hasNewInvitations = false;
                                int newInvCount = 0;
                                for (com.google.firebase.firestore.DocumentSnapshot invDoc : invitationSnapshot.getDocuments()) {
                                    Long issuedAt = invDoc.getLong("issuedAt");
                                    if (issuedAt != null && issuedAt > currentLastSeenTime) {
                                        hasNewInvitations = true;
                                        newInvCount++;
                                        Log.d(TAG, "Found new invitation: " + invDoc.getId() + 
                                            " issued at " + issuedAt + " (newer than " + currentLastSeenTime + ")");
                                    }
                                }
                                
                                Log.d(TAG, "Found " + newInvCount + " new invitations");
                                
                                boolean shouldShowBadge = hasNotificationRequests || hasNewInvitations;
                                
                                Log.d(TAG, "Badge decision: shouldShowBadge=" + shouldShowBadge + 
                                    " (notificationRequests=" + hasNotificationRequests + 
                                    ", invitations=" + hasNewInvitations + ")");
                                
                                if (getView() != null && notificationBadge != null) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (notificationBadge != null) {
                                                notificationBadge.setVisibility(shouldShowBadge ? View.VISIBLE : View.GONE);
                                                Log.d(TAG, "Badge visibility updated to: " + 
                                                    (shouldShowBadge ? "VISIBLE" : "GONE"));
                                            }
                                        });
                                    }
                                }
                            })
                            .addOnFailureListener(error -> {
                                Log.e(TAG, "Failed to check invitations for badge", error);
                                // Still update badge based on notificationRequests
                                if (getView() != null && notificationBadge != null) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (notificationBadge != null) {
                                                notificationBadge.setVisibility(hasNotificationRequests ? View.VISIBLE : View.GONE);
                                            }
                                        });
                                    }
                                }
                            });
                });
    }
    
    private void loadUserData() {
        String currentUserId = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (currentUserId != null && !currentUserId.isEmpty()) {
            // Get the current user's document in the users collection
            DocumentReference userRef = db.collection("users").document(currentUserId);
            
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

        String currentUserId = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (currentUserId == null || currentUserId.isEmpty()) {
            ToastUtil.showLong(getContext(), "Profile not set up");
            return;
        }

        String uid = currentUserId;
        
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
                // Device auth - clear cache to trigger profile setup on next launch
                com.example.eventease.auth.DeviceAuthManager authManager = 
                    new com.example.eventease.auth.DeviceAuthManager(requireContext());
                authManager.clearCache();
                clearPreferences();
                ToastUtil.showShort(getContext(), "Profile deleted successfully");
                launchProfileSetupScreen();
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("AccountFragment", "Failed to delete user document", e);
                ToastUtil.showLong(getContext(), "Failed to delete profile: " + e.getMessage());
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
            notificationBadgeListener = null;
        }
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

    private void launchProfileSetupScreen() {
        if (getContext() == null) return;
        Intent intent = new Intent(requireContext(), ProfileSetupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
