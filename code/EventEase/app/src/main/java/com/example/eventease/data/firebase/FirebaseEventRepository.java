package com.example.eventease.data.firebase;

import android.util.Log;

import com.example.eventease.data.EventRepository;
import com.example.eventease.data.ListenerRegistration;
import com.example.eventease.data.WaitlistCountListener;
import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Firebase implementation of EventRepository.
 * Loads events from Firestore and provides real-time waitlist count updates.
 */
public class FirebaseEventRepository implements EventRepository {

    private static final String TAG = "EventRepository";
    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final Map<String, Integer> waitlistCounts = new ConcurrentHashMap<>();
    private final Map<String, List<WaitlistCountListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, com.google.firebase.firestore.ListenerRegistration> waitlistCountRegistrations = new ConcurrentHashMap<>();
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

        ensureWaitlistListener(eventId);

        // Notify with the latest cached value (or zero if none yet)
        l.onChanged(waitlistCounts.getOrDefault(eventId, 0));

        return new ListenerRegistration() {
            private boolean removed = false;
            @Override public void remove() {
                if (removed) return;
                List<WaitlistCountListener> ls = listeners.get(eventId);
                if (ls != null) ls.remove(l);
                removed = true;

                if (ls == null || ls.isEmpty()) {
                    com.google.firebase.firestore.ListenerRegistration reg = waitlistCountRegistrations.remove(eventId);
                    if (reg != null) {
                        reg.remove();
                    }
                }
            }
        };
    }
    
    private void queryWaitlistCount(String eventId) {
        db.collection("events")
                .document(eventId)
                .collection("WaitlistedEntrants")
                .get()
                .addOnSuccessListener(snap -> {
                    int actualCount = snap != null ? snap.size() : 0;
                    Log.d(TAG, "Queried waitlist subcollection count for event " + eventId + ": " + actualCount);

                    waitlistCounts.put(eventId, actualCount);

                    db.collection("events").document(eventId)
                            .update("waitlistCount", actualCount)
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to sync waitlistCount field", e));

                    notifyCount(eventId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to query waitlist count for event " + eventId, e));
    }

    /* package */ void incrementWaitlist(String eventId) {
        queryWaitlistCount(eventId);
    }

    /* package */ void decrementWaitlist(String eventId) {
        queryWaitlistCount(eventId);
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

    @Override
    public Task<Void> create(Event event) {
        if (event == null || event.getId() == null) {
            return Tasks.forException(new IllegalArgumentException("Event must have an ID"));
        }
        events.put(event.getId(), event);
        waitlistCounts.put(event.getId(), event.getWaitlistCount());
        return db.collection("events").document(event.getId()).set(event.toMap());
    }

    private void ensureWaitlistListener(String eventId) {
        if (waitlistCountRegistrations.containsKey(eventId)) {
            return;
        }

        com.google.firebase.firestore.ListenerRegistration reg = db.collection("events")
                .document(eventId)
                .collection("WaitlistedEntrants")
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Waitlist listener failed for " + eventId, error);
                        return;
                    }

                    // Use DocumentChange for more accurate real-time updates
                    if (snap != null && !snap.getDocumentChanges().isEmpty()) {
                        // Process changes to update count incrementally
                        int currentCount = waitlistCounts.getOrDefault(eventId, 0);
                        for (com.google.firebase.firestore.DocumentChange change : snap.getDocumentChanges()) {
                            if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                currentCount++;
                            } else if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                currentCount = Math.max(0, currentCount - 1);
                            }
                        }
                        waitlistCounts.put(eventId, currentCount);
                    } else {
                        // Fallback to size if no changes (initial load)
                        int count = snap != null ? snap.size() : 0;
                        waitlistCounts.put(eventId, count);
                    }

                    int finalCount = waitlistCounts.get(eventId);
                    db.collection("events").document(eventId)
                            .update("waitlistCount", finalCount)
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to sync waitlistCount field", e));

                    notifyCount(eventId);
                });

        waitlistCountRegistrations.put(eventId, reg);
    }
}
