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

        String uid = auth.getUid();
        
        // Create seed events with the new Event structure
        Event swimEvent = new Event();
        swimEvent.id = "e1";
        swimEvent.title = "Swim Lessons";
        swimEvent.location = "Pool A";
        swimEvent.startsAtEpochMs = futureMillis(24);
        swimEvent.capacity = 20;
        swimEvent.organizerId = uid;
        swimEvent.createdAtEpochMs = System.currentTimeMillis();
        swimEvent.qrPayload = "event:e1";

        Event yogaEvent = new Event();
        yogaEvent.id = "e2";
        yogaEvent.title = "Yoga";
        yogaEvent.location = "Gym B";
        yogaEvent.startsAtEpochMs = futureMillis(48);
        yogaEvent.capacity = 15;
        yogaEvent.organizerId = uid;
        yogaEvent.createdAtEpochMs = System.currentTimeMillis();
        yogaEvent.qrPayload = "event:e2";

        List<Event> seedEvents = Arrays.asList(swimEvent, yogaEvent);
        events = new FirebaseEventRepository(seedEvents);
        waitlists = new FirebaseWaitlistRepository(events);

        profiles = new FirebaseProfileRepository(new Profile(uid,"Demo User","demo@example.com", null));
        invitations = new FirebaseInvitationRepository(Collections.singletonList(
                new Invitation("i1","e1", uid, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72)))
        ));
    }

    private static long futureMillis(int h) {
        return System.currentTimeMillis() + (h * 3_600_000L);
    }
}
