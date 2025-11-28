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
            // Remove from collections that should no longer contain the entrant
            batch.delete(waitlistDoc);
            batch.delete(nonSelectedDoc);
            batch.delete(cancelledDoc);
            
            // Keep entrant in SelectedEntrants but mark them as accepted so organizer can still see them
            Map<String, Object> selectedUpdates = new HashMap<>();
            selectedUpdates.put("status", "ACCEPTED");
            selectedUpdates.put("acceptedAt", FieldValue.serverTimestamp());
            batch.set(selectedDoc, selectedUpdates, SetOptions.merge());
            
            return batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SUCCESS: User " + uid + " admitted to event. Record kept in SelectedEntrants with status=ACCEPTED");
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
        if (eventId == null || eventId.isEmpty() || uid == null || uid.isEmpty()) {
            Log.w(TAG, "isAdmitted called with null/empty eventId or uid");
            return Tasks.forResult(false);
        }
        
        Log.d(TAG, "Checking if user " + uid + " is admitted to event " + eventId);
        return db.collection("events")
                .document(eventId)
                .collection("AdmittedEntrants")
                .document(uid)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Error checking admitted status for event " + eventId + ", uid: " + uid, task.getException());
                        return false;
                    }
                    
                    boolean isAdmitted = task.getResult() != null && task.getResult().exists();
                    Log.d(TAG, "User " + uid + " admitted status for event " + eventId + ": " + isAdmitted);
                    return isAdmitted;
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
                DocumentReference eventRef = eventDoc.getReference();
                DocumentReference admittedRef = eventRef.collection("AdmittedEntrants").document(uid);
                DocumentReference selectedRef = eventRef.collection("SelectedEntrants").document(uid);
                
                // CRITICAL: Check both SelectedEntrants AND AdmittedEntrants
                // User must be in BOTH collections to show in upcoming events
                Task<Boolean> admittedTask = admittedRef.get().continueWith(task -> {
                    return task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                });
                
                Task<Boolean> selectedTask = selectedRef.get().continueWith(task -> {
                    return task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                });
                
                Task<Boolean> admissionTask = Tasks.whenAllComplete(admittedTask, selectedTask)
                    .continueWith(allTasks -> {
                        if (!allTasks.isSuccessful() || allTasks.getResult() == null) {
                            return false;
                        }
                        
                        boolean isAdmitted = false;
                        boolean isSelected = false;
                        
                        try {
                            @SuppressWarnings("unchecked")
                            Task<Boolean> admittedResult = (Task<Boolean>) allTasks.getResult().get(0);
                            if (admittedResult.isSuccessful() && admittedResult.getResult() != null) {
                                isAdmitted = Boolean.TRUE.equals(admittedResult.getResult());
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error checking admitted status", e);
                        }
                        
                        try {
                            @SuppressWarnings("unchecked")
                            Task<Boolean> selectedResult = (Task<Boolean>) allTasks.getResult().get(1);
                            if (selectedResult.isSuccessful() && selectedResult.getResult() != null) {
                                isSelected = Boolean.TRUE.equals(selectedResult.getResult());
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error checking selected status", e);
                        }
                        
                        // CRITICAL: User must be in BOTH SelectedEntrants AND AdmittedEntrants
                        boolean both = isAdmitted && isSelected;
                        if (both) {
                            Log.d(TAG, "User is in both SelectedEntrants and AdmittedEntrants for event: " + eventDoc.getId());
                    }
                        return both;
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
                            
                            java.util.Map<String, Object> eventData = eventDoc.getData();
                            if (eventData == null) {
                                Log.w(TAG, "Event document " + eventDoc.getId() + " has null data, skipping");
                                continue;
                            }
                            
                            Event event = Event.fromMap(eventData);
                            if (event != null) {
                                if (event.getId() == null || event.getId().isEmpty()) {
                                    event.setId(eventDoc.getId());
                                }
                                
                                // Use event start time to determine if event is upcoming
                                // Note: deadlineEpochMs is for invitation acceptance deadline, NOT the event date
                                long startTime = event.getStartsAtEpochMs();
                                
                                if (startTime > 0) {
                                    if (startTime > currentTime) {
                                        // Event hasn't started yet - event is upcoming
                                        events.add(event);
                                        upcomingCount++;
                                        long hoursUntilStart = (startTime - currentTime) / (1000 * 60 * 60);
                                        Log.d(TAG, "Added upcoming event: " + event.getTitle() + " (id: " + event.getId() + ", starts in " + hoursUntilStart + " hours)");
                                    } else {
                                        // Event has already started or passed
                                        Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") has already started/passed (startTime: " + startTime + ", currentTime: " + currentTime + "), excluding from upcoming");
                                    }
                                } else {
                                    // No start time set - include it as upcoming (treat as upcoming by default)
                                    events.add(event);
                                    upcomingCount++;
                                    Log.d(TAG, "Added upcoming event (no start time set): " + event.getTitle() + " (id: " + event.getId() + ")");
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
     * Gets previous events (events where the event start date has passed) for a user.
     * Includes ALL events the user was associated with (waitlisted, selected, non-selected, cancelled, or admitted).
     * 
     * @param uid the user ID to get previous events for
     * @return a Task that resolves to a list of previous events
     */
    public Task<List<Event>> getPreviousEvents(String uid) {
        Log.d(TAG, "Getting previous events for uid: " + uid);
        
        long currentTime = System.currentTimeMillis();
        
        // Query all events and check ALL participation subcollections for each
        return db.collection("events").get().continueWithTask(eventsTask -> {
            if (!eventsTask.isSuccessful() || eventsTask.getResult() == null) {
                Exception error = eventsTask.getException();
                Log.e(TAG, "Failed to load events: " + (error != null ? error.getMessage() : "Unknown error"));
                if (error != null) {
                    Log.e(TAG, "Error details: ", error);
                    // Check if it's a permission error
                    if (error instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                        com.google.firebase.firestore.FirebaseFirestoreException firestoreEx = 
                            (com.google.firebase.firestore.FirebaseFirestoreException) error;
                        if (firestoreEx.getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.e(TAG, "PERMISSION DENIED: User does not have permission to read events collection. Check Firestore security rules.");
                        }
                    }
                }
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            QuerySnapshot eventsSnapshot = eventsTask.getResult();
            Log.d(TAG, "Found " + eventsSnapshot.size() + " total events, checking which ones user participated in and event start date has passed");
            
            if (eventsSnapshot.isEmpty()) {
                Log.d(TAG, "No events found in database");
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            // Create a list to store event info along with participation status
            // Check ALL subcollections: WaitlistedEntrants, SelectedEntrants, NonSelectedEntrants, CancelledEntrants, AdmittedEntrants
            List<Task<Boolean>> participationTasks = new ArrayList<>();
            List<DocumentSnapshot> eventDocs = new ArrayList<>();
            
            String[] subcollections = {
                "WaitlistedEntrants",
                "SelectedEntrants",
                "NonSelectedEntrants",
                "CancelledEntrants",
                "AdmittedEntrants"
            };
            
            for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                eventDocs.add(eventDoc);
                DocumentReference eventRef = eventDoc.getReference();
                
                // Check if user is in ANY of the participation subcollections
                List<Task<Boolean>> subcollectionTasks = new ArrayList<>();
                for (String subcollectionName : subcollections) {
                    DocumentReference userRef = eventRef.collection(subcollectionName).document(uid);
                    Task<Boolean> subcollectionTask = userRef.get().continueWith(task -> {
                        return task.isSuccessful() && 
                               task.getResult() != null && 
                               task.getResult().exists();
                    });
                    subcollectionTasks.add(subcollectionTask);
                }
                
                // Combine all subcollection checks - user is associated if they're in ANY subcollection
                Task<Boolean> participationTask = Tasks.whenAllComplete(subcollectionTasks).continueWith(allSubTasks -> {
                    if (!allSubTasks.isSuccessful() || allSubTasks.getResult() == null) {
                        return false;
                    }
                    
                    // Check if user exists in any subcollection
                    for (com.google.android.gms.tasks.Task<?> subTask : allSubTasks.getResult()) {
                        if (subTask.isSuccessful()) {
                            try {
                                @SuppressWarnings("unchecked")
                                Task<Boolean> boolTask = (Task<Boolean>) subTask;
                                Boolean exists = boolTask.getResult();
                                if (Boolean.TRUE.equals(exists)) {
                                    return true; // User is in at least one subcollection
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error checking subcollection participation", e);
                            }
                        }
                    }
                    return false; // User not in any subcollection
                });
                
                participationTasks.add(participationTask);
            }
            
            if (participationTasks.isEmpty()) {
                Log.d(TAG, "No events to check");
                return Tasks.forResult(new ArrayList<Event>());
            }
            
            Log.d(TAG, "Checking " + participationTasks.size() + " events for participation status and event start date");
            // Use whenAllComplete to wait for all tasks (including failed ones)
            return Tasks.whenAllComplete(participationTasks).continueWith(allTasks -> {
                if (!allTasks.isSuccessful() || allTasks.getResult() == null) {
                    Log.e(TAG, "Failed to complete all participation checks: " + 
                          (allTasks.getException() != null ? allTasks.getException().getMessage() : "Unknown error"));
                    return new ArrayList<Event>();
                }
                
                List<Event> events = new ArrayList<>();
                int participatedCount = 0;
                int previousCount = 0;
                
                // Extract results from completed tasks
                List<com.google.android.gms.tasks.Task<?>> completedTasks = allTasks.getResult();
                for (int i = 0; i < completedTasks.size() && i < eventDocs.size(); i++) {
                    @SuppressWarnings("unchecked")
                    Task<Boolean> participationTask = (Task<Boolean>) completedTasks.get(i);
                    
                    Boolean isParticipated = null;
                    if (participationTask.isSuccessful()) {
                        try {
                            isParticipated = participationTask.getResult();
                        } catch (Exception e) {
                            Log.w(TAG, "Error getting result from participation check for event " + eventDocs.get(i).getId() + ": " + e.getMessage());
                        }
                    } else {
                        Exception ex = participationTask.getException();
                        if (ex != null) {
                            Log.w(TAG, "Participation check failed for event " + eventDocs.get(i).getId() + ": " + ex.getMessage());
                        }
                    }
                    
                    if (Boolean.TRUE.equals(isParticipated)) {
                        participatedCount++;
                        DocumentSnapshot eventDoc = eventDocs.get(i);
                        try {
                            if (!eventDoc.exists()) {
                                Log.w(TAG, "Event document doesn't exist: " + eventDoc.getId());
                                continue;
                            }
                            
                            java.util.Map<String, Object> eventData = eventDoc.getData();
                            if (eventData == null) {
                                Log.w(TAG, "Event document " + eventDoc.getId() + " has null data, skipping");
                                continue;
                            }
                            
                            Event event = Event.fromMap(eventData);
                            if (event != null) {
                                if (event.getId() == null || event.getId().isEmpty()) {
                                    event.setId(eventDoc.getId());
                                }
                                
                                // Check if event start date has passed
                                // Note: deadlineEpochMs is for invitation acceptance deadline, NOT the event date
                                long startTime = event.getStartsAtEpochMs();
                                boolean isPrevious = false;
                                String reason = "";
                                
                                if (startTime > 0 && startTime < currentTime) {
                                    // Event start date has passed - include it as previous event
                                    isPrevious = true;
                                    long hoursSinceStart = (currentTime - startTime) / (1000 * 60 * 60);
                                    reason = "event started " + hoursSinceStart + " hours ago";
                                } else if (startTime > 0 && startTime >= currentTime) {
                                    // Event start date hasn't passed yet
                                    Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") hasn't started yet (startTime: " + startTime + ", currentTime: " + currentTime + "), excluding from previous");
                                } else {
                                    // No start time set - can't determine if it's previous
                                    Log.d(TAG, "Event " + event.getTitle() + " (id: " + event.getId() + ") has no start time set, skipping from previous events");
                                }
                                
                                if (isPrevious) {
                                    events.add(event);
                                    previousCount++;
                                    Log.d(TAG, "Added previous event: " + event.getTitle() + " (id: " + event.getId() + ", " + reason + ")");
                                }
                            } else {
                                Log.w(TAG, "Failed to parse event document: " + eventDoc.getId());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing event: " + eventDoc.getId(), e);
                        }
                    }
                }
                
                Log.d(TAG, "Found " + participatedCount + " events user participated in, " + previousCount + " are previous (event start date passed) for uid: " + uid);
                return events;
            });
        });
    }
}

