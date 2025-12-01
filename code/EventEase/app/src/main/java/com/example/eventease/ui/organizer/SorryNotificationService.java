package com.example.eventease.ui.organizer;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Service that automatically sends "sorry" notifications to non-selected entrants
 * when the event is about to start.
 * 
 * <p>This service monitors events and automatically sends notifications to entrants
 * in the NonSelectedEntrants subcollection right before the event starts.
 */
public class SorryNotificationService {
    private static final String TAG = "SorryNotificationService";
    // Send notification 1 minute before event start (in milliseconds)
    private static final long NOTIFICATION_BEFORE_START_MS = 1L * 60 * 1000;
    private static ListenerRegistration listenerRegistration;
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    /**
     * Sets up a Firestore listener to automatically send "sorry" notifications
     * shortly before event start dates.
     */
    public static void setupSorryNotificationListener() {
        if (listenerRegistration != null) {
            Log.d(TAG, "Sorry notification listener already set up");
            return;
        }
        
        Log.d(TAG, "Setting up automatic 'sorry' notification listener");
        
        // Listen to events - only process document changes to avoid processing all events
        listenerRegistration = db.collection("events")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error in sorry notification listener", error);
                        return;
                    }
                    
                    if (snapshot == null) {
                        return;
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    
                    // Only process document changes (ADDED or MODIFIED), not all documents
                    // This prevents processing all events on every snapshot
                    for (com.google.firebase.firestore.DocumentChange change : snapshot.getDocumentChanges()) {
                        // Only process if document was added or modified
                        if (change.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED && 
                            change.getType() != com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            continue;
                        }
                        
                        DocumentSnapshot eventDoc = change.getDocument();
                        checkAndSendSorryNotifications(eventDoc, currentTime);
                    }
                });
    }
    
    /**
     * Stops the sorry notification listener.
     */
    public static void stopListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
            Log.d(TAG, "Stopped sorry notification listener");
        }
    }
    
    /**
     * Checks if an event is about to start and sends sorry notifications if needed.
     */
    private static void checkAndSendSorryNotifications(DocumentSnapshot eventDoc, long currentTime) {
        Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
        Boolean sorryNotificationSent = eventDoc.getBoolean("sorryNotificationSent");
        String eventId = eventDoc.getId();
        String eventTitle = eventDoc.getString("title");
        
        // Skip if event doesn't have a start time
        if (startsAtEpochMs == null || startsAtEpochMs <= 0) {
            return;
        }
        
        // Skip if event start date has already passed
        if (currentTime >= startsAtEpochMs) {
            Log.d(TAG, "Event " + eventId + " start date has already passed, skipping notification");
            return;
        }
        
        // Skip if sorry notification already sent
        if (Boolean.TRUE.equals(sorryNotificationSent)) {
            return;
        }
        
        // Calculate notification time (1 minute before event start)
        long notificationTime = startsAtEpochMs - NOTIFICATION_BEFORE_START_MS;
        
        // Check if we're within the notification window (1 minute before, with 30 second tolerance)
        long toleranceMs = 30 * 1000; // 30 seconds
        if (currentTime >= notificationTime - toleranceMs && currentTime <= notificationTime + toleranceMs) {
            Log.d(TAG, "Event " + eventId + " is 1 minute before start, sending sorry notifications");
            sendSorryNotifications(eventId, eventTitle != null ? eventTitle : "this event");
        }
    }
    
    /**
     * Sends sorry notifications to all non-selected entrants for an event.
     */
    private static void sendSorryNotifications(String eventId, String eventTitle) {
        Log.d(TAG, "Sending sorry notifications for event: " + eventId);
        
        db.collection("events").document(eventId)
                .collection("NonSelectedEntrants")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "No non-selected entrants to notify for event " + eventId);
                        // Still mark as sent to avoid retrying
                        markSorryNotificationSent(eventId);
                        return;
                    }
                    
                    List<DocumentSnapshot> nonSelectedDocs = snapshot.getDocuments();
                    List<String> userIds = new java.util.ArrayList<>();
                    
                    for (DocumentSnapshot doc : nonSelectedDocs) {
                        userIds.add(doc.getId());
                    }
                    
                    if (userIds.isEmpty()) {
                        Log.d(TAG, "No user IDs found in NonSelectedEntrants for event " + eventId);
                        markSorryNotificationSent(eventId);
                        return;
                    }
                    
                    // Format event date nicely
                    db.collection("events").document(eventId).get()
                            .addOnSuccessListener(eventDoc -> {
                                Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                                String eventDateText = "the event";
                                
                                if (startsAtEpochMs != null && startsAtEpochMs > 0) {
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                                    eventDateText = dateFormat.format(new Date(startsAtEpochMs));
                                }
                                
                                String notificationTitle = "Update: " + eventTitle;
                                String notificationMessage = "Thank you for your interest in " + eventTitle + 
                                    ". Unfortunately, you were not selected this time. " +
                                    "The event will take place on " + eventDateText + ". " +
                                    "Oops, the event selection has been done. Better luck next time!";
                                
                                NotificationHelper notificationHelper = new NotificationHelper();
                                notificationHelper.sendNotificationsToUsers(userIds, notificationTitle, notificationMessage,
                                        eventId, eventTitle,
                                        new NotificationHelper.NotificationCallback() {
                                            @Override
                                            public void onComplete(int sentCount) {
                                                Log.d(TAG, "Successfully sent " + sentCount + " 'sorry' notifications for event " + eventId);
                                                markSorryNotificationSent(eventId);
                                            }
                                            
                                            @Override
                                            public void onError(String error) {
                                                Log.e(TAG, "Failed to send 'sorry' notifications: " + error);
                                                // Don't mark as sent if there was an error, so we can retry
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load event details for sorry notification", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load non-selected entrants for event " + eventId, e);
                });
    }
    
    /**
     * Marks the event as having sent the sorry notification.
     */
    private static void markSorryNotificationSent(String eventId) {
        db.collection("events").document(eventId)
                .update("sorryNotificationSent", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked event " + eventId + " as having sent sorry notification");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark sorry notification as sent for event " + eventId, e);
                });
    }
}

