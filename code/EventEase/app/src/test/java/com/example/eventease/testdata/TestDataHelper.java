package com.example.eventease.testdata;

import com.example.eventease.model.Event;
import com.example.eventease.model.Profile;
import com.example.eventease.model.Invitation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Helper class for creating test data for unit tests.
 * Provides realistic test data for events, profiles, and invitations.
 */
public class TestDataHelper {

    /**
     * Creates a realistic test event.
     * 
     * @param organizerId the organizer ID for the event
     * @return a test Event instance
     */
    public static Event createTestEvent(String organizerId) {
        Event event = Event.newDraft(organizerId);
        event.setTitle("Summer Music Festival 2025");
        event.setLocation("Central Park, New York");
        event.setCapacity(500);
        event.setRegistrationStart(System.currentTimeMillis() + 86400000L); // Tomorrow
        event.setRegistrationEnd(System.currentTimeMillis() + 7 * 86400000L); // 7 days from now
        event.setDeadlineEpochMs(System.currentTimeMillis() + 30 * 86400000L); // 30 days from now
        event.setStartsAtEpochMs(System.currentTimeMillis() + 60 * 86400000L); // 60 days from now
        event.setNotes("Join us for an amazing summer music festival featuring top artists!");
        event.setGuidelines("Please arrive 30 minutes early. No outside food or beverages allowed.");
        event.setPosterUrl("https://example.com/posters/summer-festival-2025.jpg");
        event.setQrPayload("event:" + event.getId());
        return event;
    }

    /**
     * Creates a realistic test profile.
     * 
     * @param uid the user ID
     * @return a test Profile instance
     */
    public static Profile createTestProfile(String uid) {
        return new Profile(
            uid,
            "John Doe",
            "john.doe@example.com",
            "https://example.com/profiles/john-doe.jpg"
        );
    }

    /**
     * Creates a test profile with phone number.
     * 
     * @param uid the user ID
     * @return a test Profile instance with phone number
     */
    public static Profile createTestProfileWithPhone(String uid) {
        Profile profile = createTestProfile(uid);
        profile.setPhoneNumber("+1-555-123-4567");
        return profile;
    }

    /**
     * Creates a realistic test invitation.
     * 
     * @param invitationId the invitation ID
     * @param eventId the event ID
     * @param uid the user ID
     * @return a test Invitation instance
     */
    public static Invitation createTestInvitation(String invitationId, String eventId, String uid) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + 7 * 24 * 60 * 60 * 1000L); // 7 days later
        return new Invitation(
            invitationId,
            eventId,
            uid,
            Invitation.Status.PENDING,
            issuedAt,
            expiresAt
        );
    }

    /**
     * Creates a list of test user IDs.
     * 
     * @param count the number of user IDs to create
     * @return a list of test user IDs
     */
    public static List<String> createTestUserIds(int count) {
        List<String> userIds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            userIds.add("user" + String.format("%03d", i));
        }
        return userIds;
    }

    /**
     * Creates a test event with waitlisted users.
     * 
     * @param organizerId the organizer ID
     * @param waitlistCount the number of users on the waitlist
     * @return a test Event instance with waitlisted users
     */
    public static Event createTestEventWithWaitlist(String organizerId, int waitlistCount) {
        Event event = createTestEvent(organizerId);
        List<String> waitlist = createTestUserIds(waitlistCount);
        event.setWaitlist(waitlist);
        return event;
    }

    /**
     * Creates a test event with admitted users.
     * 
     * @param organizerId the organizer ID
     * @param admittedCount the number of admitted users
     * @return a test Event instance with admitted users
     */
    public static Event createTestEventWithAdmitted(String organizerId, int admittedCount) {
        Event event = createTestEvent(organizerId);
        List<String> admitted = createTestUserIds(admittedCount);
        event.setAdmitted(admitted);
        return event;
    }
}

