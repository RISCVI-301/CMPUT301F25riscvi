package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to process invitations after deadline has passed.
 * 
 * <p>This class handles:
 * <ul>
 *   <li>Moving non-responders from SelectedEntrants to CancelledEntrants</li>
 *   <li>Updating invitation status to DECLINED for expired invitations</li>
 * </ul>
 */
public class InvitationDeadlineProcessor {
    private static final String TAG = "InvitationDeadlineProcessor";
    private final FirebaseFirestore db;
    
    public InvitationDeadlineProcessor() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    public interface DeadlineCallback {
        void onComplete(int processedCount);
        void onError(String error);
    }
    
    /**
     * Processes invitations for an event after the deadline has passed.
     * Moves non-responders from SelectedEntrants to CancelledEntrants.
     */
    public void processDeadlineForEvent(String eventId, DeadlineCallback callback) {
        if (eventId == null || eventId.isEmpty()) {
            if (callback != null) {
                callback.onError("Event ID is required");
            }
            return;
        }
        
        Log.d(TAG, "=== Processing deadline for event: " + eventId + " ===");
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                Log.e(TAG, "Event not found: " + eventId);
                if (callback != null) {
                    callback.onError("Event not found");
                }
                return;
            }
            
            Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
            if (deadlineEpochMs == null || deadlineEpochMs == 0) {
                Log.d(TAG, "Event " + eventId + " does not have a deadline");
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            
            // Skip if event start date has already passed
            Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
            if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                Log.d(TAG, "Event " + eventId + " start date has already passed, skipping deadline processing");
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            if (currentTime < deadlineEpochMs) {
                Log.d(TAG, "Deadline has not passed for event " + eventId + 
                    " (deadline: " + deadlineEpochMs + ", current: " + currentTime + ")");
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            // Get all PENDING invitations for this event
            Query invitationsQuery = db.collection("invitations")
                    .whereEqualTo("eventId", eventId)
                    .whereEqualTo("status", "PENDING");
            
            invitationsQuery.get()
                    .addOnSuccessListener(invitationsSnapshot -> {
                        if (invitationsSnapshot == null || invitationsSnapshot.isEmpty()) {
                            Log.d(TAG, "No pending invitations to process");
                            if (callback != null) {
                                callback.onComplete(0);
                            }
                            return;
                        }
                        
                        List<String> nonResponderUserIds = new ArrayList<>();
                        List<String> invitationIds = new ArrayList<>();
                        
                        for (QueryDocumentSnapshot doc : invitationsSnapshot) {
                            Long expiresAt = doc.getLong("expiresAt");
                            if (expiresAt != null && expiresAt > 0 && expiresAt <= currentTime) {
                                String uid = doc.getString("uid");
                                if (uid != null && !uid.isEmpty()) {
                                    nonResponderUserIds.add(uid);
                                    invitationIds.add(doc.getId());
                                }
                            }
                        }
                        
                        if (nonResponderUserIds.isEmpty()) {
                            Log.d(TAG, "No expired invitations found");
                            if (callback != null) {
                                callback.onComplete(0);
                            }
                            return;
                        }
                        
                        Log.d(TAG, "Found " + nonResponderUserIds.size() + " non-responders to move to CancelledEntrants");
                        moveNonRespondersToCancelled(eventRef, eventId, nonResponderUserIds, invitationIds, callback);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load pending invitations", e);
                        if (callback != null) {
                            callback.onError("Failed to load invitations: " + e.getMessage());
                        }
                    });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load event " + eventId, e);
            if (callback != null) {
                callback.onError("Failed to load event: " + e.getMessage());
            }
        });
    }
    
    /**
     * Moves non-responders from SelectedEntrants to CancelledEntrants.
     */
    private void moveNonRespondersToCancelled(DocumentReference eventRef, String eventId,
                                               List<String> userIds, List<String> invitationIds,
                                               DeadlineCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            if (callback != null) {
                callback.onComplete(0);
            }
            return;
        }
        
        Log.d(TAG, "Moving " + userIds.size() + " non-responders to CancelledEntrants");
        
        // Fetch user data for all non-responders
        List<Task<DocumentSnapshot>> userTasks = new ArrayList<>();
        for (String userId : userIds) {
            userTasks.add(db.collection("users").document(userId).get());
        }
        
        Tasks.whenAllSuccess(userTasks)
                .addOnSuccessListener(userDocs -> {
                    WriteBatch batch = db.batch();
                    int batchCount = 0;
                    final int MAX_BATCH_SIZE = 499;
                    List<Task<Void>> batchTasks = new ArrayList<>();
                    
                    for (int i = 0; i < userIds.size(); i++) {
                        String userId = userIds.get(i);
                        DocumentSnapshot userDoc = i < userDocs.size() ? (DocumentSnapshot) userDocs.get(i) : null;
                        
                        DocumentReference selectedRef = eventRef.collection("SelectedEntrants").document(userId);
                        DocumentReference cancelledRef = eventRef.collection("CancelledEntrants").document(userId);
                        
                        Map<String, Object> cancelledData = buildCancelledEntry(userId, userDoc);
                        batch.set(cancelledRef, cancelledData, SetOptions.merge());
                        batch.delete(selectedRef);
                        batchCount += 2;
                        
                        // Update invitation status to DECLINED
                        if (i < invitationIds.size()) {
                            String invitationId = invitationIds.get(i);
                            DocumentReference invitationRef = db.collection("invitations").document(invitationId);
                            Map<String, Object> invitationUpdates = new HashMap<>();
                            invitationUpdates.put("status", "DECLINED");
                            invitationUpdates.put("declinedAt", System.currentTimeMillis());
                            batch.update(invitationRef, invitationUpdates);
                            batchCount++;
                        }
                        
                        if (batchCount >= MAX_BATCH_SIZE) {
                            final WriteBatch currentBatch = batch;
                            batchTasks.add(currentBatch.commit());
                            batch = db.batch();
                            batchCount = 0;
                        }
                    }
                    
                    if (batchCount > 0) {
                        batchTasks.add(batch.commit());
                    }
                    
                    if (!batchTasks.isEmpty()) {
                        Tasks.whenAll(batchTasks)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✓ Successfully moved " + userIds.size() + 
                                        " non-responders to CancelledEntrants");
                                    
                                    // Send "sorry" notification to those who missed the deadline
                                    sendDeadlineMissedNotification(eventId, userIds);
                                    
                                    if (callback != null) {
                                        callback.onComplete(userIds.size());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to move non-responders to CancelledEntrants", e);
                                    if (callback != null) {
                                        callback.onError("Failed to move non-responders: " + e.getMessage());
                                    }
                                });
                    } else {
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user data", e);
                    if (callback != null) {
                        callback.onError("Failed to fetch user data: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Builds a cancelled entry from user data.
     */
    private Map<String, Object> buildCancelledEntry(String uid, DocumentSnapshot userDoc) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("cancelledAt", System.currentTimeMillis());
        
        if (userDoc != null && userDoc.exists()) {
            String displayName = userDoc.getString("fullName");
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = userDoc.getString("name");
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                String first = userDoc.getString("firstName");
                String last = userDoc.getString("lastName");
                displayName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            }
            if (displayName != null && !displayName.trim().isEmpty()) {
                data.put("name", displayName);
            }
            
            String email = userDoc.getString("email");
            if (email != null && !email.trim().isEmpty()) {
                data.put("email", email);
            }
        }
        
        return data;
    }
    
    /**
     * Sends notification to users who missed the deadline to accept/decline.
     */
    private void sendDeadlineMissedNotification(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Sending deadline missed notification to " + userIds.size() + " users");
        
        // Check if deadline notification already sent to avoid duplicates
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        Log.e(TAG, "Event not found for deadline notification");
                        return;
                    }
                    
                    // Skip if event start date has already passed
                    long currentTime = System.currentTimeMillis();
                    Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                    if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                        Log.d(TAG, "Event " + eventId + " start date has already passed, skipping deadline notification");
                        return;
                    }
                    
                    Boolean deadlineNotificationSent = eventDoc.getBoolean("deadlineNotificationSent");
                    if (Boolean.TRUE.equals(deadlineNotificationSent)) {
                        Log.d(TAG, "Deadline notification already sent for event " + eventId);
                        return;
                    }
                    
                    String eventTitle = eventDoc.getString("title");
                    if (eventTitle == null || eventTitle.trim().isEmpty()) {
                        eventTitle = "this event";
                    }
                    
                    String notificationTitle = "Update: " + eventTitle;
                    String notificationMessage = "Sorry, you won't be in the criteria for " + eventTitle + 
                        " anymore. The deadline to accept or decline your invitation has passed. " +
                        "Better luck next time!";
                    
                    NotificationHelper notificationHelper = new NotificationHelper();
                    notificationHelper.sendNotificationsToUsers(userIds, notificationTitle, notificationMessage,
                            eventId, eventTitle,
                            new NotificationHelper.NotificationCallback() {
                                @Override
                                public void onComplete(int sentCount) {
                                    Log.d(TAG, "Successfully sent deadline missed notification to " + sentCount + " users");
                                    // Mark as sent to prevent duplicates
                                    markDeadlineNotificationSent(eventId);
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Failed to send deadline missed notification: " + error);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for deadline notification", e);
                });
    }
    
    /**
     * Marks the event as having sent the deadline missed notification.
     */
    private void markDeadlineNotificationSent(String eventId) {
        db.collection("events").document(eventId)
                .update("deadlineNotificationSent", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked event " + eventId + " as having sent deadline notification");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark deadline notification as sent for event " + eventId, e);
                });
    }
}

