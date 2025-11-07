package com.example.eventease.data.firebase;

import android.util.Log;

import com.example.eventease.data.AdmittedRepository;
import com.example.eventease.data.EventRepository;
import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase implementation of AdmittedRepository.
 * Manages admitted users using AdmittedEntrants subcollection and retrieves upcoming events from Firestore.
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
        
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference admittedDoc = eventRef.collection("AdmittedEntrants").document(uid);
        DocumentReference waitlistDoc = eventRef.collection("WaitlistedEntrants").document(uid);
        DocumentReference selectedDoc = eventRef.collection("SelectedEntrants").document(uid);
        
        return admittedDoc.get().continueWithTask(admittedTask -> {
            if (admittedTask.isSuccessful() && admittedTask.getResult() != null && admittedTask.getResult().exists()) {
                Log.d(TAG, "User " + uid + " is already admitted to event " + eventId);
                return Tasks.forResult(null);
            }
            
            return eventRef.get().continueWithTask(eventTask -> {
                if (!eventTask.isSuccessful() || eventTask.getResult() == null || !eventTask.getResult().exists()) {
                    Log.e(TAG, "Event " + eventId + " not found");
                    return Tasks.forException(new Exception("Event not found"));
                }
                
                DocumentSnapshot eventDoc = eventTask.getResult();
                
                Object capacityObj = eventDoc.get("capacity");
                int capacity = capacityObj != null ? ((Number) capacityObj).intValue() : -1;
                
                if (capacity > 0) {
                    return eventRef.collection("AdmittedEntrants").get().continueWithTask(admittedCountTask -> {
                        if (admittedCountTask.isSuccessful() && admittedCountTask.getResult() != null) {
                            int currentAdmittedCount = admittedCountTask.getResult().size();
                            if (currentAdmittedCount >= capacity) {
                                Log.e(TAG, "Event " + eventId + " is at full capacity (" + capacity + "). Cannot admit user " + uid);
                                return Tasks.forException(new Exception("Event is at full capacity"));
                            }
                        }
                        
                        return moveToAdmitted(eventRef, admittedDoc, waitlistDoc, selectedDoc, uid);
                    });
                } else {
                    return moveToAdmitted(eventRef, admittedDoc, waitlistDoc, selectedDoc, uid);
                }
            });
        });
    }
    
    private Task<Void> moveToAdmitted(DocumentReference eventRef, DocumentReference admittedDoc, 
                                      DocumentReference waitlistDoc, DocumentReference selectedDoc, String uid) {
        return db.collection("users").document(uid).get().continueWithTask(userTask -> {
            DocumentSnapshot userDoc = userTask.isSuccessful() ? userTask.getResult() : null;
            Map<String, Object> admittedData = buildAdmittedEntry(uid, userDoc);
            
            WriteBatch batch = db.batch();
            
            batch.set(admittedDoc, admittedData, SetOptions.merge());
            batch.delete(waitlistDoc);
            batch.delete(selectedDoc);
            
            return batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SUCCESS: User " + uid + " admitted to event, moved to AdmittedEntrants");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "FAILED to admit user " + uid + " to event", e);
                    })
                    .continueWith(t -> null);
        });
    }
    
    private Map<String, Object> buildAdmittedEntry(String uid, DocumentSnapshot userDoc) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("admittedAt", System.currentTimeMillis());

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

    @Override
    public Task<Boolean> isAdmitted(String eventId, String uid) {
        return db.collection("events")
                .document(eventId)
                .collection("AdmittedEntrants")
                .document(uid)
                .get()
                .continueWith(task -> {
                    return task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                });
    }

    @Override
    public Task<List<Event>> getUpcomingEvents(String uid) {
        Query admittedQuery = db.collectionGroup("AdmittedEntrants").whereEqualTo("userId", uid);
        return admittedQuery.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            List<Task<DocumentSnapshot>> eventTasks = new ArrayList<>();
            for (QueryDocumentSnapshot doc : task.getResult()) {
                DocumentReference admittedRef = doc.getReference();
                DocumentReference eventRef = admittedRef.getParent().getParent();
                if (eventRef != null) {
                    eventTasks.add(eventRef.get());
                }
            }
            
            if (eventTasks.isEmpty()) {
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            return Tasks.whenAllComplete(eventTasks).continueWith(allTasks -> {
                List<Event> events = new ArrayList<>();
                long currentTime = System.currentTimeMillis();
                
                for (com.google.android.gms.tasks.Task<?> eventTask : allTasks.getResult()) {
                    if (eventTask.isSuccessful() && eventTask.getResult() instanceof DocumentSnapshot) {
                        DocumentSnapshot eventDoc = (DocumentSnapshot) eventTask.getResult();
                        try {
                            if (eventDoc.exists()) {
                                Event event = Event.fromMap(eventDoc.getData());
                                if (event != null) {
                                    if (event.getId() == null || event.getId().isEmpty()) {
                                        event.setId(eventDoc.getId());
                                    }
                                    if (event.getStartsAtEpochMs() > currentTime) {
                                        events.add(event);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing event", e);
                        }
                    }
                }
                
                return events;
            });
        });
    }
}

