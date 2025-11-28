package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
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
            
            // FIX: Check both invitation expiresAt AND event deadlineEpochMs
            // Some invitations might have expiresAt set incorrectly, so we also check the event deadline
            // Get all PENDING invitations for this event
            Query invitationsQuery = db.collection("invitations")
                    .whereEqualTo("eventId", eventId)
                    .whereEqualTo("status", "PENDING");
            
            invitationsQuery.get()
                    .addOnSuccessListener(invitationsSnapshot -> {
                        if (invitationsSnapshot == null || invitationsSnapshot.isEmpty()) {
                            Log.d(TAG, "No pending invitations to process");
                            // FIX: Also check SelectedEntrants directly in case invitations are missing
                            checkSelectedEntrantsDirectly(eventRef, eventId, deadlineEpochMs, currentTime, callback);
                            return;
                        }
                        
                        List<String> nonResponderUserIds = new ArrayList<>();
                        List<String> invitationIds = new ArrayList<>();
                        
                        // FIX: Use event deadlineEpochMs as primary check, with expiresAt as fallback
                        for (QueryDocumentSnapshot doc : invitationsSnapshot) {
                            Long expiresAt = doc.getLong("expiresAt");
                            String uid = doc.getString("uid");
                            
                            if (uid == null || uid.isEmpty()) {
                                continue;
                            }
                            
                            // Check if invitation has expired based on either expiresAt or event deadline
                            boolean hasExpired = false;
                            
                            // Primary check: event deadline has passed
                            if (deadlineEpochMs != null && deadlineEpochMs > 0 && currentTime >= deadlineEpochMs) {
                                hasExpired = true;
                                Log.d(TAG, "Invitation for user " + uid + " expired based on event deadline");
                            }
                            // Fallback check: invitation expiresAt
                            else if (expiresAt != null && expiresAt > 0 && expiresAt <= currentTime) {
                                hasExpired = true;
                                Log.d(TAG, "Invitation for user " + uid + " expired based on invitation expiresAt");
                            }
                            
                            if (hasExpired) {
                                nonResponderUserIds.add(uid);
                                invitationIds.add(doc.getId());
                            }
                        }
                        
                        if (nonResponderUserIds.isEmpty()) {
                            Log.d(TAG, "No expired invitations found based on deadline check");
                            // FIX: Also check SelectedEntrants directly in case invitations are missing
                            checkSelectedEntrantsDirectly(eventRef, eventId, deadlineEpochMs, currentTime, callback);
                            return;
                        }
                        
                        Log.d(TAG, "Found " + nonResponderUserIds.size() + " non-responders to move to CancelledEntrants");
                        moveNonRespondersToCancelled(eventRef, eventId, nonResponderUserIds, invitationIds, callback);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load pending invitations", e);
                        // FIX: On error, still try to check SelectedEntrants directly
                        Log.w(TAG, "Falling back to direct SelectedEntrants check");
                        checkSelectedEntrantsDirectly(eventRef, eventId, deadlineEpochMs, currentTime, callback);
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
                                    Log.d(TAG, "Successfully moved " + userIds.size() + 
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
    
    /**
     * FIX: Check SelectedEntrants directly if invitations query fails or returns no results.
     * This ensures we catch all non-responders even if invitations are missing or incorrectly set.
     */
    private void checkSelectedEntrantsDirectly(DocumentReference eventRef, String eventId, 
                                                Long deadlineEpochMs, long currentTime,
                                                DeadlineCallback callback) {
        Log.d(TAG, "Checking SelectedEntrants directly for non-responders");
        
        // Only proceed if deadline has passed
        if (deadlineEpochMs == null || deadlineEpochMs <= 0 || currentTime < deadlineEpochMs) {
            Log.d(TAG, "Deadline has not passed, skipping direct SelectedEntrants check");
            if (callback != null) {
                callback.onComplete(0);
            }
            return;
        }
        
        eventRef.collection("SelectedEntrants").get()
                .addOnSuccessListener(selectedSnapshot -> {
                    if (selectedSnapshot == null || selectedSnapshot.isEmpty()) {
                        Log.d(TAG, "No selected entrants to check");
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                        return;
                    }
                    
                    // Get all ACCEPTED invitations to check which users have responded
                    List<Task<QuerySnapshot>> responseTasks = new ArrayList<>();
                    responseTasks.add(db.collection("invitations")
                            .whereEqualTo("eventId", eventId)
                            .whereEqualTo("status", "ACCEPTED")
                            .get());
                    responseTasks.add(db.collection("invitations")
                            .whereEqualTo("eventId", eventId)
                            .whereEqualTo("status", "DECLINED")
                            .get());
                    
                    Tasks.whenAllSuccess(responseTasks)
                            .addOnSuccessListener(responseResults -> {
                                java.util.Set<String> respondedUserIds = new java.util.HashSet<>();
                                if (responseResults != null) {
                                    for (Object result : responseResults) {
                                        if (result instanceof QuerySnapshot) {
                                            QuerySnapshot snapshot = (QuerySnapshot) result;
                                            for (QueryDocumentSnapshot doc : snapshot) {
                                                String uid = doc.getString("uid");
                                                if (uid != null && !uid.isEmpty()) {
                                                    respondedUserIds.add(uid);
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                List<String> nonResponderUserIds = new ArrayList<>();
                                
                                // Find selected entrants who haven't responded
                                for (QueryDocumentSnapshot doc : selectedSnapshot) {
                                    String userId = doc.getId();
                                    if (!respondedUserIds.contains(userId)) {
                                        nonResponderUserIds.add(userId);
                                    }
                                }
                                
                                if (nonResponderUserIds.isEmpty()) {
                                    Log.d(TAG, "All selected entrants have responded");
                                    if (callback != null) {
                                        callback.onComplete(0);
                                    }
                                    return;
                                }
                                
                                Log.d(TAG, "Found " + nonResponderUserIds.size() + " non-responders via direct SelectedEntrants check");
                                // Find invitation IDs for these users
                                findInvitationIdsForUsers(eventId, nonResponderUserIds, invitationIds -> {
                                    moveNonRespondersToCancelled(eventRef, eventId, nonResponderUserIds, invitationIds, callback);
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load responded invitations", e);
                                // On error, assume all selected entrants are non-responders
                                List<String> allUserIds = new ArrayList<>();
                                for (QueryDocumentSnapshot doc : selectedSnapshot) {
                                    allUserIds.add(doc.getId());
                                }
                                Log.w(TAG, "Moving all " + allUserIds.size() + " selected entrants to cancelled (error checking responses)");
                                findInvitationIdsForUsers(eventId, allUserIds, invitationIds -> {
                                    moveNonRespondersToCancelled(eventRef, eventId, allUserIds, invitationIds, callback);
                                });
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load SelectedEntrants", e);
                    if (callback != null) {
                        callback.onError("Failed to load SelectedEntrants: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Helper method to find invitation IDs for a list of user IDs.
     */
    private void findInvitationIdsForUsers(String eventId, List<String> userIds, 
                                           java.util.function.Consumer<List<String>> callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.accept(new ArrayList<>());
            return;
        }
        
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (String userId : userIds) {
            tasks.add(db.collection("invitations")
                    .whereEqualTo("eventId", eventId)
                    .whereEqualTo("uid", userId)
                    .whereEqualTo("status", "PENDING")
                    .limit(1)
                    .get());
        }
        
        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<String> invitationIds = new ArrayList<>();
                    if (results != null) {
                        for (int i = 0; i < results.size() && i < userIds.size(); i++) {
                            Object result = results.get(i);
                            if (result instanceof QuerySnapshot) {
                                QuerySnapshot snapshot = (QuerySnapshot) result;
                                if (!snapshot.isEmpty()) {
                                    invitationIds.add(snapshot.getDocuments().get(0).getId());
                                }
                            }
                        }
                    }
                    callback.accept(invitationIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to find invitation IDs", e);
                    callback.accept(new ArrayList<>());
                });
    }
}

