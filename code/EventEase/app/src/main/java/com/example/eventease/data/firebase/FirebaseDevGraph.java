package com.example.eventease.data.firebase;

import java.util.*;

/**
 * Dependency graph that provides shared repository instances.
 * Initializes all Firebase repositories and makes them available throughout the app.
 * This class follows the Dependency Injection pattern, providing a centralized location
 * for all repository instances. Repositories are initialized once and reused throughout the application lifecycle.
 */
public final class FirebaseDevGraph {
    /** Repository for event data operations. */
    public final FirebaseEventRepository events;
    /** Repository for waitlist operations. */
    public final FirebaseWaitlistRepository waitlists;
    /** Repository for user profile operations. */
    public final FirebaseProfileRepository profiles;
    /** Repository for invitation operations. */
    public final FirebaseInvitationRepository invitations;
    /** Repository for admitted event operations. */
    public final FirebaseAdmittedRepository admitted;

    /**
     * Constructs a new dependency graph and initializes all repositories.
     * Repositories are initialized with empty seed data as data is loaded from Firebase.
     */
    public FirebaseDevGraph() {
        // Initialize repositories with empty seed data, data loads from firebase
        events = new FirebaseEventRepository(new ArrayList<>());
        waitlists = new FirebaseWaitlistRepository(events);
        profiles = new FirebaseProfileRepository();
        admitted = new FirebaseAdmittedRepository(events);
        invitations = new FirebaseInvitationRepository(new ArrayList<>());
        invitations.setAdmittedRepository(admitted);
    }
}
