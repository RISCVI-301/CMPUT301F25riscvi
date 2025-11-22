package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Helper class for automatically selecting entrants from waitlist and sending invitations.
 * 
 * <p>This class handles the automatic selection process that occurs after the registration
 * period ends:
 * <ol>
 *   <li>Checks if registration period has ended</li>
 *   <li>Randomly selects entrants from waitlist based on sampleSize</li>
 *   <li>Moves selected entrants to SelectedEntrants subcollection</li>
 *   <li>Automatically creates invitations for selected entrants</li>
 *   <li>Sends push notifications to selected entrants</li>
 *   <li>Moves remaining waitlisted entrants to NonSelectedEntrants after deadline</li>
 * </ol>
 */
public class EventSelectionHelper {
    private static final String TAG = "EventSelectionHelper";
    private final FirebaseFirestore db;
    
    public EventSelectionHelper() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    public interface SelectionCallback {
        void onComplete(int selectedCount);
        void onError(String error);
    }
    
    /**
     * Checks if the registration period has ended and processes selection if needed.
     * 
     * @param eventId The event ID to check
     * @param callback Callback for completion/error
     */
    public void checkAndProcessEventSelection(String eventId, SelectionCallback callback) {
        if (eventId == null || eventId.isEmpty()) {
            if (callback != null) {
                callback.onError("Event ID is required");
            }
            return;
        }
        
        Log.d(TAG, "=== Starting selection check for event: " + eventId + " ===");
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                Log.e(TAG, "Event not found: " + eventId);
                if (callback != null) {
                    callback.onError("Event not found");
                }
                return;
            }
            
            Long registrationEnd = eventDoc.getLong("registrationEnd");
            Boolean selectionProcessed = eventDoc.getBoolean("selectionProcessed");
            String eventTitle = eventDoc.getString("title");
            Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
            
            if (registrationEnd == null || registrationEnd == 0) {
                Log.d(TAG, "Event " + eventId + " does not have a registration end time");
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            if (currentTime < registrationEnd) {
                Log.d(TAG, "Registration deadline has not passed for event " + eventId + 
                    " (ends at: " + registrationEnd + ", current: " + currentTime + ")");
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            
            if (Boolean.TRUE.equals(selectionProcessed)) {
                Log.d(TAG, "Selection already processed for event " + eventId);
                
                // Check if deadline has passed and process non-responders and remaining waitlisted
                if (deadlineEpochMs != null && deadlineEpochMs > 0 && currentTime >= deadlineEpochMs) {
                    // Process non-responders first
                    InvitationDeadlineProcessor deadlineProcessor = new InvitationDeadlineProcessor();
                    deadlineProcessor.processDeadlineForEvent(eventId, new InvitationDeadlineProcessor.DeadlineCallback() {
                        @Override
                        public void onComplete(int processedCount) {
                            Log.d(TAG, "Processed " + processedCount + " non-responders");
                            // Then move remaining waitlisted to NonSelectedEntrants
                            moveRemainingWaitlistedToNonSelected(eventRef, eventId, callback);
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error processing deadline: " + error);
                            // Still try to move waitlisted
                            moveRemainingWaitlistedToNonSelected(eventRef, eventId, callback);
                        }
                    });
                } else {
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                }
                return;
            }
            
            Integer sampleSize = eventDoc.getLong("sampleSize") != null ? 
                eventDoc.getLong("sampleSize").intValue() : 0;
            
            if (sampleSize <= 0) {
                Log.d(TAG, "Event " + eventId + " has invalid or zero sample size");
                markAsProcessed(eventRef, callback);
                return;
            }
            
            Log.d(TAG, "Event " + eventId + " has sample size: " + sampleSize);
            processSelection(eventRef, eventId, sampleSize, eventTitle, deadlineEpochMs, callback);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load event " + eventId, e);
            if (callback != null) {
                callback.onError("Failed to load event: " + e.getMessage());
            }
        });
    }
    
    /**
     * Processes the selection: randomly selects entrants and sends invitations.
     */
    private void processSelection(DocumentReference eventRef, String eventId, int sampleSize, 
                                  String eventTitle, Long deadlineEpochMs, SelectionCallback callback) {
        Log.d(TAG, "=== Processing selection for event " + eventId + " with sample size " + sampleSize + " ===");
        
        eventRef.collection("WaitlistedEntrants").get()
                .addOnSuccessListener(waitlistSnapshot -> {
                    if (waitlistSnapshot == null || waitlistSnapshot.isEmpty()) {
                        Log.d(TAG, "No waitlisted entrants to select from");
                        markAsProcessed(eventRef, callback);
                        return;
                    }
                    
                    List<DocumentSnapshot> waitlistedDocs = waitlistSnapshot.getDocuments();
                    int availableCount = waitlistedDocs.size();
                    int toSelect = Math.min(sampleSize, availableCount);
                    
                    if (toSelect == 0) {
                        Log.d(TAG, "No entrants to select (sample size: " + sampleSize + ", available: " + availableCount + ")");
                        markAsProcessed(eventRef, callback);
                        return;
                    }
                    
                    Log.d(TAG, "Randomly selecting " + toSelect + " out of " + availableCount + " waitlisted entrants");
                    
                    List<DocumentSnapshot> selectedDocs = randomlySelect(waitlistedDocs, toSelect);
                    List<String> selectedUserIds = new ArrayList<>();
                    for (DocumentSnapshot doc : selectedDocs) {
                        selectedUserIds.add(doc.getId());
                    }
                    
                    Log.d(TAG, "Selected user IDs: " + selectedUserIds);
                    
                    moveToSelectedAndSendInvitations(eventRef, eventId, selectedDocs, selectedUserIds, 
                                                     eventTitle, deadlineEpochMs, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load waitlisted entrants", e);
                    if (callback != null) {
                        callback.onError("Failed to load waitlisted entrants: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Randomly selects a specified number of documents from a list.
     */
    private List<DocumentSnapshot> randomlySelect(List<DocumentSnapshot> allDocs, int count) {
        List<DocumentSnapshot> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, count);
    }
    
    /**
     * Moves selected entrants to SelectedEntrants subcollection and automatically sends invitations.
     */
    private void moveToSelectedAndSendInvitations(DocumentReference eventRef, String eventId,
                                                  List<DocumentSnapshot> selectedDocs, 
                                                  List<String> selectedUserIds,
                                                  String eventTitle, Long deadlineEpochMs,
                                                  SelectionCallback callback) {
        if (selectedDocs == null || selectedDocs.isEmpty()) {
            markAsProcessed(eventRef, callback);
            return;
        }
        
        Log.d(TAG, "=== Moving " + selectedDocs.size() + " entrants to SelectedEntrants ===");
        
        WriteBatch batch = db.batch();
        int batchCount = 0;
        final int MAX_BATCH_SIZE = 499;
        List<Task<Void>> batchTasks = new ArrayList<>();
        
        for (DocumentSnapshot doc : selectedDocs) {
            String userId = doc.getId();
            Map<String, Object> data = doc.getData();
            
            if (data == null) {
                continue;
            }
            
            DocumentReference selectedRef = eventRef.collection("SelectedEntrants").document(userId);
            DocumentReference waitlistRef = eventRef.collection("WaitlistedEntrants").document(userId);
            
            batch.set(selectedRef, data);
            batch.delete(waitlistRef);
            batchCount += 2;
            
            if (batchCount >= MAX_BATCH_SIZE) {
                final WriteBatch currentBatch = batch;
                batchTasks.add(currentBatch.commit());
                batch = db.batch();
                batchCount = 0;
            }
        }
        
        batch.update(eventRef, "waitlistCount", 
            com.google.firebase.firestore.FieldValue.increment(-selectedDocs.size()));
        batchCount++;
        
        if (batchCount > 0) {
            batchTasks.add(batch.commit());
        }
        
        if (!batchTasks.isEmpty()) {
            Tasks.whenAll(batchTasks)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Successfully moved " + selectedDocs.size() + " entrants to SelectedEntrants");
                        Log.d(TAG, "=== Now sending invitations and notifications ===");
                        
                        // Automatically send invitations and notifications
                        sendInvitationsToSelected(eventId, eventTitle, selectedUserIds, deadlineEpochMs, 
                                                  new InvitationHelper.InvitationCallback() {
                            @Override
                            public void onComplete(int sentCount) {
                                Log.d(TAG, "✓ Successfully sent " + sentCount + " invitations with push notifications");
                                markAsProcessed(eventRef, new SelectionCallback() {
                                    @Override
                                    public void onComplete(int selectedCount) {
                                        Log.d(TAG, "=== Selection process completed successfully ===");
                                        if (callback != null) {
                                            callback.onComplete(selectedDocs.size());
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        Log.e(TAG, "Error marking as processed: " + error);
                                        if (callback != null) {
                                            callback.onComplete(selectedDocs.size());
                                        }
                                    }
                                });
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Failed to send invitations: " + error);
                                // Still mark as processed since selection was successful
                                markAsProcessed(eventRef, new SelectionCallback() {
                                    @Override
                                    public void onComplete(int selectedCount) {
                                        if (callback != null) {
                                            callback.onComplete(selectedDocs.size());
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(String error2) {
                                        if (callback != null) {
                                            callback.onComplete(selectedDocs.size());
                                        }
                                    }
                                });
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to move entrants to SelectedEntrants", e);
                        if (callback != null) {
                            callback.onError("Failed to move entrants: " + e.getMessage());
                        }
                    });
        } else {
            markAsProcessed(eventRef, callback);
        }
    }
    
    /**
     * Sends invitations to selected entrants and push notifications.
     */
    private void sendInvitationsToSelected(String eventId, String eventTitle, List<String> userIds,
                                           Long deadlineEpochMs, InvitationHelper.InvitationCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            if (callback != null) {
                callback.onComplete(0);
            }
            return;
        }
        
        Log.d(TAG, "Sending invitations to " + userIds.size() + " selected entrants");
        
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) {
                callback.onError("User not authenticated");
            }
            return;
        }
        String organizerId = currentUser.getUid();
        
        long currentTime = System.currentTimeMillis();
        long expiresAt = deadlineEpochMs != null && deadlineEpochMs > 0 ? deadlineEpochMs : 
                         (currentTime + (7L * 24 * 60 * 60 * 1000)); // Default 7 days
        
        WriteBatch batch = db.batch();
        int batchCount = 0;
        final int MAX_BATCH_SIZE = 500;
        List<Task<Void>> invitationTasks = new ArrayList<>();
        
        for (String userId : userIds) {
            String invitationId = UUID.randomUUID().toString();
            DocumentReference invitationRef = db.collection("invitations").document(invitationId);
            
            Map<String, Object> invitationData = new HashMap<>();
            invitationData.put("id", invitationId);
            invitationData.put("eventId", eventId);
            invitationData.put("uid", userId);
            invitationData.put("entrantId", userId);
            invitationData.put("organizerId", organizerId);
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
                        Log.d(TAG, "✓ Created " + userIds.size() + " invitation documents");
                        
                        // Send push notifications via NotificationHelper
                        NotificationHelper notificationHelper = new NotificationHelper();
                        String notificationMessage = "You've been selected! Please check your invitations. " +
                            "Deadline to accept/decline: " + 
                            (deadlineEpochMs != null ? new java.util.Date(deadlineEpochMs).toString() : "N/A");
                        
                        notificationHelper.sendNotificationsToUsers(userIds,
                                "You've been selected! 🎉", 
                                "Congratulations! You've been selected for " + (eventTitle != null ? eventTitle : "this event") + 
                                ". Please check your invitations. Deadline: " + 
                                (deadlineEpochMs != null ? new java.util.Date(deadlineEpochMs).toString() : "N/A"),
                                eventId, eventTitle,
                                new NotificationHelper.NotificationCallback() {
                                    @Override
                                    public void onComplete(int sentCount) {
                                        Log.d(TAG, "✓ Sent push notifications to " + sentCount + " users");
                                        if (callback != null) {
                                            callback.onComplete(userIds.size());
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        Log.e(TAG, "Failed to send push notifications: " + error);
                                        // Still report success since invitations were created
                                        if (callback != null) {
                                            callback.onComplete(userIds.size());
                                        }
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
    }
    
    /**
     * Moves remaining waitlisted entrants to NonSelectedEntrants after deadline has passed.
     */
    private void moveRemainingWaitlistedToNonSelected(DocumentReference eventRef, String eventId, 
                                                      SelectionCallback callback) {
        Log.d(TAG, "=== Moving remaining waitlisted entrants to NonSelectedEntrants ===");
        
        eventRef.collection("WaitlistedEntrants").get()
                .addOnSuccessListener(waitlistSnapshot -> {
                    if (waitlistSnapshot == null || waitlistSnapshot.isEmpty()) {
                        Log.d(TAG, "No remaining waitlisted entrants to move");
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                        return;
                    }
                    
                    List<DocumentSnapshot> waitlistedDocs = waitlistSnapshot.getDocuments();
                    Log.d(TAG, "Moving " + waitlistedDocs.size() + " remaining waitlisted entrants to NonSelectedEntrants");
                    
                    WriteBatch batch = db.batch();
                    int batchCount = 0;
                    final int MAX_BATCH_SIZE = 499;
                    List<Task<Void>> batchTasks = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : waitlistedDocs) {
                        String userId = doc.getId();
                        Map<String, Object> data = doc.getData();
                        
                        if (data == null) {
                            continue;
                        }
                        
                        DocumentReference nonSelectedRef = eventRef.collection("NonSelectedEntrants").document(userId);
                        DocumentReference waitlistRef = eventRef.collection("WaitlistedEntrants").document(userId);
                        
                        batch.set(nonSelectedRef, data);
                        batch.delete(waitlistRef);
                        batchCount += 2;
                        
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
                                    Log.d(TAG, "✓ Successfully moved " + waitlistedDocs.size() + 
                                        " waitlisted entrants to NonSelectedEntrants");
                                    if (callback != null) {
                                        callback.onComplete(0);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to move waitlisted to NonSelectedEntrants", e);
                                    if (callback != null) {
                                        callback.onError("Failed to move entrants: " + e.getMessage());
                                    }
                                });
                    } else {
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load waitlisted entrants", e);
                    if (callback != null) {
                        callback.onError("Failed to load waitlisted entrants: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Marks the event as selection processed.
     */
    private void markAsProcessed(DocumentReference eventRef, SelectionCallback callback) {
        eventRef.update("selectionProcessed", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Marked event as selection processed");
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to mark event as processed", e);
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                });
    }
}
