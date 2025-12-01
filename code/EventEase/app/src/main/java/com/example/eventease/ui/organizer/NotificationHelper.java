package com.example.eventease.ui.organizer;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        // Get organizerId from event document (already stored there)
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (!eventDoc.exists()) {
                Log.e(TAG, "Event not found: " + eventId);
                if (callback != null) {
                    callback.onError("Event not found");
                }
                return;
            }
            
            String organizerId = eventDoc.getString("organizerId");
            if (organizerId == null || organizerId.isEmpty()) {
                Log.w(TAG, "No organizerId in event document, this should not happen");
                organizerId = "unknown";
            }
            
            String finalOrganizerId = organizerId;
            
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
                    
                    // Filter users based on their notification preferences before sending
                    filterUsersByPreferences(userIds, groupType, filteredUserIds -> {
                        if (filteredUserIds.isEmpty()) {
                            Log.d(TAG, "No users with matching notification preferences for " + groupType);
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
                        notificationRequest.put("organizerId", finalOrganizerId);
                        notificationRequest.put("userIds", filteredUserIds);
                        notificationRequest.put("groupType", groupType);
                        notificationRequest.put("message", notificationMessage);
                        notificationRequest.put("title", getDefaultTitle(groupType, eventTitle));
                        notificationRequest.put("status", "PENDING");
                        notificationRequest.put("createdAt", System.currentTimeMillis());
                        notificationRequest.put("processed", false);
                        
                        // Write to notificationRequests collection
                        db.collection("notificationRequests").add(notificationRequest)
                                .addOnSuccessListener(docRef -> {
                                    Log.d(TAG, "Notification request created for " + filteredUserIds.size() + " users in " + groupType + " group");
                                    Log.d(TAG, "Request ID: " + docRef.getId());
                                    
                                    // Return success - Cloud Functions will handle actual sending
                                    if (callback != null) {
                                        callback.onComplete(filteredUserIds.size());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create notification request", e);
                                    if (callback != null) {
                                        callback.onError("Failed to create notification request: " + e.getMessage());
                                    }
                                });
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load entrants from " + subcollectionName, e);
                    if (callback != null) {
                        callback.onError("Failed to load entrants: " + e.getMessage());
                    }
                });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load event document", e);
            if (callback != null) {
                callback.onError("Failed to load event: " + e.getMessage());
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
        sendNotificationsToUsers(userIds, title, message, eventId, eventTitle, true, callback);
    }
    
    /**
     * Sends notifications to a specific list of users with custom title and message.
     * 
     * @param userIds List of user IDs to notify
     * @param title Custom notification title
     * @param message Custom notification message
     * @param eventId The event ID
     * @param eventTitle The event title
     * @param filterDeclined If true, filter out users who have declined
     * @param callback Callback for completion/error
     */
    public void sendNotificationsToUsers(List<String> userIds, String title, String message, 
                                         String eventId, String eventTitle, boolean filterDeclined,
                                         NotificationCallback callback) {
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
        
        if (filterDeclined) {
            // Filter out users who have declined - they shouldn't receive notifications
            filterOutDeclinedUsers(eventId, userIds, filteredUserIds -> {
                if (filteredUserIds.isEmpty()) {
                    Log.d(TAG, "All users have declined, no notifications to send");
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                    return;
                }
                
                // Get organizerId from event document (more reliable than current user)
                db.collection("events").document(eventId).get()
                        .addOnSuccessListener(eventDoc -> {
                            String organizerId = null;
                            if (eventDoc.exists()) {
                                organizerId = eventDoc.getString("organizerId");
                            }
                            
                            // Event should always have organizerId stored when created
                            if (organizerId == null || organizerId.isEmpty()) {
                                Log.e(TAG, "No organizerId in event document");
                                if (callback != null) {
                                    callback.onError("Event has no organizer ID");
                                }
                                return;
                            }
                            
                            Log.d(TAG, "Sending notifications (filtered) with organizerId: " + organizerId);
                            sendNotificationsToFilteredUsers(filteredUserIds, title, message, eventId, eventTitle, organizerId, callback);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get event document for organizerId", e);
                            if (callback != null) {
                                callback.onError("Failed to get event details");
                            }
                        });
            });
        } else {
            // Don't filter - send to all (e.g., for selection/replacement notifications before anyone has declined)
            // Get organizerId from event document (more reliable than current user)
            db.collection("events").document(eventId).get()
                    .addOnSuccessListener(eventDoc -> {
                        String organizerId = null;
                        if (eventDoc.exists()) {
                            organizerId = eventDoc.getString("organizerId");
                        }
                        
                        // Event should always have organizerId stored when created
                        if (organizerId == null || organizerId.isEmpty()) {
                            Log.e(TAG, "No organizerId in event document");
                            if (callback != null) {
                                callback.onError("Event has no organizer ID");
                            }
                            return;
                        }
                        
                        Log.d(TAG, "Sending notifications with organizerId: " + organizerId);
                        sendNotificationsToFilteredUsers(userIds, title, message, eventId, eventTitle, organizerId, callback);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get event document for organizerId", e);
                        if (callback != null) {
                            callback.onError("Failed to get event details");
                        }
                    });
        }
    }
    
    /**
     * Filters out users who have declined invitations for this event.
     */
    private void filterOutDeclinedUsers(String eventId, List<String> userIds, 
                                        java.util.function.Consumer<List<String>> callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.accept(new java.util.ArrayList<>());
            return;
        }
        
        // Also check CancelledEntrants subcollection (includes declined users)
        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.collection("CancelledEntrants").get()
                .addOnSuccessListener(cancelledSnapshot -> {
                    java.util.Set<String> declinedUserIds = new java.util.HashSet<>();
                    
                    // Add users from CancelledEntrants (these are declined or missed deadline)
                    if (cancelledSnapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : cancelledSnapshot.getDocuments()) {
                            declinedUserIds.add(doc.getId());
                        }
                    }
                    
                    // Also check invitations collection for declined status (for users who declined but not yet moved)
                    db.collection("invitations")
                            .whereEqualTo("eventId", eventId)
                            .whereEqualTo("status", "DECLINED")
                            .get()
                            .addOnSuccessListener(invitationSnapshot -> {
                                if (invitationSnapshot != null) {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc : invitationSnapshot.getDocuments()) {
                                        String uid = doc.getString("uid");
                                        if (uid != null) {
                                            declinedUserIds.add(uid);
                                        }
                                    }
                                }
                                
                                // Filter out declined users
                                List<String> filtered = new java.util.ArrayList<>();
                                for (String userId : userIds) {
                                    if (!declinedUserIds.contains(userId)) {
                                        filtered.add(userId);
                                    }
                                }
                                
                                Log.d(TAG, "Filtered out " + declinedUserIds.size() + " declined users from " + userIds.size() + " total");
                                callback.accept(filtered);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to check declined invitations, using cancelled entrants only", e);
                                // Filter based on cancelled entrants only
                                List<String> filtered = new java.util.ArrayList<>();
                                for (String userId : userIds) {
                                    if (!declinedUserIds.contains(userId)) {
                                        filtered.add(userId);
                                    }
                                }
                                callback.accept(filtered);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check cancelled entrants, sending to all", e);
                    // On error, send to all (better to send than miss)
                    callback.accept(userIds);
                });
    }
    
    /**
     * Filters user IDs based on their notification preferences.
     * For "selected" notifications: checks notificationPreferenceInvited
     * For "nonSelected" notifications: checks notificationPreferenceNotInvited
     * 
     * @param userIds List of user IDs to filter
     * @param groupType Type of notification (selected/invited or nonSelected/not invited)
     * @param callback Callback with filtered user IDs
     */
    private void filterUsersByPreferences(List<String> userIds, String groupType, java.util.function.Consumer<List<String>> callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.accept(new ArrayList<>());
            return;
        }
        
        // Determine which preference field to check based on group type
        // "selected" = invited notifications (notificationPreferenceInvited)
        // "nonSelected" = not invited notifications (notificationPreferenceNotInvited)
        boolean checkInvitedPreference = groupType.equals("selected") || groupType.equals("selection");
        
        List<String> filteredUserIds = new ArrayList<>();
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        
        // Fetch all user documents
        for (String userId : userIds) {
            tasks.add(db.collection("users").document(userId).get());
        }
        
        // Wait for all tasks to complete
        Tasks.whenAllComplete(tasks).addOnSuccessListener(tasksList -> {
            for (int i = 0; i < tasksList.size() && i < userIds.size(); i++) {
                Task<?> task = tasksList.get(i);
                String userId = userIds.get(i);
                
                if (task.isSuccessful()) {
                    Object result = task.getResult();
                    if (result instanceof DocumentSnapshot) {
                        DocumentSnapshot userDoc = (DocumentSnapshot) result;
                        
                        if (userDoc != null && userDoc.exists()) {
                            // Check the appropriate preference field
                            Boolean preferenceValue;
                            if (checkInvitedPreference) {
                                preferenceValue = userDoc.getBoolean("notificationPreferenceInvited");
                            } else {
                                preferenceValue = userDoc.getBoolean("notificationPreferenceNotInvited");
                            }
                            
                            // Default to true (enabled) if preference not set
                            boolean isEnabled = preferenceValue != null ? preferenceValue : true;
                            
                            if (isEnabled) {
                                filteredUserIds.add(userId);
                                Log.d(TAG, "User " + userId + " has " + 
                                    (checkInvitedPreference ? "invited" : "not invited") + 
                                    " notifications enabled");
                            } else {
                                Log.d(TAG, "User " + userId + " has " + 
                                    (checkInvitedPreference ? "invited" : "not invited") + 
                                    " notifications disabled - skipping");
                            }
                        } else {
                            // User document doesn't exist or no preference set - default to enabled
                            filteredUserIds.add(userId);
                            Log.d(TAG, "User " + userId + " has no preference set - defaulting to enabled");
                        }
                    }
                } else {
                    // Failed to fetch user doc - default to enabled (don't block notifications)
                    filteredUserIds.add(userId);
                    Log.w(TAG, "Failed to fetch preferences for user " + userId + " - defaulting to enabled");
                }
            }
            
            Log.d(TAG, "Filtered " + userIds.size() + " users to " + filteredUserIds.size() + 
                " based on " + (checkInvitedPreference ? "invited" : "not invited") + " notification preferences");
            callback.accept(filteredUserIds);
        });
    }
    
    /**
     * Sends notifications to filtered user list.
     */
    private void sendNotificationsToFilteredUsers(List<String> userIds, String title, String message,
                                                 String eventId, String eventTitle, String organizerId,
                                                 NotificationCallback callback) {
        // Check for duplicates for ALL notification types to prevent duplicate notifications
        // Note: Selection notifications are now handled by Cloud Function, but we still check
        // for duplicates here in case manual notifications are sent
        
        // Check for recent duplicate notification requests (within last 5 minutes)
        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
        db.collection("notificationRequests")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("title", title)
                .whereGreaterThan("createdAt", fiveMinutesAgo)
                .limit(1)
                .get()
                .addOnSuccessListener(existingSnapshot -> {
                    if (existingSnapshot != null && !existingSnapshot.isEmpty()) {
                        Log.w(TAG, "Duplicate notification request found for event " + eventId + " with title '" + title + "', skipping");
                        if (callback != null) {
                            callback.onComplete(0);
                        }
                        return;
                    }
                    
                    // No duplicate found, create notification request
                    createNotificationRequest(userIds, title, message, eventId, eventTitle, organizerId, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check for duplicate notification requests, creating anyway", e);
                    // On error, create the request (better to send than miss)
                    createNotificationRequest(userIds, title, message, eventId, eventTitle, organizerId, callback);
                });
    }
    
    /**
     * Creates a notification request in Firestore.
     */
    private void createNotificationRequest(List<String> userIds, String title, String message,
                                          String eventId, String eventTitle, String organizerId,
                                          NotificationCallback callback) {
        // Create notification request in Firestore
        // Cloud Functions will pick this up and send FCM notifications
        
        // Determine groupType from title
        String groupType = "general";
        if (title != null) {
            String titleLower = title.toLowerCase();
            if (titleLower.contains("replacement")) {
                groupType = "replacement";
            } else if (titleLower.contains("selected") || titleLower.contains("chosen")) {
                groupType = "selection";
            } else if (titleLower.contains("deadline") || titleLower.contains("missed")) {
                groupType = "deadline";
            } else if (titleLower.contains("sorry") || titleLower.contains("not selected")) {
                groupType = "sorry";
            }
        }
        
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("eventId", eventId);
        notificationRequest.put("eventTitle", eventTitle != null ? eventTitle : "Event");
        notificationRequest.put("organizerId", organizerId);
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("groupType", groupType);
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

