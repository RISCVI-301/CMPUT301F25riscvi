package com.EventEase.data.firebase;

import android.util.Log;

import com.EventEase.data.AdmittedRepository;
import com.EventEase.data.EventRepository;
import com.EventEase.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        String docId = eventId + "_" + uid;
        
        Map<String, Object> admittedData = new HashMap<>();
        admittedData.put("eventId", eventId);
        admittedData.put("uid", uid);
        admittedData.put("admittedAt", System.currentTimeMillis());
        admittedData.put("acceptedAt", System.currentTimeMillis());
        
        Log.d(TAG, "Attempting to admit user " + uid + " to event " + eventId + " with docId: " + docId);
        
        return db.collection("admitted")
                .document(docId)
                .set(admittedData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ SUCCESS: User " + uid + " admitted to event " + eventId + " in Firebase");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ FAILED to admit user " + uid + " to event " + eventId, e);
                })
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Task completed successfully for admit operation");
                    } else {
                        Log.e(TAG, "Task failed for admit operation", task.getException());
                    }
                    return null;
                });
    }

    @Override
    public Task<Boolean> isAdmitted(String eventId, String uid) {
        String docId = eventId + "_" + uid;
        return db.collection("admitted")
                .document(docId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        return task.getResult().exists();
                    }
                    return false;
                });
    }

    @Override
    public Task<List<Event>> getUpcomingEvents(String uid) {
        return db.collection("admitted")
                .whereEqualTo("uid", uid)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String eventId = doc.getString("eventId");
                        if (eventId != null) {
                            eventIds.add(eventId);
                        }
                    }

                    if (eventIds.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    // Fetch all events
                    List<Task<DocumentSnapshot>> eventTasks = new ArrayList<>();
                    for (String eventId : eventIds) {
                        eventTasks.add(db.collection("events").document(eventId).get());
                    }

                    return Tasks.whenAllSuccess(eventTasks).continueWith(allTask -> {
                        List<Event> events = new ArrayList<>();
                        for (Object result : allTask.getResult()) {
                            if (result instanceof DocumentSnapshot) {
                                DocumentSnapshot snapshot = (DocumentSnapshot) result;
                                if (snapshot.exists()) {
                                    Event event = snapshot.toObject(Event.class);
                                    if (event != null) {
                                        if (event.getId() == null || event.getId().isEmpty()) {
                                            event.setId(snapshot.getId());
                                        }
                                        // Only include future events
                                        if (event.getStartsAtEpochMs() > System.currentTimeMillis()) {
                                            events.add(event);
                                        }
                                    }
                                }
                            }
                        }
                        return events;
                    });
                });
    }
}

