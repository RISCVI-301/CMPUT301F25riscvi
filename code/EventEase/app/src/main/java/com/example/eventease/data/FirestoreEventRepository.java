package com.example.eventease.data;

import android.util.Log;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

public class FirestoreEventRepository implements EventRepository {

    private static final String TAG = "FirestoreEventRepo";

    private final FirebaseFirestore db;

    public FirestoreEventRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public Task<List<Event>> getOpenEvents(Date now) {
        return db.collection("events")
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() :
                                new IllegalStateException("Failed to load events");
                    }

                    QuerySnapshot snapshot = task.getResult();
                    List<Event> events = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Event event = Event.fromMap(doc.getData());
                            if (event == null) continue;
                            if (event.id == null) {
                                event.id = doc.getId();
                            }
                            if (now == null || event.getStartAt() == null || event.getStartAt().after(now)) {
                                events.add(event);
                            }
                        }
                    }

                    events.sort(Comparator.comparing(Event::getStartAt,
                            Comparator.nullsLast(Comparator.naturalOrder())));
                    return Collections.unmodifiableList(events);
                });
    }

    @Override
    public Task<Event> getEvent(String eventId) {
        return db.collection("events")
                .document(eventId)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() :
                                new IllegalStateException("Failed to load event " + eventId);
                    }

                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        throw new NoSuchElementException("Event not found: " + eventId);
                    }

                    Event event = Event.fromMap(doc.getData());
                    if (event == null) {
                        throw new IllegalStateException("Failed to parse event: " + eventId);
                    }
                    if (event.id == null) {
                        event.id = doc.getId();
                    }
                    return event;
                });
    }

    @Override
    public ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration =
                db.collection("events")
                        .document(eventId)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                Log.w(TAG, "Waitlist listener failed for " + eventId, error);
                                return;
                            }

                            if (snapshot == null || !snapshot.exists()) {
                                listener.onChanged(0);
                                return;
                            }

                            Long storedCount = snapshot.getLong("waitlistCount");
                            if (storedCount != null) {
                                listener.onChanged(storedCount.intValue());
                                return;
                            }

                            Object waitlist = snapshot.get("waitlist");
                            if (waitlist instanceof List) {
                                listener.onChanged(((List<?>) waitlist).size());
                                return;
                            }

                            snapshot.getReference()
                                    .collection("WaitlistedEntrants")
                                    .get()
                                    .addOnSuccessListener(subSnap -> listener.onChanged(subSnap.size()))
                                    .addOnFailureListener(fetchError -> Log.w(TAG,
                                            "Failed to read WaitlistedEntrants for " + eventId, fetchError));
                        });

        return () -> {
            if (registration != null) {
                registration.remove();
            }
        };
    }

    @Override
    public Task<Void> create(Event event) {
        if (event == null || event.id == null) {
            return com.google.android.gms.tasks.Tasks.forException(
                    new IllegalArgumentException("Event must have an ID"));
        }
        return db.collection("events").document(event.id).set(event.toMap());
    }
}