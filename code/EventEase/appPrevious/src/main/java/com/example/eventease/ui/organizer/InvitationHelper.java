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

/**
 * Helper class for sending invitations to selected entrants.
 * 
 * <p>This class provides functionality for organizers to send invitations to users who have been
 * selected for an event. Invitations are created in the Firestore 'invitations' collection and
 * include information about the event, organizer, and entrant.
 * 
 * <p>The invitation process:
 * <ol>
 *   <li>Retrieves all selected entrants from the event's SelectedEntrants subcollection</li>
 *   <li>Creates invitation documents in the invitations collection for each selected entrant</li>
 *   <li>Includes organizerId and entrantId in the invitation data for proper tracking</li>
 *   <li>Uses batch writes for efficient database operations</li>
 * </ol>
 * 
 * <p>Invitations are created with PENDING status and can be accepted or declined by entrants.
 * This class is used by OrganizerViewEntrantsActivity when organizers send invitations.
 */
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
        
        // Get organizerId from event document
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (!eventDoc.exists()) {
                if (callback != null) {
                    callback.onError("Event not found");
                }
                return;
            }
            
            String organizerId = eventDoc.getString("organizerId");
            if (organizerId == null || organizerId.isEmpty()) {
                if (callback != null) {
                    callback.onError("Event has no organizer ID");
                }
                return;
            }
            
            // Get selected entrants and create invitations
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
                        invitationData.put("entrantId", userId); // Add for compatibility
                        invitationData.put("organizerId", organizerId); // Add organizer ID
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
                                    
                                    // Notify listeners by refreshing the invitation repository
                                    // The FirebaseInvitationRepository will pick up the new invitations via its listener
                                    
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
                                                // Still report success since invitations were created
                                                if (callback != null) {
                                                    callback.onComplete(userIds.size());
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create invitations", e);
                                    String errorMsg = e.getMessage();
                                    if (errorMsg != null && errorMsg.contains("index")) {
                                        if (callback != null) {
                                            callback.onError("Firestore index required. Please check Firebase Console for index creation link.");
                                        }
                                    } else {
                                        if (callback != null) {
                                            callback.onError("Failed to create invitations: " + errorMsg);
                                        }
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
        }).addOnFailureListener(e -> {
            if (callback != null) {
                callback.onError("Failed to load event: " + e.getMessage());
            }
        });
    }
}

