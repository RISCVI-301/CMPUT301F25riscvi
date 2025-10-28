package com.EventEase.data.firebase;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.*;
import com.EventEase.model.*;

import java.util.*;

public final class FirebaseDevGraph {
    public final AuthManager auth = new AuthManager() {
        @Override
        public String getUid() {
            return "";
        }
    };
    public final FirebaseEventRepository events;
    public final FirebaseWaitlistRepository waitlists;
    public final FirebaseProfileRepository profiles;
    public final FirebaseInvitationRepository invitations;

    public FirebaseDevGraph() {
        List<Event> seedEvents = Arrays.asList(
                new Event("e1","Swim Lessons","Kids group", futureHours(24), futureHours(26),20,"Pool A"),
                new Event("e2","Yoga","Adults", futureHours(48), futureHours(49),15,"Gym B")
        );
        events = new FirebaseEventRepository(seedEvents);
        waitlists = new FirebaseWaitlistRepository(events);
        profiles = new FirebaseProfileRepository(new Profile("demo-uid-123","Demo User","demo@example.com", null));
        invitations = new FirebaseInvitationRepository(Collections.singletonList(
                new Invitation("i1","e1","demo-uid-123", Invitation.Status.PENDING, new Date(), futureHours(72))
        ));
    }

    private static Date futureHours(int h) {
        return new Date(System.currentTimeMillis() + h * 3600_000L);
    }
}

