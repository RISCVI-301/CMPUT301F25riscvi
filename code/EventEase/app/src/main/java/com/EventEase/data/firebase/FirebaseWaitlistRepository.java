package com.EventEase.data.firebase;

import android.util.Log;

import com.EventEase.data.WaitlistRepository;
import com.EventEase.model.WaitlistEntry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
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
        
        // First, check if user is already admitted
        return db.collection("events")
                .document(eventId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        Log.e(TAG, "Event " + eventId + " not found");
                        return Tasks.forException(new Exception("Event not found"));
                    }
                    
                    DocumentSnapshot eventDoc = task.getResult();
                    
                    // Check if already admitted
                    @SuppressWarnings("unchecked")
                    List<String> admitted = (List<String>) eventDoc.get("admitted");
                    if (admitted != null && admitted.contains(uid)) {
                        Log.d(TAG, "User " + uid + " is already admitted to event " + eventId + ". Cannot join waitlist.");
                        return Tasks.forException(new Exception("User is already admitted to this event"));
                    }
                    
                    // Check if already in waitlist
                    @SuppressWarnings("unchecked")
                    List<String> waitlist = (List<String>) eventDoc.get("waitlist");
                    if (waitlist != null && waitlist.contains(uid)) {
                        Log.d(TAG, "User " + uid + " is already in waitlist for event " + eventId);
                        return Tasks.forResult(null);
                    }
                    
                    // Get capacity and admitted count to check if event is full
                    Object capacityObj = eventDoc.get("capacity");
                    int capacity = capacityObj != null ? ((Number) capacityObj).intValue() : 0;
                    int admittedCount = admitted != null ? admitted.size() : 0;
                    
                    // If capacity is set and event is full, allow joining waitlist
                    // (Capacity check is only enforced when admitting, not when joining waitlist)
                    // Users can join waitlist even if event is full
                    
                    // Add UID to event's waitlist array using arrayUnion
                    return db.collection("events")
                            .document(eventId)
                            .update("waitlist", FieldValue.arrayUnion(uid))
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ SUCCESS: User " + uid + " added to waitlist for event " + eventId + " (Capacity: " + capacity + ", Admitted: " + admittedCount + ")");
                                membership.add(key(eventId, uid));
                                eventRepo.incrementWaitlist(eventId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ FAILED to add user " + uid + " to waitlist for event " + eventId, e);
                            })
                            .continueWith(t -> null);
                });
    }

    @Override
    public Task<Boolean> isJoined(String eventId, String uid) {
        // Check in-memory cache first
        if (membership.contains(key(eventId, uid))) {
            return Tasks.forResult(true);
        }
        
        // Query Firestore - get event document and check if waitlist array contains uid
        return db.collection("events")
                .document(eventId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot doc = task.getResult();
                        if (doc.exists()) {
                            @SuppressWarnings("unchecked")
                            List<String> waitlist = (List<String>) doc.get("waitlist");
                            boolean exists = waitlist != null && waitlist.contains(uid);
                            if (exists) {
                                membership.add(key(eventId, uid));
                            }
                            return exists;
                        }
                    }
                    return false;
                });
    }

    @Override
    public Task<Void> leave(String eventId, String uid) {
        Log.d(TAG, "Attempting to remove user " + uid + " from waitlist for event " + eventId);
        
        // Remove UID from event's waitlist array using arrayRemove
        return db.collection("events")
                .document(eventId)
                .update("waitlist", FieldValue.arrayRemove(uid))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ SUCCESS: User " + uid + " removed from waitlist for event " + eventId);
                    membership.remove(key(eventId, uid));
                    eventRepo.decrementWaitlist(eventId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ FAILED to remove user " + uid + " from waitlist for event " + eventId, e);
                })
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Task completed successfully for leave operation");
                    } else {
                        Log.e(TAG, "Task failed for leave operation", task.getException());
                    }
                    return null;
                });
    }
}
