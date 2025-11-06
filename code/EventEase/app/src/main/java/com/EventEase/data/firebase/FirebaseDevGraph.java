package com.EventEase.data.firebase;

import com.EventEase.auth.AuthManager;
import com.EventEase.auth.FirebaseAuthManager;
import com.EventEase.data.*;
import com.EventEase.model.*;

import java.util.*;

public final class FirebaseDevGraph {
    public final AuthManager auth; // init in ctor
    public final FirebaseEventRepository events;
    public final FirebaseWaitlistRepository waitlists;
    public final FirebaseProfileRepository profiles;
    public final FirebaseInvitationRepository invitations;
    public final FirebaseAdmittedRepository admitted;

    public FirebaseDevGraph() {
        this.auth = new FirebaseAuthManager("demo-uid-123"); // <-- pass fallback UID
        
        // Initialize repositories with empty seed data - data will be loaded from Firestore
        events = new FirebaseEventRepository(new ArrayList<>());
        waitlists = new FirebaseWaitlistRepository(events);

        profiles = new FirebaseProfileRepository();
        
        // Initialize admitted repository
        admitted = new FirebaseAdmittedRepository(events);
        
        // Initialize invitations repository with empty seed data - data will be loaded from Firestore
        invitations = new FirebaseInvitationRepository(new ArrayList<>());
        
        // Link admitted repository to invitations
        invitations.setAdmittedRepository(admitted);
    }

}
