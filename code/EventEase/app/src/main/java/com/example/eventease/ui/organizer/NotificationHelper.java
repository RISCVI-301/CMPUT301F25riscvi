package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for sending notifications to different groups of entrants.
 * 
 * <p>This class provides functionality for organizers to send push notifications to:
 * <ul>
 *   <li>Waitlisted entrants</li>
 *   <li>Selected entrants</li>
 *   <li>Cancelled entrants</li>
 * </ul>
 * 
 * <p>Notifications are sent via Firebase Cloud Functions by writing notification requests
 * to the Firestore 'notificationRequests' collection. The Cloud Functions will then:
 * <ol>
 *   <li>Read the notification request</li>
 *   <li>Retrieve FCM tokens for all target users</li>
 *   <li>Send push notifications via FCM</li>
 *   <li>Mark the request as processed</li>
 * </ol>
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private final FirebaseFirestore db;
    
    public NotificationHelper() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    public interface NotificationCallback {
        void onComplete(int sentCount);
        void onError(String error);
    }
    
    /**
     * Sends notifications to all waitlisted entrants for an event.
     * 
     * @param eventId The ID of the event
     * @param eventTitle The title of the event
     * @param message The notification message (optional, will use default if null)
     * @param callback Callback for completion/error
     */
    public void sendNotificationsToWaitlisted(String eventId, String eventTitle, String message, NotificationCallback callback) {
        sendNotificationsToGroup(eventId, eventTitle, "WaitlistedEntrants", "waitlist", message, callback);
    }
    
    /**
     * Sends notifications to all non-selected entrants for an event.
     * 
     * @param eventId The ID of the event
     * @param eventTitle The title of the event
     * @param message The notification message (optional, will use default if null)
     * @param callback Callback for completion/error
     */
    public void sendNotificationsToNonSelected(String eventId, String eventTitle, String message, NotificationCallback callback) {
        sendNotificationsToGroup(eventId, eventTitle, "NonSelectedEntrants", "nonSelected", message, callback);
    }
    
    /**
     * Sends notifications to all selected entrants for an event.
     * 
     * @param eventId The ID of the event
     * @param eventTitle The title of the event
     * @param message The notification message (optional, will use default if null)
     * @param callback Callback for completion/error
     */
    public void sendNotificationsToSelected(String eventId, String eventTitle, String message, NotificationCallback callback) {
        sendNotificationsToGroup(eventId, eventTitle, "SelectedEntrants", "selected", message, callback);
    }
    
    /**
     * Sends notifications to all cancelled entrants for an event.
     * 
     * @param eventId The ID of the event
     * @param eventTitle The title of the event
     * @param message The notification message (optional, will use default if null)
     * @param callback Callback for completion/error
     */
    public void sendNotificationsToCancelled(String eventId, String eventTitle, String message, NotificationCallback callback) {
        sendNotificationsToGroup(eventId, eventTitle, "CancelledEntrants", "cancelled", message, callback);
    }
    
    /**
     * Internal method to send notifications to a specific group of entrants.
     * 
     * @param eventId The event ID
     * @param eventTitle The event title
     * @param subcollectionName The name of the subcollection (WaitlistedEntrants, SelectedEntrants, etc.)
     * @param groupType The type of group (waitlist, selected, cancelled)
     * @param customMessage Custom message (optional)
     * @param callback Callback for completion/error
     */
    private void sendNotificationsToGroup(String eventId, String eventTitle, String subcollectionName, 
                                         String groupType, String customMessage, NotificationCallback callback) {
        if (eventId == null || eventId.isEmpty()) {
            if (callback != null) {
                callback.onError("Event ID is required");
            }
            return;
        }
        
        // Get current user (organizer)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            if (callback != null) {
                callback.onError("User not authenticated");
            }
            return;
        }
        String organizerId = currentUser.getUid();
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        // Get all entrants from the specified subcollection
        eventRef.collection(subcollectionName).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "No entrants in " + subcollectionName + " to send notifications to");
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                        return;
                    }
                    
                    List<DocumentSnapshot> entrantDocs = snapshot.getDocuments();
                    List<String> userIds = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : entrantDocs) {
                        String userId = doc.getId();
                        userIds.add(userId);
                    }
                    
                    if (userIds.isEmpty()) {
                        Log.d(TAG, "No user IDs found in " + subcollectionName);
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                        return;
                    }
                    
                    // Create default message if not provided
                    String notificationMessage = customMessage;
                    if (notificationMessage == null || notificationMessage.trim().isEmpty()) {
                        notificationMessage = getDefaultMessage(groupType, eventTitle);
                    }
                    
                    // Create notification request in Firestore
                    // Cloud Functions will pick this up and send FCM notifications
                    Map<String, Object> notificationRequest = new HashMap<>();
                    notificationRequest.put("eventId", eventId);
                    notificationRequest.put("eventTitle", eventTitle != null ? eventTitle : "Event");
                    notificationRequest.put("organizerId", organizerId);
                    notificationRequest.put("userIds", userIds);
                    notificationRequest.put("groupType", groupType);
                    notificationRequest.put("message", notificationMessage);
                    notificationRequest.put("title", getDefaultTitle(groupType, eventTitle));
                    notificationRequest.put("status", "PENDING");
                    notificationRequest.put("createdAt", System.currentTimeMillis());
                    notificationRequest.put("processed", false);
                    
                    // Write to notificationRequests collection
                    db.collection("notificationRequests").add(notificationRequest)
                            .addOnSuccessListener(docRef -> {
                                Log.d(TAG, "Notification request created for " + userIds.size() + " users in " + groupType + " group");
                                Log.d(TAG, "Request ID: " + docRef.getId());
                                
                                // Return success - Cloud Functions will handle actual sending
                                if (callback != null) {
                                    callback.onComplete(userIds.size());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to create notification request", e);
                                if (callback != null) {
                                    callback.onError("Failed to create notification request: " + e.getMessage());
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load entrants from " + subcollectionName, e);
                    if (callback != null) {
                        callback.onError("Failed to load entrants: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Gets the default notification title based on group type.
     */
    private String getDefaultTitle(String groupType, String eventTitle) {
        String eventName = eventTitle != null ? eventTitle : "Event";
        switch (groupType) {
            case "waitlist":
                return "Update: " + eventName;
            case "selected":
                return "You've been selected! 🎉";
            case "cancelled":
                return "Update: " + eventName;
            case "nonSelected":
                return "Update: " + eventName;
            default:
                return "Event Update";
        }
    }
    
    /**
     * Gets the default notification message based on group type.
     */
    private String getDefaultMessage(String groupType, String eventTitle) {
        String eventName = eventTitle != null ? eventTitle : "this event";
        switch (groupType) {
            case "waitlist":
                return "You are on the waitlist for " + eventName + ". We'll notify you if a spot becomes available.";
            case "selected":
                return "Congratulations! You've been selected for " + eventName + ". Please check your invitations.";
            case "cancelled":
                return "Your registration for " + eventName + " has been cancelled. Please contact the organizer if you have questions.";
            case "nonSelected":
                return "Thank you for your interest in " + eventName + ". Unfortunately, you were not selected this time. We appreciate your participation.";
            default:
                return "You have an update regarding " + eventName + ".";
        }
    }
    
    /**
     * Sends notifications to a specific list of users with custom title and message.
     * This is used for replacement invitations and other custom notifications.
     * 
     * @param userIds List of user IDs to notify
     * @param title Custom notification title
     * @param message Custom notification message
     * @param eventId The event ID
     * @param eventTitle The event title
     * @param callback Callback for completion/error
     */
    public void sendNotificationsToUsers(List<String> userIds, String title, String message, 
                                         String eventId, String eventTitle, NotificationCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            if (callback != null) {
                callback.onComplete(0);
            }
            return;
        }
        
        if (eventId == null || eventId.isEmpty()) {
            if (callback != null) {
                callback.onError("Event ID is required");
            }
            return;
        }
        
        // Get current user (organizer)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            if (callback != null) {
                callback.onError("User not authenticated");
            }
            return;
        }
        String organizerId = currentUser.getUid();
        
        // Create notification request in Firestore
        // Cloud Functions will pick this up and send FCM notifications
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("eventId", eventId);
        notificationRequest.put("eventTitle", eventTitle != null ? eventTitle : "Event");
        notificationRequest.put("organizerId", organizerId);
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("groupType", "replacement");
        notificationRequest.put("message", message);
        notificationRequest.put("title", title);
        notificationRequest.put("status", "PENDING");
        notificationRequest.put("createdAt", System.currentTimeMillis());
        notificationRequest.put("processed", false);
        
        // Write to notificationRequests collection
        db.collection("notificationRequests").add(notificationRequest)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Custom notification request created for " + userIds.size() + " users");
                    Log.d(TAG, "Request ID: " + docRef.getId());
                    
                    // Return success - Cloud Functions will handle actual sending
                    if (callback != null) {
                        callback.onComplete(userIds.size());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create custom notification request", e);
                    if (callback != null) {
                        callback.onError("Failed to create notification request: " + e.getMessage());
                    }
                });
    }
}

