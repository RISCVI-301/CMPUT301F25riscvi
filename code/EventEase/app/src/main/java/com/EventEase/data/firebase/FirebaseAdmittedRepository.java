package com.EventEase.data.firebase;

import android.util.Log;

import com.EventEase.data.AdmittedRepository;
import com.EventEase.data.EventRepository;
import com.EventEase.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase implementation of AdmittedRepository.
 * Manages admitted users and retrieves upcoming events from Firestore.
 */
public class FirebaseAdmittedRepository implements AdmittedRepository {

    private static final String TAG = "AdmittedRepository";
    private final FirebaseFirestore db;
    private final EventRepository eventRepo;

    public FirebaseAdmittedRepository(EventRepository eventRepo) {
        this.db = FirebaseFirestore.getInstance();
        this.eventRepo = eventRepo;
    }

    @Override
    public Task<Void> admit(String eventId, String uid) {
        Log.d(TAG, "Attempting to admit user " + uid + " to event " + eventId);
        
        // First, check capacity and remove from waitlist if present, then add to admitted array
        return db.collection("events")
                .document(eventId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        Log.e(TAG, "Event " + eventId + " not found");
                        return Tasks.forException(new Exception("Event not found"));
                    }
                    
                    DocumentSnapshot eventDoc = task.getResult();
                    
                    // Get event capacity
                    Object capacityObj = eventDoc.get("capacity");
                    int capacity = capacityObj != null ? ((Number) capacityObj).intValue() : 0;
                    
                    // Get current admitted list
                    @SuppressWarnings("unchecked")
                    List<String> admitted = (List<String>) eventDoc.get("admitted");
                    int currentAdmittedCount = admitted != null ? admitted.size() : 0;
                    
                    // Check if already admitted
                    if (admitted != null && admitted.contains(uid)) {
                        Log.d(TAG, "User " + uid + " is already admitted to event " + eventId);
                        return Tasks.forResult(null);
                    }
                    
                    // Check capacity (only enforce if capacity > 0)
                    if (capacity > 0 && currentAdmittedCount >= capacity) {
                        Log.e(TAG, "❌ Event " + eventId + " is at full capacity (" + capacity + "). Cannot admit user " + uid);
                        return Tasks.forException(new Exception("Event is at full capacity"));
                    }
                    
                    Map<String, Object> updates = new HashMap<>();
                    
                    // Remove from waitlist if present (using arrayRemove)
                    updates.put("waitlist", FieldValue.arrayRemove(uid));
                    
                    // Add to admitted array (using arrayUnion)
                    updates.put("admitted", FieldValue.arrayUnion(uid));
                    
                    return db.collection("events")
                            .document(eventId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ SUCCESS: User " + uid + " admitted to event " + eventId + " (" + (currentAdmittedCount + 1) + "/" + capacity + ")");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ FAILED to admit user " + uid + " to event " + eventId, e);
                            })
                            .continueWith(t -> null);
                });
    }

    @Override
    public Task<Boolean> isAdmitted(String eventId, String uid) {
        // Get event document and check if admitted array contains uid
        return db.collection("events")
                .document(eventId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot doc = task.getResult();
                        if (doc.exists()) {
                            @SuppressWarnings("unchecked")
                            List<String> admitted = (List<String>) doc.get("admitted");
                            return admitted != null && admitted.contains(uid);
                        }
                    }
                    return false;
                });
    }

    @Override
    public Task<List<Event>> getUpcomingEvents(String uid) {
        // Query events where admitted array contains uid
        return db.collection("events")
                .whereArrayContains("admitted", uid)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return new ArrayList<>();
                    }

                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        try {
                            if (doc.exists()) {
                                Event event = Event.fromMap(doc.getData());
                                if (event != null) {
                                    if (event.getId() == null || event.getId().isEmpty()) {
                                        event.setId(doc.getId());
                                    }
                                    // Only include future events
                                    if (event.getStartsAtEpochMs() > System.currentTimeMillis()) {
                                        events.add(event);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing event", e);
                        }
                    }
                    return events;
                });
    }
}

