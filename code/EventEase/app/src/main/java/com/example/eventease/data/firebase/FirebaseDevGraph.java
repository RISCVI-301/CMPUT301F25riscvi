package com.example.eventease.data.firebase;

import com.example.eventease.auth.AuthManager;
import com.example.eventease.auth.FirebaseAuthManager;

import java.util.*;

/**
 * Dependency graph that provides shared repository instances.
 * Initializes all Firebase repositories and makes them available throughout the app.
 */
public final class FirebaseDevGraph {
    public final AuthManager auth;
    public final FirebaseEventRepository events;
    public final FirebaseWaitlistRepository waitlists;
    public final FirebaseProfileRepository profiles;
    public final FirebaseInvitationRepository invitations;
    public final FirebaseAdmittedRepository admitted;

    public FirebaseDevGraph() {
        this.auth = new FirebaseAuthManager();
        
        // Initialize repositories with empty seed data, datal loads from firebase
        events = new FirebaseEventRepository(new ArrayList<>());
        waitlists = new FirebaseWaitlistRepository(events);
        profiles = new FirebaseProfileRepository();
        admitted = new FirebaseAdmittedRepository(events);
        invitations = new FirebaseInvitationRepository(new ArrayList<>());
        invitations.setAdmittedRepository(admitted);
    }

}
