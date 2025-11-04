package com.EventEase.data.firebase;

import android.util.Log;

import com.EventEase.data.EventRepository;
import com.EventEase.data.ListenerRegistration;
import com.EventEase.data.WaitlistCountListener;
import com.EventEase.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FirebaseEventRepository
 * Now loads events from Firestore for persistence.
 */
public class FirebaseEventRepository implements EventRepository {

    private static final String TAG = "EventRepository";
    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final Map<String, Integer> waitlistCounts = new ConcurrentHashMap<>();
    private final Map<String, List<WaitlistCountListener>> listeners = new ConcurrentHashMap<>();
    private final FirebaseFirestore db;

    public FirebaseEventRepository(List<Event> seed) {
        this.db = FirebaseFirestore.getInstance();
        
        // Load seed events into memory
        for (Event e : seed) {
            events.put(e.getId(), e);
            waitlistCounts.put(e.getId(), 0);
        }
        
        // Also load all events from Firestore
        loadEventsFromFirestore();
    }
    
    private void loadEventsFromFirestore() {
        db.collection("events")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        try {
                            Event event = Event.fromMap(doc.getData());
                            if (event != null && event.getId() != null) {
                                events.put(event.getId(), event);
                                if (!waitlistCounts.containsKey(event.getId())) {
                                    waitlistCounts.put(event.getId(), event.getWaitlistCount());
                                }
                                Log.d(TAG, "Loaded event from Firestore: " + event.getTitle());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing event from Firestore", e);
                        }
                    }
                    Log.d(TAG, "Total events loaded: " + events.size());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load events from Firestore", e);
                });
    }

    @Override
    public Task<List<Event>> getOpenEvents(Date now) {
        // Return cached events first, but also refresh from Firestore
        return db.collection("events")
                .get()
                .continueWith(task -> {
                    // Update cache with latest Firestore data
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            try {
                                Event event = Event.fromMap(doc.getData());
                                if (event != null && event.getId() != null) {
                                    events.put(event.getId(), event);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing event", e);
                            }
                        }
                    }
                    
                    // Filter open events
                    List<Event> result = new ArrayList<>();
                    for (Event e : events.values()) {
                        if (e.getStartAt() == null || e.getStartAt().after(now)) {
                            result.add(e);
                        }
                    }
                    result.sort(Comparator.comparing(Event::getStartAt, Comparator.nullsLast(Comparator.naturalOrder())));
                    return Collections.unmodifiableList(result);
                });
    }

    @Override
    public Task<Event> getEvent(String eventId) {
        Event e = events.get(eventId);
        if (e != null) return Tasks.forResult(e);
        return Tasks.forException(new NoSuchElementException("Event not found: " + eventId));
    }

    @Override
    public ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener l) {
        listeners.computeIfAbsent(eventId, k -> new ArrayList<>()).add(l);
        
        // First, get the real count from Firebase by querying waitlist documents
        queryWaitlistCount(eventId);
        
        // Then notify with current cached count
        l.onChanged(waitlistCounts.getOrDefault(eventId, 0));
        
        return new ListenerRegistration() {
            private boolean removed = false;
            @Override public void remove() {
                if (removed) return;
                List<WaitlistCountListener> ls = listeners.get(eventId);
                if (ls != null) ls.remove(l);
                removed = true;
            }
        };
    }
    
    private void queryWaitlistCount(String eventId) {
        // Query the waitlists collection to count actual entries
        db.collection("waitlists")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int actualCount = querySnapshot != null ? querySnapshot.size() : 0;
                    Log.d(TAG, "Queried waitlist count for event " + eventId + ": " + actualCount);
                    
                    // Update cached count
                    waitlistCounts.put(eventId, actualCount);
                    
                    // Also update the event's waitlistCount in Firestore
                    db.collection("events").document(eventId)
                            .update("waitlistCount", actualCount)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Updated waitlistCount in event document for " + eventId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to update waitlistCount in event document", e);
                            });
                    
                    // Notify all listeners
                    notifyCount(eventId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query waitlist count for event " + eventId, e);
                });
    }

    /* package */ void incrementWaitlist(String eventId) {
        waitlistCounts.merge(eventId, 1, Integer::sum);
        
        // Also update Firestore
        db.collection("events").document(eventId)
                .update("waitlistCount", com.google.firebase.firestore.FieldValue.increment(1))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Waitlist count incremented in Firestore for event " + eventId);
                    // Refresh the event from Firestore to get the updated count
                    db.collection("events").document(eventId).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    Event event = Event.fromMap(documentSnapshot.getData());
                                    if (event != null) {
                                        events.put(eventId, event);
                                        waitlistCounts.put(eventId, event.getWaitlistCount());
                                        notifyCount(eventId);
                                    }
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error incrementing waitlist count in Firestore", e);
                });
        
        notifyCount(eventId);
    }

    /* package */ void decrementWaitlist(String eventId) {
        Integer current = waitlistCounts.get(eventId);
        if (current != null && current > 0) {
            waitlistCounts.put(eventId, current - 1);
        }
        
        // Also update Firestore
        db.collection("events").document(eventId)
                .update("waitlistCount", com.google.firebase.firestore.FieldValue.increment(-1))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Waitlist count decremented in Firestore for event " + eventId);
                    // Refresh the event from Firestore to get the updated count
                    db.collection("events").document(eventId).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    Event event = Event.fromMap(documentSnapshot.getData());
                                    if (event != null) {
                                        events.put(eventId, event);
                                        waitlistCounts.put(eventId, event.getWaitlistCount());
                                        notifyCount(eventId);
                                    }
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error decrementing waitlist count in Firestore", e);
                });
        
        notifyCount(eventId);
    }

    /* package */ boolean isKnown(String eventId) {
        return events.containsKey(eventId);
    }

    private void notifyCount(String eventId) {
        int count = waitlistCounts.getOrDefault(eventId, 0);
        List<WaitlistCountListener> ls = listeners.get(eventId);
        if (ls != null) {
            for (WaitlistCountListener l : new ArrayList<>(ls)) {
                l.onChanged(count);
            }
        }
    }
}
