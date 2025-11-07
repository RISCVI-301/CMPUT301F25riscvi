package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirestoreEventRepository implements EventRepository {
    private final FirebaseFirestore db;

    public FirestoreEventRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public Task<Void> create(Event event) {
        return db.collection("events").document(event.id).set(event.toMap());
    }
}