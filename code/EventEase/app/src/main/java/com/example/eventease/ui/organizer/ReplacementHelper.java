package com.example.eventease.ui.organizer;

import android.util.Log;

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
 * Helper class for automatically replacing cancelled entrants with waitlisted entrants.
 */
public class ReplacementHelper {
    private static final String TAG = "ReplacementHelper";
    private final FirebaseFirestore db;

    public ReplacementHelper() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Automatically replaces cancelled entrants with waitlisted entrants.
     * This is called when someone declines an invitation or when the View Entrants screen loads.
     *
     * @param eventId The event ID
     * @param eventTitle The event title (for notifications)
     */
    public void autoReplaceCancelledEntrants(String eventId, String eventTitle) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        // Check if we're still before the deadline to accept/decline
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        return;
                    }

                    Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
                    long currentTime = System.currentTimeMillis();
                    
                    // Only auto-replace if we're still before the deadline
                    if (deadlineEpochMs != null && currentTime >= deadlineEpochMs) {
                        Log.d(TAG, "Deadline has passed, skipping auto-replacement");
                        return;
                    }

                    // Count cancelled and waitlisted entrants
                    db.collection("events").document(eventId).collection("CancelledEntrants").get()
                            .addOnSuccessListener(cancelledSnapshot -> {
                                int cancelledCount = cancelledSnapshot != null ? cancelledSnapshot.size() : 0;

                                if (cancelledCount == 0) {
                                    return; // No cancellations, nothing to replace
                                }

                                db.collection("events").document(eventId).collection("WaitlistedEntrants").get()
                                        .addOnSuccessListener(waitlistSnapshot -> {
                                            int waitlistCount = waitlistSnapshot != null ? waitlistSnapshot.size() : 0;

                                            if (waitlistCount == 0) {
                                                Log.d(TAG, "No waitlisted entrants available for replacement");
                                                return; // No waitlisted entrants available
                                            }

                                            // Auto-replace: move from waitlisted to selected
                                            int toReplace = Math.min(cancelledCount, waitlistCount);
                                            Log.d(TAG, "Auto-replacing " + toReplace + " cancelled entrant(s)");
                                            performReplacement(eventId, eventTitle, toReplace);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to check waitlisted entrants for auto-replace", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to check cancelled entrants for auto-replace", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for auto-replace check", e);
                });
    }

    private void performReplacement(String eventId, String eventTitle, int count) {
        // Get event details for deadline calculation
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    // Use correct Firestore field names
                    Long eventDeadline = eventDoc != null ? eventDoc.getLong("deadlineEpochMs") : null;
                    Long eventStart = eventDoc != null ? eventDoc.getLong("eventStart") : null;
                    
                    // Calculate deadline for replacement invitations
                    long currentTime = System.currentTimeMillis();
                    long calculatedDeadline = currentTime + (7L * 24 * 60 * 60 * 1000); // 7 days from now
                    
                    if (eventDeadline != null) {
                        calculatedDeadline = Math.min(calculatedDeadline, eventDeadline);
                    } else if (eventStart != null) {
                        calculatedDeadline = Math.min(calculatedDeadline, eventStart);
                    }
                    
                    // Ensure minimum 2 days
                    long minDeadline = currentTime + (2L * 24 * 60 * 60 * 1000);
                    calculatedDeadline = Math.max(calculatedDeadline, minDeadline);
                    
                    final long deadlineToAccept = calculatedDeadline;

                    // Fetch waitlisted entrants
                    db.collection("events").document(eventId).collection("WaitlistedEntrants").get()
                            .addOnSuccessListener(waitlistSnapshot -> {
                                if (waitlistSnapshot == null || waitlistSnapshot.isEmpty()) {
                                    Log.d(TAG, "No waitlisted entrants available");
                                    return;
                                }

                                List<DocumentSnapshot> waitlistDocs = waitlistSnapshot.getDocuments();
                                if (waitlistDocs.size() < count) {
                                    Log.d(TAG, "Not enough waitlisted entrants available");
                                    return;
                                }

                                // Randomly select entrants
                                List<DocumentSnapshot> selectedForReplacement = randomlySelect(waitlistDocs, count);
                                
                                DocumentReference eventRef = db.collection("events").document(eventId);
                                WriteBatch batch = db.batch();
                                List<String> userIds = new ArrayList<>();

                                // Move selected entrants from WaitlistedEntrants to SelectedEntrants
                                for (DocumentSnapshot doc : selectedForReplacement) {
                                    String userId = doc.getId();
                                    Map<String, Object> data = doc.getData();
                                    userIds.add(userId);

                                    if (data != null) {
                                        // Move to SelectedEntrants
                                        batch.set(eventRef.collection("SelectedEntrants").document(userId), data);
                                        // Remove from WaitlistedEntrants
                                        batch.delete(eventRef.collection("WaitlistedEntrants").document(userId));
                                        
                                        // Create invitation for replacement
                                        // Use same field names as InvitationHelper for consistency
                                        String invitationId = UUID.randomUUID().toString();
                                        Map<String, Object> invitation = new HashMap<>();
                                        invitation.put("id", invitationId);
                                        invitation.put("eventId", eventId);
                                        invitation.put("uid", userId); // Use 'uid' to match InvitationHelper
                                        invitation.put("entrantId", userId); // Add for compatibility
                                        invitation.put("status", "PENDING");
                                        invitation.put("issuedAt", System.currentTimeMillis()); // Use 'issuedAt' to match InvitationHelper
                                        invitation.put("expiresAt", deadlineToAccept);
                                        invitation.put("isReplacement", true);
                                        
                                        // Get organizer ID for consistency
                                        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
                                        com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
                                        if (currentUser != null) {
                                            invitation.put("organizerId", currentUser.getUid());
                                        }
                                        
                                        batch.set(db.collection("invitations").document(invitationId), invitation);
                                    }
                                }

                                batch.commit()
                                        .addOnSuccessListener(v -> {
                                            Log.d(TAG, "Successfully auto-replaced " + count + " entrant(s)");
                                            
                                            // Send notifications
                                            String eventTitleStr = eventTitle != null ? eventTitle : "the event";
                                            sendReplacementNotifications(eventId, userIds, eventTitleStr, deadlineToAccept);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to perform auto-replacement", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load waitlisted entrants for replacement", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for replacement", e);
                });
    }

    private List<DocumentSnapshot> randomlySelect(List<DocumentSnapshot> allDocs, int count) {
        List<DocumentSnapshot> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private void sendReplacementNotifications(String eventId, List<String> userIds, String eventTitle, long deadlineMs) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault());
        String deadlineStr = sdf.format(new java.util.Date(deadlineMs));
        String message = String.format(
                "You've been selected as a replacement for \"%s\"! Please accept or decline by %s",
                eventTitle, deadlineStr
        );

        // Use NotificationHelper for push notifications (works when app is closed)
        NotificationHelper notificationHelper = new NotificationHelper();
        notificationHelper.sendNotificationsToUsers(
                userIds,
                "Replacement Invitation",
                message,
                eventId,
                eventTitle,
                new NotificationHelper.NotificationCallback() {
                    @Override
                    public void onComplete(int sentCount) {
                        Log.d(TAG, "Successfully sent " + sentCount + " replacement push notifications");
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to send replacement notifications: " + error);
                    }
                }
        );
    }
}

