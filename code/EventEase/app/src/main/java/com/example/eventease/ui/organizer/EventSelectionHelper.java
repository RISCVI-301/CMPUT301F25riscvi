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
            
            // Skip if event start date has already passed
            Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
            if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                Log.d(TAG, "Event " + eventId + " start date has already passed, skipping selection processing");
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
                    
                    Log.d(TAG, "Selected " + selectedUserIds.size() + " user IDs: " + selectedUserIds);
                    Log.d(TAG, "Total waitlisted: " + availableCount + ", Sample size: " + sampleSize + ", Selected: " + selectedUserIds.size());
                    
                    // CRITICAL FIX: Verify we're not selecting more than sampleSize
                    // If somehow more were selected, truncate BOTH lists to match sampleSize
                    // This ensures we don't move more entrants to SelectedEntrants than sampleSize
                    if (selectedUserIds.size() > sampleSize) {
                        Log.e(TAG, "ERROR: Selected more users than sample size! Selected: " + selectedUserIds.size() + ", Sample size: " + sampleSize);
                        selectedUserIds = selectedUserIds.subList(0, sampleSize);
                        selectedDocs = selectedDocs.subList(0, sampleSize); // FIX: Also truncate selectedDocs
                        Log.d(TAG, "Truncated to sample size: " + selectedUserIds.size() + " users");
                    }
                    
                    // Additional safety check: ensure selectedDocs never exceeds sampleSize
                    if (selectedDocs.size() > sampleSize) {
                        Log.e(TAG, "ERROR: selectedDocs size (" + selectedDocs.size() + ") exceeds sampleSize (" + sampleSize + "), truncating");
                        selectedDocs = selectedDocs.subList(0, sampleSize);
                        // Also ensure selectedUserIds matches
                        if (selectedUserIds.size() > sampleSize) {
                            selectedUserIds = selectedUserIds.subList(0, sampleSize);
                        }
                    }
                    
                    // Final validation: ensure both lists are the same size
                    if (selectedDocs.size() != selectedUserIds.size()) {
                        Log.e(TAG, "ERROR: Mismatch between selectedDocs (" + selectedDocs.size() + ") and selectedUserIds (" + selectedUserIds.size() + ")");
                        int minSize = Math.min(selectedDocs.size(), selectedUserIds.size());
                        selectedDocs = selectedDocs.subList(0, minSize);
                        selectedUserIds = selectedUserIds.subList(0, minSize);
                    }
                    
                    // Final check: ensure we never exceed sampleSize
                    if (selectedDocs.size() > sampleSize) {
                        Log.e(TAG, "CRITICAL: Final check failed - selectedDocs size (" + selectedDocs.size() + ") still exceeds sampleSize (" + sampleSize + ")");
                        selectedDocs = selectedDocs.subList(0, sampleSize);
                        selectedUserIds = selectedUserIds.subList(0, Math.min(selectedUserIds.size(), sampleSize));
                    }
                    
                    Log.d(TAG, "Final selection count: " + selectedDocs.size() + " entrants (sampleSize: " + sampleSize + ")");
                    
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
     * Guarantees that the returned list size never exceeds the requested count.
     */
    private List<DocumentSnapshot> randomlySelect(List<DocumentSnapshot> allDocs, int count) {
        if (allDocs == null || allDocs.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }
        
        if (count >= allDocs.size()) {
            Log.w(TAG, "Requested count (" + count + ") >= available docs (" + allDocs.size() + "), returning all");
            return new ArrayList<>(allDocs);
        }
        
        List<DocumentSnapshot> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random(System.currentTimeMillis()));
        
        // Ensure we only take exactly 'count' items (never more)
        int actualCount = Math.min(count, shuffled.size());
        List<DocumentSnapshot> selected = shuffled.subList(0, actualCount);
        Log.d(TAG, "Randomly selected " + selected.size() + " out of " + allDocs.size() + " documents (requested: " + count + ")");
        
        // Return a new list to avoid issues with subList
        List<DocumentSnapshot> result = new ArrayList<>(selected);
        
        // Final safety check: ensure we never return more than requested
        if (result.size() > count) {
            Log.e(TAG, "CRITICAL ERROR: randomlySelect returned " + result.size() + " items when " + count + " was requested!");
            result = new ArrayList<>(result.subList(0, count));
        }
        
        return result;
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
     * Checks if selection notification was already sent and sends it if not.
     */
    private void checkAndSendSelectionNotification(String eventId, String eventTitle, List<String> userIds,
                                                   Long deadlineEpochMs, InvitationHelper.InvitationCallback callback) {
        // Check if selection notification already sent
        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                Log.e(TAG, "Event not found for selection notification check");
                if (callback != null) {
                    callback.onComplete(userIds.size());
                }
                return;
            }
            
            // Skip if event start date has already passed
            long currentTime = System.currentTimeMillis();
            Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
            if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                Log.d(TAG, "Event " + eventId + " start date has already passed, skipping selection notification");
                if (callback != null) {
                    callback.onComplete(userIds.size());
                }
                return;
            }
            
            Boolean selectionNotificationSent = eventDoc.getBoolean("selectionNotificationSent");
            if (Boolean.TRUE.equals(selectionNotificationSent)) {
                Log.d(TAG, "Selection notification already sent for event " + eventId + ", skipping");
                if (callback != null) {
                    callback.onComplete(userIds.size());
                }
                return;
            }
            
            // Use a transaction to atomically check and set the flag
            // This prevents race conditions where multiple processes try to send notifications
            final long finalCurrentTime = currentTime;
            final Long finalStartsAtEpochMs = startsAtEpochMs;
            db.runTransaction(transaction -> {
                // Re-read the document in the transaction
                DocumentSnapshot snapshot = transaction.get(eventRef);
                if (!snapshot.exists()) {
                    throw new RuntimeException("Event not found");
                }
                
                // Check if notification already sent
                Boolean alreadySent = snapshot.getBoolean("selectionNotificationSent");
                if (Boolean.TRUE.equals(alreadySent)) {
                    throw new RuntimeException("Notification already sent");
                }
                
                // Check if event start date has passed (use values from outer scope)
                if (finalStartsAtEpochMs != null && finalStartsAtEpochMs > 0 && finalCurrentTime >= finalStartsAtEpochMs) {
                    throw new RuntimeException("Event start date has passed");
                }
                
                // Atomically set the flag
                transaction.update(eventRef, "selectionNotificationSent", true);
                return null;
            }).addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Atomically marked selection notification as sent for event " + eventId);
                // Now send the notification
                sendSelectionNotification(eventId, eventTitle, userIds, deadlineEpochMs, eventRef, callback);
            })
            .addOnFailureListener(e -> {
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("already sent") || errorMsg.contains("start date has passed"))) {
                    Log.d(TAG, "Selection notification cannot be sent: " + errorMsg);
                } else {
                    Log.e(TAG, "Transaction failed for selection notification", e);
                }
                // Don't send notification if transaction failed
                if (callback != null) {
                    callback.onComplete(userIds.size());
                }
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to check selection notification status", e);
            // On error, don't send notification to avoid duplicates for past events
            if (callback != null) {
                callback.onComplete(userIds.size());
            }
        });
    }
    
    /**
     * Sends selection notification to selected entrants.
     */
    private void sendSelectionNotification(String eventId, String eventTitle, List<String> userIds,
                                          Long deadlineEpochMs, DocumentReference eventRef,
                                          InvitationHelper.InvitationCallback callback) {
        NotificationHelper notificationHelper = new NotificationHelper();
        
        // Format deadline nicely
        String deadlineText = "N/A";
        if (deadlineEpochMs != null && deadlineEpochMs > 0) {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault());
            deadlineText = dateFormat.format(new java.util.Date(deadlineEpochMs));
        }
        
        String notificationTitle = "You've been selected! 🎉";
        String notificationMessage = "Congratulations! You've been selected for " + 
            (eventTitle != null ? eventTitle : "this event") + 
            ". Please check your invitations to accept or decline. " +
            "Deadline to respond: " + deadlineText;
        
        // Don't filter declined users for selection notification (they haven't declined yet)
        notificationHelper.sendNotificationsToUsers(userIds, notificationTitle, notificationMessage,
                eventId, eventTitle, false, // filterDeclined = false
                new NotificationHelper.NotificationCallback() {
                    @Override
                    public void onComplete(int sentCount) {
                        Log.d(TAG, "✓ Sent push notifications to " + sentCount + " users");
                        // Flag already set before sending, no need to set again
                        if (callback != null) {
                            callback.onComplete(userIds.size());
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to send push notifications: " + error);
                        // Flag already set before sending, no need to set again
                        // Still report success since invitations were created
                        if (callback != null) {
                            callback.onComplete(userIds.size());
                        }
                    }
                });
    }
    
    /**
     * Marks the event as having sent the selection notification.
     */
    private void markSelectionNotificationSent(DocumentReference eventRef) {
        eventRef.update("selectionNotificationSent", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked event as having sent selection notification");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark selection notification as sent", e);
                });
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
                        
                        // Check if selection notification already sent to prevent duplicates
                        checkAndSendSelectionNotification(eventId, eventTitle, userIds, deadlineEpochMs, callback);
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
