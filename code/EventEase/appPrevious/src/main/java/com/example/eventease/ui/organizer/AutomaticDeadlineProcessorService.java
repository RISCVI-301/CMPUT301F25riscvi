package com.example.eventease.ui.organizer;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Service that automatically processes invitation deadlines when they pass.
 * 
 * <p>This service monitors events and automatically processes deadlines:
 * <ul>
 *   <li>Moves non-responders from SelectedEntrants to CancelledEntrants</li>
 *   <li>Sends "sorry" notifications to those who missed the deadline</li>
 * </ul>
 */
public class AutomaticDeadlineProcessorService {
    private static final String TAG = "AutoDeadlineProcessor";
    private static ListenerRegistration deadlineListenerRegistration;
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static boolean isInitialLoad = true;
    private static long listenerSetupTime = 0;
    private static final long INITIAL_LOAD_SKIP_DURATION_MS = 10 * 1000; // Skip processing for 10 seconds after listener setup
    
    /**
     * Sets up a Firestore listener to automatically process deadlines when they pass.
     */
    public static void setupDeadlineProcessorListener() {
        // Remove existing listener if any to prevent duplicates
        if (deadlineListenerRegistration != null) {
            Log.d(TAG, "Removing existing deadline processor listener to prevent duplicates");
            deadlineListenerRegistration.remove();
            deadlineListenerRegistration = null;
        }
        
        Log.d(TAG, "Setting up automatic deadline processor listener");
        
        // Record when listener is set up
        listenerSetupTime = System.currentTimeMillis();
        isInitialLoad = true;
        
        InvitationDeadlineProcessor deadlineProcessor = new InvitationDeadlineProcessor();
        
        // Listen to events - only process document changes
        deadlineListenerRegistration = db.collection("events")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error in deadline processor listener", error);
                        return;
                    }
                    
                    if (snapshot == null) {
                        return;
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    
                    // Only process changed documents (ADDED or MODIFIED)
                    for (com.google.firebase.firestore.DocumentChange change : snapshot.getDocumentChanges()) {
                        // Only process if document was added or modified
                        if (change.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED && 
                            change.getType() != com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            continue;
                        }
                        
                        DocumentSnapshot eventDoc = change.getDocument();
                        String eventId = eventDoc.getId();
                        Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
                        Boolean deadlineNotificationSent = eventDoc.getBoolean("deadlineNotificationSent");
                        
                        // Skip if no deadline set
                        if (deadlineEpochMs == null || deadlineEpochMs <= 0) {
                            continue;
                        }
                        
                        // Skip if deadline notification already sent
                        if (Boolean.TRUE.equals(deadlineNotificationSent)) {
                            continue;
                        }
                        
                        // Skip if event start date has already passed
                        Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                        if (startsAtEpochMs != null && startsAtEpochMs > 0 && currentTime >= startsAtEpochMs) {
                            continue;
                        }
                        
                        // Check if deadline has passed
                        if (currentTime >= deadlineEpochMs) {
                            // For initial load, skip events that were created before listener setup
                            if (isInitialLoad) {
                                Long createdAt = eventDoc.getLong("createdAt");
                                if (createdAt == null || createdAt == 0) {
                                    // No createdAt field, check if deadline is old (more than 5 seconds ago)
                                    if ((currentTime - deadlineEpochMs) > 5000) {
                                        Log.d(TAG, "Skipping old deadline for event " + eventId + " on initial load");
                                        continue;
                                    }
                                } else if (listenerSetupTime > 0 && (createdAt < (listenerSetupTime - 5000))) {
                                    Log.d(TAG, "Skipping old deadline for event " + eventId + " created before listener setup");
                                    continue;
                                }
                            }
                            
                            Log.d(TAG, "Auto-processing deadline for event: " + eventId + 
                                " (deadline was at " + new java.util.Date(deadlineEpochMs) + ")");
                            
                            deadlineProcessor.processDeadlineForEvent(eventId, new InvitationDeadlineProcessor.DeadlineCallback() {
                                @Override
                                public void onComplete(int processedCount) {
                                    if (processedCount > 0) {
                                        Log.d(TAG, "Auto-deadline processing completed: " + processedCount + 
                                            " non-responders processed for event " + eventId);
                                    }
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Auto-deadline processing error for event " + eventId + ": " + error);
                                }
                            });
                        }
                    }
                    
                    // Mark initial load as complete after processing first batch
                    if (isInitialLoad) {
                        isInitialLoad = false;
                        Log.d(TAG, "Initial load complete for deadline processor, will process new deadlines normally");
                    }
                });
    }
    
    /**
     * Stops the deadline processor listener.
     */
    public static void stopDeadlineProcessorListener() {
        if (deadlineListenerRegistration != null) {
            deadlineListenerRegistration.remove();
            deadlineListenerRegistration = null;
            isInitialLoad = true;
            Log.d(TAG, "Stopped deadline processor listener");
        }
    }
}

