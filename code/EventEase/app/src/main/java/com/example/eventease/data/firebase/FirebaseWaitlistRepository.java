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

        WriteBatch batch = db.batch();
        batch.delete(waitlistDoc);
        batch.update(eventRef, "waitlistCount", FieldValue.increment(-1));

        return batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "SUCCESS: User " + uid + " removed from waitlist for event " + eventId);
                    membership.remove(key(eventId, uid));
                    eventRepo.decrementWaitlist(eventId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "FAILED to remove user " + uid + " from waitlist for event " + eventId, e))
                .continueWith(task -> null);
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
