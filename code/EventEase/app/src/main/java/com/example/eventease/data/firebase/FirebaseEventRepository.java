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
import com.google.firebase.firestore.QuerySnapshot;

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
                    
                    long nowMs = now != null ? now.getTime() : System.currentTimeMillis();
                    // Filter open events
                    List<Event> result = new ArrayList<>();
                    Iterator<Map.Entry<String, Event>> iterator = events.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Event> entry = iterator.next();
                        Event e = entry.getValue();

                        // Remove cached events whose registration window has ended
                        long registrationEnd = e.getRegistrationEnd();
                        if (registrationEnd > 0 && nowMs > registrationEnd) {
                            iterator.remove();
                            continue;
                        }

                        Date startDate = e.getStartAt();
                        if (startDate == null || startDate.after(now)) {
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

        // Trigger an immediate refresh so the listener gets an accurate value,
        // even if the cached count is stale.
        queryWaitlistCount(eventId);

        // Also notify with the latest cached value (or zero if none yet) for instant UI feedback
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
        // Compute a "logical" waitlist count depending on event state:
        // - Before selectionProcessed: number of docs in WaitlistedEntrants
        // - After selectionProcessed: NonSelectedEntrants
        //   + SelectedEntrants with PENDING invitations
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        Log.w(TAG, "queryWaitlistCount: event not found: " + eventId);
                        waitlistCounts.put(eventId, 0);
                        notifyCount(eventId);
                        return;
                    }

                    Boolean selectionProcessed = eventDoc.getBoolean("selectionProcessed");
                    boolean isSelectionProcessed = selectionProcessed != null && selectionProcessed;

                    if (!isSelectionProcessed) {
                        // BEFORE SELECTION: use WaitlistedEntrants subcollection size
                        eventDoc.getReference()
                                .collection("WaitlistedEntrants")
                .get()
                .addOnSuccessListener(snap -> {
                    int actualCount = snap != null ? snap.size() : 0;
                                    Log.d(TAG, "Queried pre-selection waitlist count for event " + eventId + ": " + actualCount);

                    waitlistCounts.put(eventId, actualCount);

                                    eventDoc.getReference()
                            .update("waitlistCount", actualCount)
                                            .addOnFailureListener(e -> Log.e(TAG, "Failed to sync waitlistCount field (pre-selection)", e));

                                    notifyCount(eventId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to query WaitlistedEntrants for event " + eventId, e);
                                    notifyCount(eventId);
                                });
                    } else {
                        // AFTER SELECTION:
                        // waitlistCount = NonSelectedEntrants
                        //                + SelectedEntrants that still have PENDING invitations
                        final DocumentSnapshot finalEventDoc = eventDoc;
                        Task<QuerySnapshot> nonSelectedTask = eventDoc.getReference()
                                .collection("NonSelectedEntrants")
                                .get();
                        Task<QuerySnapshot> selectedTask = eventDoc.getReference()
                                .collection("SelectedEntrants")
                                .get();
                        Task<QuerySnapshot> pendingInvitesTask = db.collection("invitations")
                                .whereEqualTo("eventId", eventId)
                                .whereEqualTo("status", "PENDING")
                                .get();

                        Tasks.whenAllSuccess(nonSelectedTask, selectedTask, pendingInvitesTask)
                                .addOnSuccessListener(results -> {
                                    QuerySnapshot nonSelectedSnap = (QuerySnapshot) results.get(0);
                                    QuerySnapshot selectedSnap = (QuerySnapshot) results.get(1);
                                    QuerySnapshot pendingSnap = (QuerySnapshot) results.get(2);

                                    int nonSelectedCount = nonSelectedSnap != null ? nonSelectedSnap.size() : 0;

                                    // Build set of selected userIds
                                    java.util.Set<String> selectedIds = new java.util.HashSet<>();
                                    if (selectedSnap != null) {
                                        for (DocumentSnapshot doc : selectedSnap.getDocuments()) {
                                            selectedIds.add(doc.getId());
                                        }
                                    }

                                    // Build set of uids with PENDING invitations
                                    java.util.Set<String> pendingUids = new java.util.HashSet<>();
                                    if (pendingSnap != null) {
                                        for (DocumentSnapshot doc : pendingSnap.getDocuments()) {
                                            String uid = doc.getString("uid");
                                            if (uid != null && !uid.isEmpty()) {
                                                pendingUids.add(uid);
                                            }
                                        }
                                    }

                                    // Count how many selected entrants still have PENDING invitations
                                    int selectedPendingCount = 0;
                                    for (String uid : pendingUids) {
                                        if (selectedIds.contains(uid)) {
                                            selectedPendingCount++;
                                        }
                                    }

                                    int logicalCount = nonSelectedCount + selectedPendingCount;

                                    Log.d(TAG, "Queried post-selection waitlist count for event " + eventId +
                                            ": nonSelected=" + nonSelectedCount +
                                            ", selectedPending=" + selectedPendingCount +
                                            ", total=" + logicalCount);

                                    waitlistCounts.put(eventId, logicalCount);

                                    finalEventDoc.getReference()
                                            .update("waitlistCount", logicalCount)
                                            .addOnFailureListener(e -> Log.e(TAG, "Failed to sync waitlistCount field (post-selection)", e));

                    notifyCount(eventId);
                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to compute post-selection waitlist count for event " + eventId, e);
                                    notifyCount(eventId);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for waitlist count: " + eventId, e);
                    notifyCount(eventId);
                });
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

                    // Any change in WaitlistedEntrants should trigger a recomputation
                    // of the logical waitlist count, which also considers NonSelected
                    // and Selected+PENDING after selection.
                    queryWaitlistCount(eventId);
                });

        waitlistCountRegistrations.put(eventId, reg);
    }
}
