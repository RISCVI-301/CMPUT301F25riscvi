package com.example.eventease.ui.entrant.profile;

import android.content.Context;
import android.net.Uri;
import com.example.eventease.util.ToastUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for updating user profile data in Firestore.
 * Handles name, phone, and profile picture updates.
 */
public class ProfileUpdateHelper {
    
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;
    private final Context context;
    
    /**
     * Callback interface for when profile update completes.
     */
    public interface UpdateCallback {
        void onUpdateSuccess();
        void onUpdateFailure(String error);
    }
    
    /**
     * Creates a new ProfileUpdateHelper.
     * 
     * @param context the context
     */
    public ProfileUpdateHelper(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }
    
    /**
     * Saves profile changes (name, phone, and optionally profile picture).
     * 
     * @param name the new name (or empty to keep current)
     * @param phone the new phone (or empty to keep current)
     * @param imageUri the new image URI (or null to keep current)
     * @param callback callback for update result
     */
    public void saveChanges(String name, String phone, Uri imageUri, UpdateCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            if (callback != null) callback.onUpdateFailure("Not signed in");
            return;
        }
        
        if (currentUser.isAnonymous()) {
            ToastUtil.showLong(context, "Cannot change email for guest accounts");
            if (callback != null) callback.onUpdateFailure("Guest account");
            return;
        }
        
        String uid = currentUser.getUid();
        DocumentReference userRef = db.collection("users").document(uid);
        
        // First, load current values from Firestore to use if text boxes are empty
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            Map<String, Object> updates = new HashMap<>();
            
            // Get current values from Firestore
            String currentName = documentSnapshot.exists() ? documentSnapshot.getString("name") : null;
            String currentPhone = documentSnapshot.exists() ? documentSnapshot.getString("phoneNumber") : null;
            
            // Get new values from parameters
            String newName = name != null ? name.trim() : "";
            String newPhone = phone != null ? phone.trim() : "";
            
            // If text box is empty, use current value from Firestore (or null if it was null)
            if (newName.isEmpty()) {
                // Use current name if it exists, otherwise keep it as null/empty
                if (currentName != null && !currentName.isEmpty()) {
                    updates.put("name", currentName);
                }
                // Keep as null/empty - don't update
            } else {
                // Use new value from text box
                updates.put("name", newName);
            }
            
            // If phone text box is empty, use current value from Firestore (or null if it was null)
            if (newPhone.isEmpty()) {
                // Use current phone if it exists, otherwise keep it as null/empty
                if (currentPhone != null && !currentPhone.isEmpty()) {
                    updates.put("phoneNumber", currentPhone);
                }
                // Keep as null/empty - don't update
            } else {
                // Use new value from text box
                updates.put("phoneNumber", newPhone);
            }

            Runnable continueProfileUpdate = () -> {
                // Update photo if selected
                if (imageUri != null) {
                    StorageReference storageRef = storage.getReference()
                        .child("profile_pictures")
                        .child(uid + "_" + System.currentTimeMillis() + ".jpg");
                    
                    storageRef.putFile(imageUri)
                        .addOnSuccessListener(taskSnapshot -> {
                            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                                updates.put("photoUrl", uri.toString());
                                updateFirestore(userRef, updates, callback);
                            });
                        })
                    .addOnFailureListener(e -> {
                        ToastUtil.showShort(context, "Failed to upload image");
                        if (callback != null) callback.onUpdateFailure("Image upload failed");
                    });
                } else {
                    updateFirestore(userRef, updates, callback);
                }
            };

            continueProfileUpdate.run();
        }).addOnFailureListener(e -> {
            ToastUtil.showShort(context, "Failed to load current profile data");
            if (callback != null) callback.onUpdateFailure("Failed to load profile");
        });
    }
    
    /**
     * Updates Firestore with the provided updates.
     * 
     * @param userRef the document reference
     * @param updates the updates to apply
     * @param callback callback for update result
     */
    private void updateFirestore(DocumentReference userRef, Map<String, Object> updates, UpdateCallback callback) {
        if (!updates.isEmpty()) {
            updates.put("updatedAt", System.currentTimeMillis());
            userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    ToastUtil.showShort(context, "Profile updated successfully");
                    if (callback != null) callback.onUpdateSuccess();
                })
                .addOnFailureListener(e -> {
                    ToastUtil.showShort(context, "Failed to update profile");
                    if (callback != null) callback.onUpdateFailure("Update failed");
                });
        } else {
            // Even if no updates, call success
            if (callback != null) callback.onUpdateSuccess();
        }
    }
    
    /**
     * Syncs email from Firebase Auth to Firestore.
     * Called after email verification to keep them in sync.
     */
    public void syncEmailToFirestore() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        currentUser.reload()
            .addOnSuccessListener(unused -> {
                String uid = currentUser.getUid();
                String authEmail = currentUser.getEmail();
                if (authEmail == null || authEmail.trim().isEmpty()) return;

                DocumentReference userRef = db.collection("users").document(uid);
                userRef.get().addOnSuccessListener(snap -> {
                    if (snap != null && snap.exists()) {
                        String dbEmail = snap.getString("email");
                        if (dbEmail == null || !authEmail.equalsIgnoreCase(dbEmail)) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("email", authEmail);
                            updates.put("updatedAt", System.currentTimeMillis());
                            userRef.update(updates);
                        }
                    } else {
                        // If doc missing, set minimal fields to avoid NPE elsewhere
                        Map<String, Object> create = new HashMap<>();
                        create.put("uid", uid);
                        create.put("email", authEmail);
                        create.put("updatedAt", System.currentTimeMillis());
                        userRef.set(create, com.google.firebase.firestore.SetOptions.merge());
                    }
                });
            });
    }
}

