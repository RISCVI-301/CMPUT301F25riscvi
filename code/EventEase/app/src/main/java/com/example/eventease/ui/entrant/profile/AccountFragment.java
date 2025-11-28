package com.example.eventease.ui.entrant.profile;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
import com.example.eventease.util.ToastUtil;
import com.example.eventease.ui.organizer.OrganizerMyEventActivity;
import com.example.eventease.ui.organizer.NotificationHelper;
import com.example.eventease.notifications.NotificationChannelManager;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.Arrays;
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
    private FirebaseAuth mAuth;
    private CardView organizerSwitchCard;
    private String organizerIdForSwitch;
    private View notificationBadge;
    private com.google.firebase.firestore.ListenerRegistration notificationBadgeListener;

    public AccountFragment() { }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
            notificationBadgeListener = null;
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
            notificationBadgeListener = null;
        }
        checkForNewNotifications();
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


        // Set up notification preferences card click listener
        CardView notificationPreferencesCard = root.findViewById(R.id.notificationPreferencesCard);
        if (notificationPreferencesCard != null) {
            notificationPreferencesCard.setOnClickListener(v -> {
                showNotificationPreferencesDialog();
            });
        }

        root.findViewById(R.id.logoutButton).setOnClickListener(v -> logout());

        root.findViewById(R.id.deleteProfileButton).setOnClickListener(v -> showDeleteConfirmationDialog());

        root.findViewById(R.id.settingsButton).setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_accountFragment_to_editProfileFragment));

        ImageView notificationBellButton = root.findViewById(R.id.notificationBellButton);
        notificationBadge = root.findViewById(R.id.notificationBadge);
        
        if (notificationBellButton != null) {
            notificationBellButton.setOnClickListener(v -> {
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), com.example.eventease.ui.entrant.notifications.NotificationsActivity.class);
                    startActivity(intent);
                }
            });
        }
        
        checkForNewNotifications();

        CardView testNotificationButton = root.findViewById(R.id.testNotificationButton);
        if (testNotificationButton != null) {
            testNotificationButton.setOnClickListener(v -> sendTestNotificationToSelf());
        }
        
        // Load user data with real-time updates
        loadUserData();
        
        return root;
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

    /**
     * Shows the notification preferences dialog.
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
     * Loads notification preferences from Firestore and updates the switches.
     */
    private void loadNotificationPreferences(com.google.android.material.switchmaterial.SwitchMaterial switchInvited,
                                           com.google.android.material.switchmaterial.SwitchMaterial switchNotInvited) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || switchInvited == null || switchNotInvited == null) {
            return;
        }

        db.collection("users").document(currentUser.getUid())
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
                        switchInvited.setChecked(true);
                        switchNotInvited.setChecked(true);
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
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            ToastUtil.showShort(getContext(), "Please log in to save preferences");
            return;
        }

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("notificationPreferenceInvited", invitedEnabled);
        updates.put("notificationPreferenceNotInvited", notInvitedEnabled);

        db.collection("users").document(currentUser.getUid())
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
     * Temporarily added test method to send a notification to the current user.
     * This creates a notification request for Cloud Functions AND shows a local notification immediately.
     */
    private void sendTestNotificationToSelf() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            ToastUtil.showShort(getContext(), "Please log in to send test notification");
            Log.e(TAG, "Cannot send test notification: User not logged in");
            return;
        }

        String uid = currentUser.getUid();
        long timestamp = System.currentTimeMillis();
        String testEventId = "test_event_" + timestamp;
        String testEventTitle = "Test Event";
        String testTitle = "Test Notification";
        String testMessage = "This is a test notification sent to yourself. You can use this to test the notifications page.";

        Log.d(TAG, "Starting test notification for user: " + uid);
        Log.d(TAG, "Event ID: " + testEventId);
        Log.d(TAG, "Title: " + testTitle);
        Log.d(TAG, "Message: " + testMessage);
        Log.d(TAG, "Timestamp: " + timestamp);

        ToastUtil.showShort(getContext(), "Sending test notification...");

        showLocalNotificationImmediately(testTitle, testMessage, testEventId);
        
        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        Log.e(TAG, "User document does not exist for UID: " + uid);
                        ToastUtil.showShort(getContext(), "User document not found");
                        return;
                    }

                    String fcmToken = userDoc.getString("fcmToken");
                    Object notificationsEnabledObj = userDoc.get("notificationsEnabled");
                    boolean notificationsEnabled = true;
                    if (notificationsEnabledObj instanceof Boolean) {
                        notificationsEnabled = (Boolean) notificationsEnabledObj;
                    } else if (notificationsEnabledObj instanceof String) {
                        notificationsEnabled = Boolean.parseBoolean((String) notificationsEnabledObj);
                    }

                    Log.d(TAG, "User FCM Token exists: " + (fcmToken != null && !fcmToken.isEmpty()));
                    Log.d(TAG, "User FCM Token length: " + (fcmToken != null ? fcmToken.length() : 0));
                    Log.d(TAG, "Notifications enabled: " + notificationsEnabled);

                    if (fcmToken == null || fcmToken.isEmpty()) {
                        Log.w(TAG, "FCM token is missing - Cloud Function notification may not work");
                        Log.w(TAG, "Local notification should still be visible");
                    }

                    Map<String, Object> notificationRequest = new HashMap<>();
                    notificationRequest.put("eventId", testEventId);
                    notificationRequest.put("eventTitle", testEventTitle);
                    notificationRequest.put("organizerId", uid);
                    notificationRequest.put("userIds", Arrays.asList(uid));
                    notificationRequest.put("groupType", "general");
                    notificationRequest.put("message", testMessage);
                    notificationRequest.put("title", testTitle);
                    notificationRequest.put("status", "PENDING");
                    notificationRequest.put("createdAt", timestamp);
                    notificationRequest.put("processed", false);

                    Log.d(TAG, "Creating notification request in Firestore...");
                    db.collection("notificationRequests").add(notificationRequest)
                            .addOnSuccessListener(docRef -> {
                                String requestId = docRef.getId();
                                Log.d(TAG, "Notification request created with ID: " + requestId);
                                Log.d(TAG, "Document path: notificationRequests/" + requestId);
                                Log.d(TAG, "Cloud Function should trigger automatically");
                                Log.d(TAG, "Check Firebase Functions logs for processing details");
                                
                                ToastUtil.showShort(getContext(), "Test notification sent! Check phone notification bar.");
                                
                                docRef.addSnapshotListener((snapshot, e) -> {
                                    if (e != null) {
                                        Log.e(TAG, "Error listening to notification request", e);
                                        return;
                                    }
                                    if (snapshot != null && snapshot.exists()) {
                                        Boolean processed = snapshot.getBoolean("processed");
                                        Long sentCount = snapshot.getLong("sentCount");
                                        String error = snapshot.getString("error");
                                        
                                        if (Boolean.TRUE.equals(processed)) {
                                            Log.d(TAG, "Notification request processed. Sent count: " + (sentCount != null ? sentCount : 0));
                                            if (error != null) {
                                                Log.w(TAG, "Error from Cloud Function: " + error);
                                            } else {
                                                Log.d(TAG, "Notification sent successfully via Cloud Function");
                                            }
                                        }
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to create notification request: " + e.getMessage(), e);
                                ToastUtil.showShort(getContext(), "Failed to create notification request: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check user document", e);
                    ToastUtil.showShort(getContext(), "Failed to verify user settings");
                });
    }

    private void showLocalNotificationImmediately(String title, String message, String eventId) {
        if (getContext() == null) {
            Log.e(TAG, "Context is null, cannot show local notification");
            return;
        }

        Log.d(TAG, "Showing local notification: " + title);

        NotificationChannelManager.createNotificationChannel(getContext());
        
        NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null");
            return;
        }

        Intent intent = new Intent(getContext(), com.example.eventease.ui.entrant.notifications.NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getContext(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), NotificationChannelManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.entrant_ic_notification_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));

        int notificationId = (int) System.currentTimeMillis();
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Local notification displayed with ID: " + notificationId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show local notification", e);
        }
    }

    private void checkForNewNotifications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || getContext() == null) {
            return;
        }

        String uid = currentUser.getUid();
        SharedPreferences prefs = getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        long lastSeenTime = prefs.getLong("lastNotificationSeenTime", 0);
        
        android.util.Log.d(TAG, "Setting up real-time notification badge listener for user: " + uid);
        android.util.Log.d(TAG, "Initial last seen time: " + lastSeenTime);

        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
        }

        notificationBadgeListener = db.collection("notificationRequests")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        android.util.Log.e(TAG, "Error in notification badge listener", e);
                        return;
                    }
                    
                    if (querySnapshot == null) {
                        android.util.Log.w(TAG, "Query snapshot is null");
                        return;
                    }
                    
                    if (getView() == null) {
                        android.util.Log.w(TAG, "View is null, cannot update badge");
                        return;
                    }

                    android.util.Log.d(TAG, "Real-time update: notificationRequests changed, checking " + querySnapshot.size() + " documents");

                    SharedPreferences currentPrefs = getContext() != null ? 
                        getContext().getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE) : null;
                    if (currentPrefs == null) {
                        android.util.Log.w(TAG, "Cannot get SharedPreferences");
                        return;
                    }
                    
                    long currentLastSeenTime = currentPrefs.getLong("lastNotificationSeenTime", 0);
                    android.util.Log.d(TAG, "Current last seen time from prefs: " + currentLastSeenTime);

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
                                android.util.Log.d(TAG, "Found new notificationRequest: " + doc.getId() + 
                                    " created at " + createdAt + " (newer than " + currentLastSeenTime + ")");
                            }
                        }
                    }

                    android.util.Log.d(TAG, "Found " + matchingCount + " new notificationRequests for this user");

                    final boolean hasNotificationRequests = hasNewNotifications;
                    
                    db.collection("invitations")
                            .whereEqualTo("uid", uid)
                            .whereEqualTo("status", "PENDING")
                            .get()
                            .addOnSuccessListener(invitationSnapshot -> {
                                android.util.Log.d(TAG, "Checking " + invitationSnapshot.size() + " pending invitations");
                                
                                boolean hasNewInvitations = false;
                                int newInvCount = 0;
                                for (com.google.firebase.firestore.DocumentSnapshot invDoc : invitationSnapshot.getDocuments()) {
                                    Long issuedAt = invDoc.getLong("issuedAt");
                                    if (issuedAt != null && issuedAt > currentLastSeenTime) {
                                        hasNewInvitations = true;
                                        newInvCount++;
                                        android.util.Log.d(TAG, "Found new invitation: " + invDoc.getId() + 
                                            " issued at " + issuedAt + " (newer than " + currentLastSeenTime + ")");
                                    }
                                }
                                
                                android.util.Log.d(TAG, "Found " + newInvCount + " new invitations");
                                
                                boolean shouldShowBadge = hasNotificationRequests || hasNewInvitations;
                                
                                android.util.Log.d(TAG, "Badge decision: shouldShowBadge=" + shouldShowBadge + 
                                    " (notificationRequests=" + hasNotificationRequests + 
                                    ", invitations=" + hasNewInvitations + ")");
                                
                                if (getView() != null && notificationBadge != null) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (notificationBadge != null) {
                                                notificationBadge.setVisibility(shouldShowBadge ? View.VISIBLE : View.GONE);
                                                android.util.Log.d(TAG, "Badge visibility updated to: " + 
                                                    (shouldShowBadge ? "VISIBLE" : "GONE"));
                                            }
                                        });
                                    }
                                } else {
                                    android.util.Log.w(TAG, "Cannot update badge - view or badge is null");
                                }
                            })
                            .addOnFailureListener(e2 -> {
                                android.util.Log.e(TAG, "Failed to check invitations", e2);
                                if (getView() != null && notificationBadge != null && hasNotificationRequests) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (notificationBadge != null) {
                                                notificationBadge.setVisibility(View.VISIBLE);
                                                android.util.Log.d(TAG, "Badge set to VISIBLE (invitation check failed)");
                                            }
                                        });
                                    }
                                }
                            });
                });
    }
}
