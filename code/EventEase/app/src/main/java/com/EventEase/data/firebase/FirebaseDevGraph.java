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

    public FirebaseDevGraph() {
        this.auth = new FirebaseAuthManager("demo-uid-123"); // <-- pass fallback UID

        List<Event> seedEvents = Arrays.asList(
                new Event("e1","Swim Lessons","Kids group", futureHours(24), futureHours(26),20,"Pool A"),
                new Event("e2","Yoga","Adults", futureHours(48), futureHours(49),15,"Gym B")
        );
        events = new FirebaseEventRepository(seedEvents);
        waitlists = new FirebaseWaitlistRepository(events);

        String uid = auth.getUid();
        profiles = new FirebaseProfileRepository(new Profile(uid,"Demo User","demo@example.com", null));
        invitations = new FirebaseInvitationRepository(Collections.singletonList(
                new Invitation("i1","e1", uid, Invitation.Status.PENDING, new Date(), futureHours(72))
        ));
    }

    private static Date futureHours(int h) {
        return new Date(System.currentTimeMillis() + h * 3_600_000L);
    }
}
