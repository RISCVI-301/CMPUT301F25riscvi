package com.example.eventease.data;

import com.google.firebase.firestore.FirebaseFirestore;

public final class RepositoryProvider {
    private static EventRepository override;

    private RepositoryProvider() {}

    public static void setEventRepositoryForTesting(EventRepository repo) {
        override = repo;
    }

    public static EventRepository events() {
        if (override != null) return override;
        return new FirestoreEventRepository(FirebaseFirestore.getInstance());
    }
}