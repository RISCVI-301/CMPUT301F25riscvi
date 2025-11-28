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
import com.google.firebase.firestore.QuerySnapshot;
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
            
            // CRITICAL: Remove from ALL other collections when admitting
            DocumentReference nonSelectedDoc = eventRef.collection("NonSelectedEntrants").document(uid);
            DocumentReference cancelledDoc = eventRef.collection("CancelledEntrants").document(uid);
            
            WriteBatch batch = db.batch();
            
            batch.set(admittedDoc, admittedData, SetOptions.merge());
            // Remove from ALL other collections to ensure user exists in only ONE collection
            batch.delete(waitlistDoc);
            batch.delete(selectedDoc);
            batch.delete(nonSelectedDoc);
            batch.delete(cancelledDoc);
            
            return batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SUCCESS: User " + uid + " admitted to event, moved to AdmittedEntrants and removed from all other collections");
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
        Log.d(TAG, "Getting upcoming events for uid: " + uid);
        
        // Use alternative method by default (doesn't require Firestore index)
        // This queries all events and checks AdmittedEntrants subcollection for each
        return getUpcomingEventsAlternative(uid);
    }
    
    /**
     * Method to get upcoming events by querying all events and checking AdmittedEntrants subcollection.
     * This doesn't require a Firestore index and works reliably.
     */
    private Task<List<Event>> getUpcomingEventsAlternative(String uid) {
        Log.d(TAG, "Getting upcoming events for uid: " + uid + " (querying all events and checking AdmittedEntrants)");
        
        // Query all events and check AdmittedEntrants subcollection for each
        return db.collection("events").get().continueWithTask(eventsTask -> {
            if (!eventsTask.isSuccessful() || eventsTask.getResult() == null) {
                Log.e(TAG, "Failed to load events: " + (eventsTask.getException() != null ? eventsTask.getException().getMessage() : "Unknown error"));
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            QuerySnapshot eventsSnapshot = eventsTask.getResult();
            Log.d(TAG, "Found " + eventsSnapshot.size() + " total events, checking which ones user is admitted to");
            
            if (eventsSnapshot.isEmpty()) {
                Log.d(TAG, "No events found in database");
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            // Create a list to store event info along with admission status
            List<Task<Boolean>> admittedTasks = new ArrayList<>();
            List<DocumentSnapshot> eventDocs = new ArrayList<>();
            
            for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                eventDocs.add(eventDoc);
                DocumentReference admittedRef = eventDoc.getReference()
                    .collection("AdmittedEntrants")
                    .document(uid);
                Task<Boolean> admissionTask = admittedRef.get().continueWith(admittedTask -> {
                    boolean isAdmitted = admittedTask.isSuccessful() && 
                                        admittedTask.getResult() != null && 
                                        admittedTask.getResult().exists();
                    if (isAdmitted) {
                        Log.d(TAG, "User is admitted to event: " + eventDoc.getId());
                    }
                    return isAdmitted;
                });
                admittedTasks.add(admissionTask);
            }
            
            if (admittedTasks.isEmpty()) {
                Log.d(TAG, "No events to check");
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            Log.d(TAG, "Checking " + admittedTasks.size() + " events for admission status");
            // Use whenAllComplete to wait for all tasks (including failed ones)
            return Tasks.whenAllComplete(admittedTasks).continueWith(allTasks -> {
                if (!allTasks.isSuccessful() || allTasks.getResult() == null) {
                    Log.e(TAG, "Failed to complete all admission checks: " + 
                          (allTasks.getException() != null ? allTasks.getException().getMessage() : "Unknown error"));
                    return new ArrayList<Event>();
                }
                
                List<Event> events = new ArrayList<>();
                long currentTime = System.currentTimeMillis();
                int admittedCount = 0;
                int upcomingCount = 0;
                
                // Extract results from completed tasks
                List<com.google.android.gms.tasks.Task<?>> completedTasks = allTasks.getResult();
                for (int i = 0; i < completedTasks.size() && i < eventDocs.size(); i++) {
                    @SuppressWarnings("unchecked")
                    Task<Boolean> admittedTask = (Task<Boolean>) completedTasks.get(i);
                    
                    Boolean isAdmitted = null;
                    if (admittedTask.isSuccessful()) {
                        try {
                            isAdmitted = admittedTask.getResult();
                        } catch (Exception e) {
                            Log.w(TAG, "Error getting result from admission check for event " + eventDocs.get(i).getId() + ": " + e.getMessage());
                        }
                    } else {
                        Exception ex = admittedTask.getException();
                        if (ex != null) {
                            Log.w(TAG, "Admission check failed for event " + eventDocs.get(i).getId() + ": " + ex.getMessage());
                        }
                    }
                    
                    if (Boolean.TRUE.equals(isAdmitted)) {
                        admittedCount++;
                        DocumentSnapshot eventDoc = eventDocs.get(i);
                        try {
                            if (!eventDoc.exists()) {
                                Log.w(TAG, "Event document doesn't exist: " + eventDoc.getId());
                                continue;
                            }
                            
                            Event event = Event.fromMap(eventDoc.getData());
                            if (event != null) {
                                if (event.getId() == null || event.getId().isEmpty()) {
                                    event.setId(eventDoc.getId());
                                }
                                
                                // Check deadline first, then fall back to start time
                                long deadline = event.getDeadlineEpochMs();
                                long startTime = event.getStartsAtEpochMs();
                                
                                // If deadline is set, use it to determine if event is upcoming
                                if (deadline > 0) {
                                    if (deadline > currentTime) {
                                        // Deadline hasn't passed - event is upcoming
                                        events.add(event);
                                        upcomingCount++;
                                        long hoursUntilDeadline = (deadline - currentTime) / (1000 * 60 * 60);
                                        Log.d(TAG, "Added upcoming event: " + event.getTitle() + " (id: " + event.getId() + ", deadline in " + hoursUntilDeadline + " hours)");
                                    } else {
                                        // Deadline has passed - exclude from upcoming (will be in previous events)
                                        Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") deadline has passed (deadline: " + deadline + ", currentTime: " + currentTime + "), excluding from upcoming");
                                    }
                                } else if (startTime > 0) {
                                    // No deadline, use start time
                                    if (startTime > currentTime) {
                                        // Event is in the future
                                        events.add(event);
                                        upcomingCount++;
                                        long hoursUntilStart = (startTime - currentTime) / (1000 * 60 * 60);
                                        Log.d(TAG, "Added upcoming event: " + event.getTitle() + " (id: " + event.getId() + ", starts in " + hoursUntilStart + " hours)");
                                    } else {
                                        // Event has already started or passed
                                        Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") has already started/passed (startTime: " + startTime + ", currentTime: " + currentTime + "), excluding from upcoming");
                                    }
                                } else {
                                    // No deadline and no start time - include it as upcoming (treat as upcoming)
                                    events.add(event);
                                    upcomingCount++;
                                    Log.d(TAG, "Added upcoming event (no deadline/start time): " + event.getTitle() + " (id: " + event.getId() + ")");
                                }
                            } else {
                                Log.w(TAG, "Failed to parse event document: " + eventDoc.getId());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing event: " + eventDoc.getId(), e);
                        }
                    }
                }
                
                Log.d(TAG, "Found " + admittedCount + " admitted events, " + upcomingCount + " are upcoming for uid: " + uid);
                return events;
            });
        });
    }

    /**
     * Gets previous events (events where deadline has passed) for a user who is in AdmittedEntrants.
     * 
     * @param uid the user ID to get previous events for
     * @return a Task that resolves to a list of previous events
     */
    public Task<List<Event>> getPreviousEvents(String uid) {
        Log.d(TAG, "Getting previous events for uid: " + uid);
        
        long currentTime = System.currentTimeMillis();
        
        // Query all events and check AdmittedEntrants subcollection for each
        return db.collection("events").get().continueWithTask(eventsTask -> {
            if (!eventsTask.isSuccessful() || eventsTask.getResult() == null) {
                Log.e(TAG, "Failed to load events: " + (eventsTask.getException() != null ? eventsTask.getException().getMessage() : "Unknown error"));
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            QuerySnapshot eventsSnapshot = eventsTask.getResult();
            Log.d(TAG, "Found " + eventsSnapshot.size() + " total events, checking which ones user is admitted to and deadline has passed");
            
            if (eventsSnapshot.isEmpty()) {
                Log.d(TAG, "No events found in database");
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            // Create a list to store event info along with admission status
            List<Task<Boolean>> admittedTasks = new ArrayList<>();
            List<DocumentSnapshot> eventDocs = new ArrayList<>();
            
            for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                eventDocs.add(eventDoc);
                DocumentReference admittedRef = eventDoc.getReference()
                    .collection("AdmittedEntrants")
                    .document(uid);
                Task<Boolean> admissionTask = admittedRef.get().continueWith(admittedTask -> {
                    boolean isAdmitted = admittedTask.isSuccessful() && 
                                        admittedTask.getResult() != null && 
                                        admittedTask.getResult().exists();
                    return isAdmitted;
                });
                admittedTasks.add(admissionTask);
            }
            
            if (admittedTasks.isEmpty()) {
                Log.d(TAG, "No events to check");
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            Log.d(TAG, "Checking " + admittedTasks.size() + " events for admission status and deadline");
            // Use whenAllComplete to wait for all tasks (including failed ones)
            return Tasks.whenAllComplete(admittedTasks).continueWith(allTasks -> {
                if (!allTasks.isSuccessful() || allTasks.getResult() == null) {
                    Log.e(TAG, "Failed to complete all admission checks: " + 
                          (allTasks.getException() != null ? allTasks.getException().getMessage() : "Unknown error"));
                    return new ArrayList<Event>();
                }
                
                List<Event> events = new ArrayList<>();
                int admittedCount = 0;
                int previousCount = 0;
                
                // Extract results from completed tasks
                List<com.google.android.gms.tasks.Task<?>> completedTasks = allTasks.getResult();
                for (int i = 0; i < completedTasks.size() && i < eventDocs.size(); i++) {
                    @SuppressWarnings("unchecked")
                    Task<Boolean> admittedTask = (Task<Boolean>) completedTasks.get(i);
                    
                    Boolean isAdmitted = null;
                    if (admittedTask.isSuccessful()) {
                        try {
                            isAdmitted = admittedTask.getResult();
                        } catch (Exception e) {
                            Log.w(TAG, "Error getting result from admission check for event " + eventDocs.get(i).getId() + ": " + e.getMessage());
                        }
                    } else {
                        Exception ex = admittedTask.getException();
                        if (ex != null) {
                            Log.w(TAG, "Admission check failed for event " + eventDocs.get(i).getId() + ": " + ex.getMessage());
                        }
                    }
                    
                    if (Boolean.TRUE.equals(isAdmitted)) {
                        admittedCount++;
                        DocumentSnapshot eventDoc = eventDocs.get(i);
                        try {
                            if (!eventDoc.exists()) {
                                Log.w(TAG, "Event document doesn't exist: " + eventDoc.getId());
                                continue;
                            }
                            
                            Event event = Event.fromMap(eventDoc.getData());
                            if (event != null) {
                                if (event.getId() == null || event.getId().isEmpty()) {
                                    event.setId(eventDoc.getId());
                                }
                                
                                // Check if deadline has passed
                                long deadline = event.getDeadlineEpochMs();
                                if (deadline > 0 && deadline < currentTime) {
                                    // Event deadline has passed - include it as previous event
                                    events.add(event);
                                    previousCount++;
                                    long hoursSinceDeadline = (currentTime - deadline) / (1000 * 60 * 60);
                                    Log.d(TAG, "Added previous event: " + event.getTitle() + " (id: " + event.getId() + ", deadline passed " + hoursSinceDeadline + " hours ago)");
                                } else if (deadline == 0) {
                                    // No deadline set - skip (can't determine if it's previous)
                                    Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") has no deadline set, skipping");
                                } else {
                                    // Deadline hasn't passed yet
                                    Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") deadline hasn't passed yet (deadline: " + deadline + ", currentTime: " + currentTime + "), excluding from previous");
                                }
                            } else {
                                Log.w(TAG, "Failed to parse event document: " + eventDoc.getId());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing event: " + eventDoc.getId(), e);
                        }
                    }
                }
                
                Log.d(TAG, "Found " + admittedCount + " admitted events, " + previousCount + " are previous (deadline passed) for uid: " + uid);
                return events;
            });
        });
    }
}

