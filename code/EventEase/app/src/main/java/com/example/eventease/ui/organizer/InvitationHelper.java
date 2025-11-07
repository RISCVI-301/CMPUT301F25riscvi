package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InvitationHelper {
    private static final String TAG = "InvitationHelper";
    private final FirebaseFirestore db;
    
    public InvitationHelper() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    public interface InvitationCallback {
        void onComplete(int sentCount);
        void onError(String error);
    }
    
    public void sendInvitationsToSelectedEntrants(String eventId, String eventTitle, InvitationCallback callback) {
        if (eventId == null || eventId.isEmpty()) {
            if (callback != null) {
                callback.onError("Event ID is required");
            }
            return;
        }
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        eventRef.collection("SelectedEntrants").get()
                .addOnSuccessListener(selectedSnapshot -> {
                    if (selectedSnapshot == null || selectedSnapshot.isEmpty()) {
                        Log.d(TAG, "No selected entrants to send invitations to");
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                        return;
                    }
                    
                    List<DocumentSnapshot> selectedDocs = selectedSnapshot.getDocuments();
                    List<Task<Void>> invitationTasks = new ArrayList<>();
                    List<String> userIds = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : selectedDocs) {
                        String userId = doc.getId();
                        userIds.add(userId);
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    long expiresAt = currentTime + (7L * 24 * 60 * 60 * 1000);
                    
                    WriteBatch batch = db.batch();
                    int batchCount = 0;
                    final int MAX_BATCH_SIZE = 500;
                    
                    for (String userId : userIds) {
                        String invitationId = UUID.randomUUID().toString();
                        DocumentReference invitationRef = db.collection("invitations").document(invitationId);
                        
                        Map<String, Object> invitationData = new HashMap<>();
                        invitationData.put("id", invitationId);
                        invitationData.put("eventId", eventId);
                        invitationData.put("uid", userId);
                        invitationData.put("status", "PENDING");
                        invitationData.put("issuedAt", currentTime);
                        invitationData.put("expiresAt", expiresAt);
                        
                        batch.set(invitationRef, invitationData);
                        batchCount++;
                        
                        if (batchCount >= MAX_BATCH_SIZE) {
                            final WriteBatch currentBatch = batch;
                            invitationTasks.add(currentBatch.commit());
                            batch = db.batch();
                            batchCount = 0;
                        }
                    }
                    
                    if (batchCount > 0) {
                        invitationTasks.add(batch.commit());
                    }
                    
                    if (!invitationTasks.isEmpty()) {
                        Tasks.whenAll(invitationTasks)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Successfully created " + userIds.size() + " invitations");
                                    
                                    Map<String, Object> notificationData = new HashMap<>();
                                    notificationData.put("eventId", eventId);
                                    notificationData.put("eventTitle", eventTitle != null ? eventTitle : "event");
                                    notificationData.put("userIds", userIds);
                                    notificationData.put("timestamp", currentTime);
                                    notificationData.put("type", "invitation");
                                    
                                    db.collection("notifications").add(notificationData)
                                            .addOnSuccessListener(docRef -> {
                                                Log.d(TAG, "Notification data saved for " + userIds.size() + " users");
                                                if (callback != null) {
                                                    callback.onComplete(userIds.size());
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.w(TAG, "Failed to save notification data", e);
                                                if (callback != null) {
                                                    callback.onComplete(userIds.size());
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create invitations", e);
                                    if (callback != null) {
                                        callback.onError("Failed to create invitations: " + e.getMessage());
                                    }
                                });
                    } else {
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load selected entrants", e);
                    if (callback != null) {
                        callback.onError("Failed to load selected entrants: " + e.getMessage());
                    }
                });
    }
}

