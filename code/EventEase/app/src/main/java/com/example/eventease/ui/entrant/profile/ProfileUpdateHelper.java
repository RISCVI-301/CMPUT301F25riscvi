package com.example.eventease.ui.entrant.profile;

import android.content.Context;
import android.net.Uri;
import com.example.eventease.util.ToastUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
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
                    // After successfully updating the user document, update all event subcollections
                    updateEventSubcollections(userRef.getId());
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
     * Updates all event subcollections where this user appears as an entrant.
     * This ensures that when a user updates their profile, all event-specific
     * entrant documents are also updated with the latest information.
     * 
     * @param uid the user ID
     */
    private void updateEventSubcollections(String uid) {
        // First, get the updated user document
        db.collection("users").document(uid).get()
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
                db.collection("events").get()
                    .addOnSuccessListener(eventsSnapshot -> {
                        if (eventsSnapshot == null || eventsSnapshot.isEmpty()) {
                            return;
                        }
                        
                        // Collect all entrant document references across all events and subcollections
                        java.util.List<DocumentReference> allEntrantRefs = new java.util.ArrayList<>();
                        
                        for (DocumentSnapshot eventDoc : eventsSnapshot.getDocuments()) {
                            String eventId = eventDoc.getId();
                            DocumentReference eventRef = db.collection("events").document(eventId);
                            
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
                        android.util.Log.e("ProfileUpdateHelper", 
                            "Failed to query events for subcollection update", e);
                    });
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ProfileUpdateHelper", 
                    "Failed to get user document for subcollection update", e);
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
                    android.util.Log.w("ProfileUpdateHelper", 
                        "Failed to check entrant document", e);
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
            android.util.Log.d("ProfileUpdateHelper", 
                "No entrant documents found to update");
            return;
        }
        
        final int MAX_BATCH_SIZE = 500; // Firestore batch limit
        final int totalToUpdate = refs.size();
        int batchCount = 0;
        WriteBatch batch = db.batch();
        
        for (DocumentReference ref : refs) {
            // Use merge to preserve fields like joinedAt
            batch.set(ref, updatedData, SetOptions.merge());
            batchCount++;
            
            // Commit batch if it's getting large
            if (batchCount >= MAX_BATCH_SIZE) {
                final WriteBatch currentBatch = batch;
                final int currentBatchCount = batchCount;
                currentBatch.commit()
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("ProfileUpdateHelper", 
                            "Updated batch of " + currentBatchCount + " entrant documents");
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("ProfileUpdateHelper", 
                            "Failed to update batch of entrant documents", e);
                    });
                batch = db.batch();
                batchCount = 0;
            }
        }
        
        // Commit any remaining updates
        if (batchCount > 0) {
            final int finalBatchCount = batchCount;
            batch.commit()
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("ProfileUpdateHelper", 
                        "Updated final batch of " + finalBatchCount + " entrant documents. " +
                        "Total updated: " + totalToUpdate);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ProfileUpdateHelper", 
                        "Failed to update final batch of entrant documents", e);
                });
        } else if (totalToUpdate > 0) {
            android.util.Log.d("ProfileUpdateHelper", 
                "Total entrant documents updated: " + totalToUpdate);
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
                            userRef.update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    // After successfully updating email, update event subcollections
                                    updateEventSubcollections(uid);
                                });
                        }
                    } else {
                        // If doc missing, set minimal fields to avoid NPE elsewhere
                        Map<String, Object> create = new HashMap<>();
                        create.put("uid", uid);
                        create.put("email", authEmail);
                        create.put("updatedAt", System.currentTimeMillis());
                        userRef.set(create, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                // After successfully creating/updating email, update event subcollections
                                updateEventSubcollections(uid);
                            });
                    }
                });
            });
    }
}

