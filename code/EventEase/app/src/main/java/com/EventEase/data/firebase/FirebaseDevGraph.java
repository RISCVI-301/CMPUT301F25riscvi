package com.EventEase.data.firebase;

import com.EventEase.auth.AuthManager;
import com.EventEase.auth.FirebaseAuthManager;
import com.EventEase.data.*;
import com.EventEase.model.*;
import com.google.firebase.firestore.FirebaseFirestore;

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

        String uid = auth.getUid();
        
        // Create seed events with the new Event structure
        Event swimEvent = new Event();
        swimEvent.id = "e1";
        swimEvent.title = "Summer Pool Party";
        swimEvent.location = "Community Pool";
        swimEvent.startsAtEpochMs = futureMillis(24);
        swimEvent.capacity = 50;
        swimEvent.waitlistCount = 0; // Start at 0, will be incremented as users join
        swimEvent.waitlist = new ArrayList<>();
        swimEvent.admitted = new ArrayList<>();
        swimEvent.notes = "Join us for a fun-filled summer pool party! Bring your swimwear, sunscreen, and get ready for games, music, and refreshments. This event is perfect for families and friends looking to cool off and enjoy the summer sunshine.";
        swimEvent.guidelines = "• All ages welcome - children must be supervised by an adult\n• Bring your own towel and swimwear\n• No glass containers allowed in pool area\n• Pool rules and lifeguard instructions must be followed\n• Event is weather dependent - check for updates";
        swimEvent.posterUrl = "https://images.unsplash.com/photo-1519046904884-53103b34b206?w=400";
        swimEvent.organizerId = uid;
        swimEvent.createdAtEpochMs = System.currentTimeMillis();
        swimEvent.qrPayload = "event:e1";

        Event yogaEvent = new Event();
        yogaEvent.id = "e2";
        yogaEvent.title = "Sunset Yoga Session";
        yogaEvent.location = "Central Park";
        yogaEvent.startsAtEpochMs = futureMillis(48);
        yogaEvent.capacity = 30;
        yogaEvent.waitlistCount = 0; // Start at 0, will be incremented as users join
        yogaEvent.waitlist = new ArrayList<>();
        yogaEvent.admitted = new ArrayList<>();
        yogaEvent.notes = "Experience tranquility with our outdoor sunset yoga session. Suitable for all levels, this class focuses on mindfulness, breathing techniques, and gentle stretches. Please bring your own mat and water bottle.";
        yogaEvent.guidelines = "• Must be 18+ years old to participate\n• Bring your own yoga mat and water bottle\n• Wear comfortable athletic clothing\n• Arrive 10 minutes early for check-in\n• No photography during the session\n• Session will be cancelled if weather is poor";
        yogaEvent.posterUrl = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=400";
        yogaEvent.organizerId = uid;
        yogaEvent.createdAtEpochMs = System.currentTimeMillis();
        yogaEvent.qrPayload = "event:e2";

        Event concertEvent = new Event();
        concertEvent.id = "e3";
        concertEvent.title = "Live Jazz Concert";
        concertEvent.location = "Downtown Music Hall";
        concertEvent.startsAtEpochMs = futureMillis(72);
        concertEvent.capacity = 150;
        concertEvent.waitlistCount = 0; // Start at 0, will be incremented as users join
        concertEvent.waitlist = new ArrayList<>();
        concertEvent.admitted = new ArrayList<>();
        concertEvent.notes = "Enjoy an evening of smooth jazz with local talented musicians. The concert features a variety of jazz styles from bebop to contemporary fusion. Light refreshments will be available for purchase.";
        concertEvent.guidelines = "• Valid photo ID required at entrance\n• Doors open 30 minutes before showtime\n• No outside food or beverages allowed\n• Photography allowed but no flash or video recording\n• Reserved seating - tickets non-transferable\n• Late arrivals will be seated during intermission";
        concertEvent.posterUrl = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=400";
        concertEvent.organizerId = uid;
        concertEvent.createdAtEpochMs = System.currentTimeMillis();
        concertEvent.qrPayload = "event:e3";

        Event foodEvent = new Event();
        foodEvent.id = "e4";
        foodEvent.title = "Food Truck Festival";
        foodEvent.location = "City Plaza";
        foodEvent.startsAtEpochMs = futureMillis(96);
        foodEvent.capacity = 200;
        foodEvent.waitlistCount = 0; // Start at 0, will be incremented as users join
        foodEvent.waitlist = new ArrayList<>();
        foodEvent.admitted = new ArrayList<>();
        foodEvent.notes = "A culinary adventure awaits! Sample delicious food from over 20 local food trucks featuring cuisines from around the world. Live entertainment, kids activities, and family-friendly atmosphere. Don't miss this gastronomic celebration!";
        foodEvent.guidelines = "• Family-friendly event - all ages welcome\n• Cash and card payment accepted at all vendors\n• No outside food or alcohol permitted\n• Pets must be leashed at all times\n• Please dispose of trash in designated bins\n• Free parking available at nearby lots";
        foodEvent.posterUrl = "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=400";
        foodEvent.organizerId = uid;
        foodEvent.createdAtEpochMs = System.currentTimeMillis();
        foodEvent.qrPayload = "event:e4";

        Event workshopEvent = new Event();
        workshopEvent.id = "e5";
        workshopEvent.title = "Photography Workshop";
        workshopEvent.location = "Art Studio 101";
        workshopEvent.startsAtEpochMs = futureMillis(120);
        workshopEvent.capacity = 25;
        workshopEvent.waitlistCount = 0; // Start at 0, will be incremented as users join
        workshopEvent.waitlist = new ArrayList<>();
        workshopEvent.admitted = new ArrayList<>();
        workshopEvent.notes = "Learn professional photography techniques in this hands-on workshop. Topics include composition, lighting, and post-processing. Suitable for beginners and intermediate photographers. Please bring your own camera (DSLR or mirrorless preferred).";
        workshopEvent.guidelines = "• Must be 16+ years old to attend\n• Bring your own camera (DSLR, mirrorless, or advanced compact)\n• Laptop optional for editing portion\n• Limited to 25 participants for personalized instruction\n• Refreshments will be provided\n• Participants will receive a certificate upon completion";
        workshopEvent.posterUrl = "https://images.unsplash.com/photo-1542038784456-1ea8e935640e?w=400";
        workshopEvent.organizerId = uid;
        workshopEvent.createdAtEpochMs = System.currentTimeMillis();
        workshopEvent.qrPayload = "event:e5";

        List<Event> seedEvents = Arrays.asList(swimEvent, yogaEvent, concertEvent, foodEvent, workshopEvent);
        events = new FirebaseEventRepository(seedEvents);
        waitlists = new FirebaseWaitlistRepository(events);

        // Write events to Firestore
        writeEventsToFirestore(seedEvents);

        profiles = new FirebaseProfileRepository(new Profile(uid,"Demo User","demo@example.com", null));
        
        // Initialize admitted repository
        admitted = new FirebaseAdmittedRepository(events);
        
        // Create invitations
        List<Invitation> seedInvitations = new ArrayList<>();
        
        // Demo user invitation for first event (Summer Pool Party)
        seedInvitations.add(new Invitation("i1","e1", uid, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        
        // Firebase user (gamestari734@gmail.com) invitations for events e2, e3, e4, e5 (NOT e1)
        String firebaseUserId = "gN8jla0HwJdMT45SMzvHsNkiLPT2";
        seedInvitations.add(new Invitation("i2","e2", firebaseUserId, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        seedInvitations.add(new Invitation("i3","e3", firebaseUserId, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        seedInvitations.add(new Invitation("i4","e4", firebaseUserId, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        seedInvitations.add(new Invitation("i5","e5", firebaseUserId, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        
        // Add invitation for UID pE10ntPBfAeplK3UnYQOfufDVU32 to Summer Pool Party (e1) for testing
        String testUserId = "pE10ntPBfAeplK3UnYQOfufDVU32";
        seedInvitations.add(new Invitation("i6","e1", testUserId, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        
        // Add invitation for UID gCvsIC90AkdCZWFiEEnWZN8CR3C3 to Sunset Yoga Session (e2) for waitlist testing
        String waitlistUserId = "gCvsIC90AkdCZWFiEEnWZN8CR3C3";
        seedInvitations.add(new Invitation("i7","e2", waitlistUserId, Invitation.Status.PENDING, new Date(), new Date(futureMillis(72))));
        
        invitations = new FirebaseInvitationRepository(seedInvitations);
        
        // Link admitted repository to invitations
        invitations.setAdmittedRepository(admitted);
        
        // Write invitations to Firestore
        writeInvitationsToFirestore(seedInvitations);
    }

    private void writeEventsToFirestore(List<Event> events) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        for (Event event : events) {
            db.collection("events")
                .document(event.getId())
                .set(event.toMap())
                .addOnSuccessListener(aVoid -> {
                    // Event written successfully
                })
                .addOnFailureListener(e -> {
                    // Handle error
                    e.printStackTrace();
                });
        }
    }

    private void writeInvitationsToFirestore(List<Invitation> invitations) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        for (Invitation invitation : invitations) {
            Map<String, Object> invData = new HashMap<>();
            invData.put("id", invitation.getId());
            invData.put("eventId", invitation.getEventId());
            invData.put("uid", invitation.getUid());
            invData.put("status", invitation.getStatus().toString());
            invData.put("issuedAt", invitation.getIssuedAt() != null ? invitation.getIssuedAt().getTime() : System.currentTimeMillis());
            invData.put("expiresAt", invitation.getExpiresAt() != null ? invitation.getExpiresAt().getTime() : null);
            
            db.collection("invitations")
                .document(invitation.getId())
                .set(invData)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("FirebaseDevGraph", "Invitation " + invitation.getId() + " written successfully");
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("FirebaseDevGraph", "Failed to write invitation " + invitation.getId(), e);
                    e.printStackTrace();
                });
        }
    }

    private static long futureMillis(int h) {
        return System.currentTimeMillis() + (h * 3_600_000L);
    }
}
