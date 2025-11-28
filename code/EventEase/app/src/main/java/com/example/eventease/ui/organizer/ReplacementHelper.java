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
     * AUTOMATIC REPLACEMENT IS DISABLED - Only manual replacement is allowed.
     * Organizer must use the "Replace Cancelled Entrants" button to manually select replacements.
     * 
     * This method is kept for backward compatibility but does nothing.
     *
     * @param eventId The event ID
     * @param eventTitle The event title (for notifications)
     */
    public void autoReplaceCancelledEntrants(String eventId, String eventTitle) {
        // AUTOMATIC REPLACEMENT DISABLED - Organizer must manually replace via button
        Log.d(TAG, "Auto-replacement is disabled. Organizer must manually replace cancelled entrants via the Replace button.");
        return;
    }

    private void performReplacement(String eventId, String eventTitle, int count) {
        // Get event details for deadline calculation and sample size
        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        Log.e(TAG, "Event not found: " + eventId);
                        return;
                    }
                    
                    // Get sample size
                    int sampleSize = 0;
                    Long sampleSizeObj = eventDoc.getLong("sampleSize");
                    if (sampleSizeObj != null) {
                        sampleSize = sampleSizeObj.intValue();
                    }
                    final int finalSampleSize = sampleSize;
                    
                    // Use correct Firestore field names for deadline
                    Long eventDeadline = eventDoc.getLong("deadlineEpochMs");
                    Long eventStart = eventDoc.getLong("eventStart");
                    
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

                    // CRITICAL: Check current selected count before proceeding
                    eventRef.collection("SelectedEntrants").get()
                                .addOnSuccessListener(selectedSnapshot -> {
                                    int currentSelectedCount = selectedSnapshot != null ? selectedSnapshot.size() : 0;
                                    int availableSpots = finalSampleSize > 0 ? (finalSampleSize - currentSelectedCount) : count;
                                    
                                    Log.d(TAG, "Replacement check: currentSelected=" + currentSelectedCount + ", sampleSize=" + finalSampleSize + ", availableSpots=" + availableSpots + ", requested=" + count);
                                    
                                    if (finalSampleSize > 0 && availableSpots <= 0) {
                                        Log.w(TAG, "Cannot replace: Already at sample size limit (" + currentSelectedCount + "/" + finalSampleSize + ")");
                                        return;
                                    }
                                    
                                    int actualCount = finalSampleSize > 0 ? Math.min(availableSpots, count) : count;

                    // Fetch waitlisted entrants
                                    eventRef.collection("WaitlistedEntrants").get()
                            .addOnSuccessListener(waitlistSnapshot -> {
                                if (waitlistSnapshot == null || waitlistSnapshot.isEmpty()) {
                                    Log.d(TAG, "No waitlisted entrants available");
                                    return;
                                }

                                List<DocumentSnapshot> waitlistDocs = waitlistSnapshot.getDocuments();
                                                if (waitlistDocs.size() < actualCount) {
                                                    Log.d(TAG, "Not enough waitlisted entrants available (need " + actualCount + ", have " + waitlistDocs.size() + ")");
                                    return;
                                }

                                                // Randomly select entrants (limited to availableSpots)
                                                List<DocumentSnapshot> selectedForReplacement = randomlySelect(waitlistDocs, actualCount);
                                
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
                                        
                                        // Get organizer ID from event document (should already be there)
                                        
                                        batch.set(db.collection("invitations").document(invitationId), invitation);
                                    }
                                }

                                                // CRITICAL: Final verification before commit
                                                int finalTotal = currentSelectedCount + selectedForReplacement.size();
                                                if (finalSampleSize > 0 && finalTotal > finalSampleSize) {
                                                    Log.e(TAG, "CRITICAL: Final count (" + finalTotal + ") would exceed sampleSize (" + finalSampleSize + ")! Truncating.");
                                                    int maxToAdd = finalSampleSize - currentSelectedCount;
                                                    if (maxToAdd > 0) {
                                                        selectedForReplacement = selectedForReplacement.subList(0, maxToAdd);
                                                        userIds.clear();
                                                        for (DocumentSnapshot doc : selectedForReplacement) {
                                                            userIds.add(doc.getId());
                                                        }
                                                    } else {
                                                        Log.e(TAG, "Cannot add any more - already at sample size!");
                                                        return;
                                    }
                                }

                                batch.commit()
                                        .addOnSuccessListener(v -> {
                                                            Log.d(TAG, "Successfully auto-replaced " + userIds.size() + " entrant(s) (sampleSize: " + finalSampleSize + ", total selected: " + (currentSelectedCount + userIds.size()) + ")");
                                            
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
                                        Log.e(TAG, "Failed to load selected entrants count", e);
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

