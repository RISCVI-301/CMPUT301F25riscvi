package com.example.eventease.ui.organizer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.example.eventease.ui.entrant.profile.ProfileDeletionHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
/**
 * Manages the organizer's account screen.
 * <p>
 * This activity displays the organizer's profile information, such as their name and avatar.
 * It fetches this data from the 'users' collection in Firestore. It also provides functionality
 * for the organizer to update their profile picture and navigate to other parts of the app.
 */
public class OrganizerAccountActivity extends AppCompatActivity {

    private static final String TAG = "OrganizerAccount";

    private ImageView ivAvatar;
    private TextView tvFullName;
    private ImageView btnEditProfile;
    private String organizerId;
    private boolean isResolvingOrganizerId;
    private String userEmail;
    private com.google.firebase.firestore.ListenerRegistration profileListener;
    
    // Bottom nav icon and label views
    private ImageView navIconMyEvents, navIconCreate, navIconAccount;
    private TextView navLabelMyEvents, navLabelCreate, navLabelAccount;
    /**
     * Initializes the activity, view components, and loads the organizer's profile data.
     * @param savedInstanceState If the activity is being re-initialized, this Bundle contains the most recent data.
     */
    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organizer_account);

        ivAvatar = findViewById(R.id.ivAvatar);
        tvFullName = findViewById(R.id.tvFullName);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        
        // Get bottom nav icon and label views
        navIconMyEvents = findViewById(R.id.nav_icon_my_events);
        navIconCreate = findViewById(R.id.nav_icon_create);
        navIconAccount = findViewById(R.id.nav_icon_account);
        navLabelMyEvents = findViewById(R.id.nav_label_my_events);
        navLabelCreate = findViewById(R.id.nav_label_create);
        navLabelAccount = findViewById(R.id.nav_label_account);

        organizerId = getIntent().getStringExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID);

        if (tvFullName == null || ivAvatar == null) {
            Log.e(TAG, "organizer_account.xml must define tvFullName and ivAvatar");
            return;
        }

        tvFullName.setText("Loading...");
        Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);

        // Get device ID
        String userId = com.example.eventease.auth.AuthHelper.getUid(this);
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Please complete your profile", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadProfile(userId);
        setupProfileListener(userId);
        resolveOrganizerId(null);

        // Remove click listener from avatar - profile picture editing is done in edit profile screen
        // ivAvatar.setOnClickListener(v -> pickImage.launch("image/*"));

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                // Navigate to edit profile activity
                Intent intent = new Intent(this, OrganizerEditProfileActivity.class);
                startActivity(intent);
            });
        }

        setupBottomNavigation();
        
        // Set icon states - Account is selected (light), others are dark
        updateNavigationSelection("account");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
    }
    
    /**
     * Sets up a real-time listener for profile changes so the UI updates automatically
     * when the profile picture is changed from another view.
     */
    private void setupProfileListener(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        
        profileListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Profile listener error", error);
                        return;
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        applyProfileFromDoc(snapshot);
                    }
                });
    }

    private void resolveOrganizerId(@Nullable Runnable onReady) {
        if (organizerId != null && !organizerId.trim().isEmpty()) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (isResolvingOrganizerId) {
            return;
        }
        String userId = com.example.eventease.auth.AuthHelper.getUid(this);
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_LONG).show();
            return;
        }
        isResolvingOrganizerId = true;
        organizerId = userId; // Use device ID as organizer ID

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    isResolvingOrganizerId = false;
                    if (organizerId == null || organizerId.trim().isEmpty()) {
                        Toast.makeText(this, "Organizer profile not configured yet.", Toast.LENGTH_LONG).show();
                    } else if (onReady != null) {
                        onReady.run();
                    }
                })
                .addOnFailureListener(e -> {
                    isResolvingOrganizerId = false;
                    Toast.makeText(this, "Failed to load organizer profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
    /**
     * Sets up the listeners for the custom bottom navigation bar.
     */
    private void setupBottomNavigation() {
        LinearLayout btnMyEvents = findViewById(R.id.btnMyEvents);
        if (btnMyEvents != null) {
            btnMyEvents.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, com.example.eventease.ui.organizer.OrganizerMyEventActivity.class);
                if (organizerId != null && !organizerId.trim().isEmpty()) {
                    intent.putExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID, organizerId);
                }
                startActivity(intent);
                overridePendingTransition(0, 0); // Remove slide animation
                finish(); // Close this activity to show new one instantly
            });
        }

        LinearLayout btnAccount = findViewById(R.id.btnAccount);
        if (btnAccount != null) {
            btnAccount.setOnClickListener(v -> recreate());
        }

        LinearLayout fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, com.example.eventease.ui.organizer.OrganizerCreateEventActivity.class);
                if (organizerId != null && !organizerId.trim().isEmpty()) {
                    intent.putExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID, organizerId);
                }
                startActivity(intent);
                overridePendingTransition(0, 0); // Remove slide animation
            });
        }

        View btnSwitchRole = findViewById(R.id.btnSwitchRole);
        if (btnSwitchRole != null) {
            btnSwitchRole.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.eventease.MainActivity.class);
                intent.putExtra("nav_target", "account");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        View btnDeleteProfile = findViewById(R.id.btnDeleteProfile);
        if (btnDeleteProfile != null) {
            btnDeleteProfile.setOnClickListener(v -> {
                showDeleteConfirmationDialog();
            });
        }
    }
    /**
     * Loads the user's profile data from a specific document in the 'users' collection.
     *
     * @param documentId The ID of the user document to fetch (could be an Auth UID or a custom organizer ID).
     */
    private void loadProfile(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            String userId = com.example.eventease.auth.AuthHelper.getUid(this);
            documentId = userId != null ? userId : "";
        }
        final String docId = documentId;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(docId)
                .get()
                .addOnSuccessListener(this::applyProfileFromDoc)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Fetch user failed for document: " + docId, e);
                    if (tvFullName != null)
                        tvFullName.setText("Organizer");
                    if (ivAvatar != null)
                        Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
                });
    }
    /**
     * Applies the fetched profile data to the UI elements.
     *
     * @param doc The DocumentSnapshot retrieved from Firestore containing the user's data.
     */
    private void applyProfileFromDoc(DocumentSnapshot doc) {
        if (tvFullName == null || ivAvatar == null) return;

        if (doc == null || !doc.exists()) {
            Log.w(TAG, "User document not found");
            tvFullName.setText("Organizer");
            Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
            return;
        }

        String name = doc.getString("fullName");
        if (name == null || name.trim().isEmpty()) {
            name = doc.getString("name");
        }

        String photoUrl = doc.getString("photoUrl");
        userEmail = doc.getString("email");

        tvFullName.setText((name != null && !name.isEmpty()) ? name : "Organizer");

        if (photoUrl != null && !photoUrl.isEmpty()) {
            // Clear any existing image first
            Glide.with(this).clear(ivAvatar);
            // Load the image
            Glide.with(this)
                .load(photoUrl)
                .skipMemoryCache(false) // Allow memory cache for performance
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // Cache on disk
                .circleCrop()
                .into(ivAvatar);
        } else {
            Glide.with(this).clear(ivAvatar);
            Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
        }

        // We no longer display authentication info on the organizer account screen
    }
    /**
     * Uploads a new avatar image to Firebase Storage and updates the URL
     * in the user's Firestore document. Uses the same storage path and pattern
     * as the entrant view to ensure consistency across both views.
     *
     * @param uri The local URI of the image to be uploaded.
     */
    private void uploadNewAvatar(Uri uri) {
        String userId = com.example.eventease.auth.AuthHelper.getUid(this);
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure we're authenticated for Storage upload
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener(authResult -> {
                // Use the same storage path and naming pattern as entrant view
                StorageReference ref = FirebaseStorage.getInstance()
                        .getReference("profile_pictures")
                        .child(userId + "_" + System.currentTimeMillis() + ".jpg");

                ref.putFile(uri)
                        .continueWithTask(task -> {
                            if (!task.isSuccessful()) throw task.getException();
                            return ref.getDownloadUrl();
                        })
                        .addOnSuccessListener(download -> {
                            Map<String, Object> update = new HashMap<>();
                            update.put("photoUrl", download.toString());
                            update.put("updatedAt", System.currentTimeMillis());
                            
                            // Update the user document (same document used by entrant view)
                            FirebaseFirestore.getInstance().collection("users")
                                    .document(userId)
                                    .set(update, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(v -> {
                                    String newPhotoUrl = download.toString();
                                    
                                    // Clear Glide cache for the old image if it exists
                                    if (ivAvatar != null) {
                                        Glide.with(this).clear(ivAvatar);
                                    }
                                    
                                    // Update the avatar immediately with cache busting using signature
                                    if (ivAvatar != null) {
                                        com.bumptech.glide.request.RequestOptions options = 
                                            new com.bumptech.glide.request.RequestOptions()
                                                .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                                                .circleCrop();
                                        
                                        Glide.with(this)
                                            .load(newPhotoUrl)
                                            .apply(options)
                                            .into(ivAvatar);
                                    }
                                    
                                    // Reload profile to ensure UI is updated (this will also update via listener)
                                    loadProfile(userId);
                                    
                                    Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show();
                                    
                                    // Update event subcollections to keep entrant data in sync
                                    updateEventSubcollections(userId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to save photoUrl to Firestore", e);
                                    Toast.makeText(this, "Save URL failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to upload image", e);
                            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to sign in anonymously for upload", e);
                Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * Updates all event subcollections where this user appears as an entrant.
     * This ensures that when a user updates their profile from organizer view,
     * all event-specific entrant documents are also updated with the latest information.
     * 
     * @param uid the user ID
     */
    private void updateEventSubcollections(String uid) {
        // Get the updated user document
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener(userDoc -> {
                if (userDoc == null || !userDoc.exists()) {
                    return;
                }
                
                // Build the updated entrant data from the user document
                Map<String, Object> updatedEntrantData = buildEntrantDataFromUser(userDoc);
                
                // List of subcollection names to check
                String[] subcollections = {
                    "WaitlistedEntrants",
                    "SelectedEntrants",
                    "NonSelectedEntrants",
                    "CancelledEntrants"
                };
                
                // Query all events once
                FirebaseFirestore.getInstance().collection("events").get()
                    .addOnSuccessListener(eventsSnapshot -> {
                        if (eventsSnapshot == null || eventsSnapshot.isEmpty()) {
                            return;
                        }
                        
                        // Collect all entrant document references across all events and subcollections
                        java.util.List<DocumentReference> allEntrantRefs = new java.util.ArrayList<>();
                        
                        for (DocumentSnapshot eventDoc : eventsSnapshot.getDocuments()) {
                            String eventId = eventDoc.getId();
                            DocumentReference eventRef = FirebaseFirestore.getInstance()
                                    .collection("events").document(eventId);
                            
                            // Check each subcollection for this event
                            for (String subcollectionName : subcollections) {
                                DocumentReference entrantRef = eventRef
                                    .collection(subcollectionName)
                                    .document(uid);
                                allEntrantRefs.add(entrantRef);
                            }
                        }
                        
                        // Check which documents exist and update them
                        checkAndUpdateEntrantDocuments(allEntrantRefs, updatedEntrantData);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to query events for subcollection update", e);
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to get user document for subcollection update", e);
            });
    }
    
    /**
     * Checks which entrant documents exist and updates them in batches.
     * 
     * @param entrantRefs list of document references to check and update
     * @param updatedData the data to update with
     */
    private void checkAndUpdateEntrantDocuments(java.util.List<DocumentReference> entrantRefs, 
                                                Map<String, Object> updatedData) {
        if (entrantRefs.isEmpty()) {
            return;
        }
        
        // Check each document and collect those that exist
        java.util.List<DocumentReference> existingRefs = new java.util.ArrayList<>();
        final int[] checkedCount = {0};
        final int totalCount = entrantRefs.size();
        
        for (DocumentReference ref : entrantRefs) {
            ref.get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        existingRefs.add(ref);
                    }
                    checkedCount[0]++;
                    
                    // Once all documents are checked, update them in batches
                    if (checkedCount[0] >= totalCount) {
                        updateDocumentsInBatches(existingRefs, updatedData);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to check entrant document", e);
                    checkedCount[0]++;
                    if (checkedCount[0] >= totalCount) {
                        updateDocumentsInBatches(existingRefs, updatedData);
                    }
                });
        }
    }
    
    /**
     * Updates documents in batches to avoid exceeding Firestore limits.
     * 
     * @param refs list of document references to update
     * @param updatedData the data to update with
     */
    private void updateDocumentsInBatches(java.util.List<DocumentReference> refs,
                                         Map<String, Object> updatedData) {
        if (refs.isEmpty()) {
            Log.d(TAG, "No entrant documents found to update");
            return;
        }
        
        final int MAX_BATCH_SIZE = 500; // Firestore batch limit
        final int totalToUpdate = refs.size();
        int batchCount = 0;
        com.google.firebase.firestore.WriteBatch batch = FirebaseFirestore.getInstance().batch();
        
        for (DocumentReference ref : refs) {
            // Use merge to preserve fields like joinedAt
            batch.set(ref, updatedData, com.google.firebase.firestore.SetOptions.merge());
            batchCount++;
            
            // Commit batch if it's getting large
            if (batchCount >= MAX_BATCH_SIZE) {
                final com.google.firebase.firestore.WriteBatch currentBatch = batch;
                final int currentBatchCount = batchCount;
                currentBatch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Updated batch of " + currentBatchCount + " entrant documents");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update batch of entrant documents", e);
                    });
                batch = FirebaseFirestore.getInstance().batch();
                batchCount = 0;
            }
        }
        
        // Commit any remaining updates
        if (batchCount > 0) {
            final int finalBatchCount = batchCount;
            batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Updated final batch of " + finalBatchCount + " entrant documents. " +
                        "Total updated: " + totalToUpdate);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update final batch of entrant documents", e);
                });
        } else if (totalToUpdate > 0) {
            Log.d(TAG, "Total entrant documents updated: " + totalToUpdate);
        }
    }
    
    /**
     * Builds entrant data map from a user document snapshot.
     * This replicates the structure used in buildWaitlistEntry.
     * 
     * @param userDoc the user document snapshot
     * @return a map containing the entrant data
     */
    private Map<String, Object> buildEntrantDataFromUser(DocumentSnapshot userDoc) {
        Map<String, Object> data = new HashMap<>();
        String uid = userDoc.getId();
        data.put("userId", uid);
        
        // Compute displayName from available name fields
        String displayName = userDoc.getString("fullName");
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = userDoc.getString("name");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            String first = userDoc.getString("firstName");
            String last = userDoc.getString("lastName");
            displayName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        }
        
        // Add all relevant fields
        putIfString(data, "displayName", displayName);
        putIfString(data, "fullName", userDoc.getString("fullName"));
        putIfString(data, "name", userDoc.getString("name"));
        putIfString(data, "firstName", userDoc.getString("firstName"));
        putIfString(data, "lastName", userDoc.getString("lastName"));
        putIfString(data, "email", userDoc.getString("email"));
        putIfString(data, "phoneNumber", userDoc.getString("phoneNumber"));
        putIfString(data, "photoUrl", userDoc.getString("photoUrl"));
        
        return data;
    }
    
    /**
     * Helper method to add a string value to a map only if it's not null or empty.
     */
    private void putIfString(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value);
        }
    }

    private void showDeleteConfirmationDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
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
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);

            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }

    private Bitmap captureScreenshot() {
        try {
            if (getWindow() == null) return null;
            android.view.View rootView = getWindow().getDecorView().getRootView();
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
        if (bitmap == null) return null;

        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

            RenderScript rs = RenderScript.create(this);
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
        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "No profile found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Deleting profile...", Toast.LENGTH_SHORT).show();

        ProfileDeletionHelper deletionHelper = new ProfileDeletionHelper(this);
        
        // Check if user is an organizer and delete their events first
        com.google.firebase.firestore.DocumentReference userRef = FirebaseFirestore.getInstance().collection("users").document(uid);
        userRef.get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc != null && userDoc.exists()) {
                        java.util.List<String> roles = (java.util.List<String>) userDoc.get("roles");
                        boolean isOrganizer = roles != null && roles.contains("organizer");
                        
                        com.google.android.gms.tasks.Task<Void> organizerEventsTask = com.google.android.gms.tasks.Tasks.forResult(null);
                        if (isOrganizer) {
                            Log.d(TAG, "User is an organizer, deleting their events first");
                            organizerEventsTask = deletionHelper.deleteOrganizerEvents(uid);
                        }
                        
                        // Wait for organizer events deletion (if applicable), then proceed with user references
                        organizerEventsTask
                                .continueWithTask(task -> {
                                    com.google.android.gms.tasks.TaskCompletionSource<Void> completionSource = new com.google.android.gms.tasks.TaskCompletionSource<>();
                                    deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
                                        @Override
                                        public void onDeletionComplete() {
                                            completionSource.setResult(null);
                                        }

                                        @Override
                                        public void onDeletionFailure(String error) {
                                            Log.e(TAG, "Failed to delete user references: " + error);
                                            // Still proceed even if some references failed
                                            completionSource.setResult(null);
                                        }
                                    });
                                    return completionSource.getTask();
                                })
                                .addOnSuccessListener(aVoid -> {
                                    deleteUserDocumentAndAuth(uid);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed during profile deletion process", e);
                                    // Still try to delete the user document
                                    deleteUserDocumentAndAuth(uid);
                                });
                    } else {
                        // User document not found, proceed with deletion anyway
                        deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
                            @Override
                            public void onDeletionComplete() {
                                deleteUserDocumentAndAuth(uid);
                            }

                            @Override
                            public void onDeletionFailure(String error) {
                                Log.e(TAG, "Failed to delete user references: " + error);
                                deleteUserDocumentAndAuth(uid);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check user roles, proceeding with deletion", e);
                    // If we can't check roles, proceed with deletion anyway
                    deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
                        @Override
                        public void onDeletionComplete() {
                            deleteUserDocumentAndAuth(uid);
                        }

                        @Override
                        public void onDeletionFailure(String error) {
                            Log.e(TAG, "Failed to delete user references: " + error);
                            deleteUserDocumentAndAuth(uid);
                        }
                    });
                });
    }

    private void deleteUserDocumentAndAuth(String uid) {
        // Sign in anonymously to get Firebase Auth token for Firestore rules
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    // Now delete with authenticated context
                    DocumentReference userRef = FirebaseFirestore.getInstance().collection("users").document(uid);
                    userRef.delete()
                            .addOnSuccessListener(aVoid -> {
                                // Device auth - clear cache to trigger profile setup on next launch
                                com.example.eventease.auth.DeviceAuthManager authManager =
                                        new com.example.eventease.auth.DeviceAuthManager(OrganizerAccountActivity.this);
                                authManager.clearCache();
                                Toast.makeText(this, "Profile deleted successfully", Toast.LENGTH_SHORT).show();
                                launchProfileSetupScreen();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete user document", e);
                                Toast.makeText(this, "Failed to delete profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sign in anonymously", e);
                    // Try deleting anyway (might work if rules allow)
                    DocumentReference userRef = FirebaseFirestore.getInstance().collection("users").document(uid);
                    userRef.delete()
                            .addOnSuccessListener(aVoid -> {
                                com.example.eventease.auth.DeviceAuthManager authManager =
                                        new com.example.eventease.auth.DeviceAuthManager(OrganizerAccountActivity.this);
                                authManager.clearCache();
                                Toast.makeText(this, "Profile deleted successfully", Toast.LENGTH_SHORT).show();
                                launchProfileSetupScreen();
                            })
                            .addOnFailureListener(deleteError -> {
                                Log.e(TAG, "Failed to delete user document", deleteError);
                                Toast.makeText(this, "Failed to delete profile: " + deleteError.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });
    }

    private void launchProfileSetupScreen() {
        Intent intent = new Intent(this, com.example.eventease.auth.ProfileSetupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Updates the bottom navigation icons and labels based on the selected page.
     * Selected icons are light, unselected are dark.
     */
    private void updateNavigationSelection(String selectedPage) {
        // Dark blue for unselected items (brand color)
        int unselectedColor = android.graphics.Color.parseColor("#223C65");
        // iOS blue color for selected items
        int selectedColor = android.graphics.Color.parseColor("#446EAF");

        // Reset all to unselected (dark circles and dark text)
        if (navIconMyEvents != null) {
            navIconMyEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_dark);
        }
        if (navIconCreate != null) {
            navIconCreate.setImageResource(R.drawable.organizer_ic_add_circle_dark);
        }
        if (navIconAccount != null) {
            navIconAccount.setImageResource(R.drawable.entrant_ic_account_circle_dark);
        }
        if (navLabelMyEvents != null) {
            navLabelMyEvents.setTextColor(unselectedColor);
        }
        if (navLabelCreate != null) {
            navLabelCreate.setTextColor(unselectedColor);
        }
        if (navLabelAccount != null) {
            navLabelAccount.setTextColor(unselectedColor);
        }

        // Set selected (light circle and blue text) based on page
        if ("myEvents".equals(selectedPage)) {
            if (navIconMyEvents != null) {
                navIconMyEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_light);
            }
            if (navLabelMyEvents != null) {
                navLabelMyEvents.setTextColor(selectedColor);
            }
        } else if ("create".equals(selectedPage)) {
            if (navIconCreate != null) {
                navIconCreate.setImageResource(R.drawable.organizer_ic_add_circle_light);
            }
            if (navLabelCreate != null) {
                navLabelCreate.setTextColor(selectedColor);
            }
        } else if ("account".equals(selectedPage)) {
            if (navIconAccount != null) {
                navIconAccount.setImageResource(R.drawable.entrant_ic_account_circle_light);
            }
            if (navLabelAccount != null) {
                navLabelAccount.setTextColor(selectedColor);
            }
        }
    }
}