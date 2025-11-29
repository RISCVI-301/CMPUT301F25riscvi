package com.example.eventease.data.firebase;

import android.util.Log;

import com.example.eventease.data.WaitlistRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Firebase implementation of WaitlistRepository.
 * Uses Firestore for persistent waitlist storage and management.
 */
public class FirebaseWaitlistRepository implements WaitlistRepository {

    private static final String TAG = "WaitlistRepository";
    private final Set<String> membership = ConcurrentHashMap.newKeySet();
    private final FirebaseEventRepository eventRepo;
    private final FirebaseFirestore db;

    public FirebaseWaitlistRepository(FirebaseEventRepository eventRepo) {
        this.eventRepo = eventRepo;
        this.db = FirebaseFirestore.getInstance();
    }

    private static String key(String eventId, String uid) {
        return eventId + "||" + uid;
    }

    @Override
    public Task<Void> join(String eventId, String uid) {
        Log.d(TAG, "Attempting to add user " + uid + " to waitlist for event " + eventId);

        DocumentReference eventRef = db.collection("events").document(eventId);

        return eventRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                Log.e(TAG, "Event " + eventId + " not found");
                return Tasks.forException(new Exception("Event not found"));
            }

            DocumentSnapshot eventDoc = task.getResult();

            @SuppressWarnings("unchecked")
            List<String> admitted = (List<String>) eventDoc.get("admitted");
            if (admitted != null && admitted.contains(uid)) {
                Log.d(TAG, "User " + uid + " is already admitted to event " + eventId);
                return Tasks.forException(new Exception("User is already admitted to this event"));
            }

            // Check registration period
            Long registrationStart = eventDoc.getLong("registrationStart");
            Long registrationEnd = eventDoc.getLong("registrationEnd");
            long currentTime = System.currentTimeMillis();
            
            if (registrationStart != null && registrationStart > 0 && currentTime < registrationStart) {
                Log.d(TAG, "Registration period has not started yet for event " + eventId);
                return Tasks.forException(new Exception("Registration period has not started yet"));
            }
            
            if (registrationEnd != null && registrationEnd > 0 && currentTime > registrationEnd) {
                Log.d(TAG, "Registration period has ended for event " + eventId);
                return Tasks.forException(new Exception("Registration period has ended"));
            }

            // Check capacity
            Long capacityLong = eventDoc.getLong("capacity");
            int capacity = capacityLong != null ? capacityLong.intValue() : 0;
            if (capacity > 0) {
                // FIX: Always check the actual subcollection count to ensure accuracy
                // The stored waitlistCount field can be stale, especially after deadline changes
                return eventRef.collection("WaitlistedEntrants").get().continueWithTask(countTask -> {
                    int actualCount = countTask.isSuccessful() && countTask.getResult() != null ? 
                        countTask.getResult().size() : 0;
                    
                    Log.d(TAG, "Capacity check for event " + eventId + ": capacity=" + capacity + ", actualWaitlistCount=" + actualCount);
                    
                    if (actualCount >= capacity) {
                        Log.d(TAG, "Waitlist capacity reached for event " + eventId + " (capacity: " + capacity + ", actual: " + actualCount + ")");
                        return Tasks.forException(new Exception("Waitlist is full. Capacity reached."));
                    }
                    
                    // Also update the waitlistCount field to keep it in sync
                    if (actualCount >= 0) {
                        eventRef.update("waitlistCount", actualCount)
                                .addOnFailureListener(e -> Log.w(TAG, "Failed to sync waitlistCount field", e));
                    }
                    
                    return proceedWithJoin(eventRef, eventId, uid);
                });
            }

            return proceedWithJoin(eventRef, eventId, uid);
        });
    }
    
    private Task<Void> proceedWithJoin(DocumentReference eventRef, String eventId, String uid) {
        DocumentReference waitlistDoc = eventRef.collection("WaitlistedEntrants").document(uid);
        return waitlistDoc.get().continueWithTask(waitlistTask -> {
            if (waitlistTask.isSuccessful() && waitlistTask.getResult() != null && waitlistTask.getResult().exists()) {
                Log.d(TAG, "User " + uid + " already has a waitlist entry for event " + eventId);
                membership.add(key(eventId, uid));
                return Tasks.forResult(null);
            }

            return db.collection("users").document(uid).get().continueWithTask(userTask -> {
                DocumentSnapshot userDoc = userTask.isSuccessful() ? userTask.getResult() : null;
                Map<String, Object> payload = buildWaitlistEntry(uid, userDoc);

                WriteBatch batch = db.batch();
                batch.set(waitlistDoc, payload, SetOptions.merge());
                batch.update(eventRef, "waitlistCount", FieldValue.increment(1));

                return batch.commit()
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "SUCCESS: User " + uid + " added to waitlist subcollection for event " + eventId);
                            membership.add(key(eventId, uid));
                            eventRepo.incrementWaitlist(eventId);
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "FAILED to add user " + uid + " to waitlist for event " + eventId, e))
                        .continueWith(t -> null);
            });
        });
    }

    @Override
    public Task<Boolean> isJoined(String eventId, String uid) {
        // Check in-memory cache first
        if (membership.contains(key(eventId, uid))) {
            return Tasks.forResult(true);
        }
        
        // Query Firestore - check waitlist subcollection document
        return db.collection("events")
                .document(eventId)
                .collection("WaitlistedEntrants")
                .document(uid)
                .get()
                .continueWith(task -> {
                    boolean exists = task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                    if (exists) {
                        membership.add(key(eventId, uid));
                    }
                    return exists;
                });
    }

    @Override
    public Task<Void> leave(String eventId, String uid) {
        Log.d(TAG, "Attempting to remove user " + uid + " from waitlist for event " + eventId);
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference waitlistDoc = eventRef.collection("WaitlistedEntrants").document(uid);

        // Get event details for notification
        return eventRef.get().continueWithTask(eventTask -> {
            String eventTitle = null;
            Long capacity = null;
            Long waitlistCount = null;
            
            if (eventTask.isSuccessful() && eventTask.getResult() != null && eventTask.getResult().exists()) {
                DocumentSnapshot eventDoc = eventTask.getResult();
                eventTitle = eventDoc.getString("title");
                capacity = eventDoc.getLong("capacity");
                waitlistCount = eventDoc.getLong("waitlistCount");
            }
            
            return proceedWithLeave(eventRef, waitlistDoc, eventId, uid, eventTitle, capacity, waitlistCount);
        });
    }
    
    private Task<Void> proceedWithLeave(DocumentReference eventRef, DocumentReference waitlistDoc, 
                                       String eventId, String uid, String eventTitle, 
                                       Long capacity, Long waitlistCount) {
        WriteBatch batch = db.batch();
        batch.delete(waitlistDoc);
        batch.update(eventRef, "waitlistCount", FieldValue.increment(-1));

        return batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SUCCESS: User " + uid + " removed from waitlist for event " + eventId);
                    membership.remove(key(eventId, uid));
                    eventRepo.decrementWaitlist(eventId);
                    
                    // Send notification to other waitlisted users if capacity was full
                    if (eventTitle != null && capacity != null && capacity > 0 && 
                        waitlistCount != null && waitlistCount >= capacity) {
                        // Capacity was full, now there's a spot available - notify others
                        sendWaitlistSpotAvailableNotification(eventId, eventTitle, uid);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "FAILED to remove user " + uid + " from waitlist for event " + eventId, e))
                .continueWith(task -> null);
    }
    
    /**
     * Sends notification to waitlisted users when a spot becomes available.
     */
    private void sendWaitlistSpotAvailableNotification(String eventId, String eventTitle, String leavingUserId) {
        Log.d(TAG, "Sending waitlist spot available notification for event " + eventId);
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        
        eventRef.collection("WaitlistedEntrants").get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }
                    
                    List<String> userIds = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        String userId = doc.getId();
                        // Don't notify the user who just left
                        if (!userId.equals(leavingUserId)) {
                            userIds.add(userId);
                        }
                    }
                    
                    if (userIds.isEmpty()) {
                        return;
                    }
                    
                    String notificationTitle = "Waitlist Update: " + eventTitle;
                    String notificationMessage = "A spot has become available on the waitlist for " + 
                        eventTitle + ". Check the event details if you're interested!";
                    
                    com.example.eventease.ui.organizer.NotificationHelper notificationHelper = 
                        new com.example.eventease.ui.organizer.NotificationHelper();
                    
                    notificationHelper.sendNotificationsToUsers(userIds, notificationTitle, notificationMessage,
                            eventId, eventTitle,
                            new com.example.eventease.ui.organizer.NotificationHelper.NotificationCallback() {
                                @Override
                                public void onComplete(int sentCount) {
                                    Log.d(TAG, "Sent waitlist spot available notification to " + sentCount + " users");
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Failed to send waitlist spot available notification: " + error);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load waitlisted entrants for notification", e);
                });
    }

    private Map<String, Object> buildWaitlistEntry(String uid, DocumentSnapshot userDoc) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("joinedAt", System.currentTimeMillis());

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

            putIfString(data, "displayName", displayName);
            putIfString(data, "fullName", userDoc.getString("fullName"));
            putIfString(data, "name", userDoc.getString("name"));
            putIfString(data, "firstName", userDoc.getString("firstName"));
            putIfString(data, "lastName", userDoc.getString("lastName"));
            putIfString(data, "email", userDoc.getString("email"));
            putIfString(data, "phoneNumber", userDoc.getString("phoneNumber"));
            putIfString(data, "photoUrl", userDoc.getString("photoUrl"));
        }

        return data;
    }

    private void putIfString(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value);
        }
    }
}
