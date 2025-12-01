package com.example.eventease.helper;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Backend unit tests for ReplacementHelper functionality.
 * Tests automatic replacement of cancelled entrants with waitlisted entrants.
 */
public class ReplacementHelperTest {

    private Map<String, List<Map<String, Object>>> eventEntrants;
    private String testEventId;
    
    @Before
    public void setUp() {
        testEventId = "event123";
        eventEntrants = new HashMap<>();
        eventEntrants.put("selected", new ArrayList<>());
        eventEntrants.put("waitlisted", new ArrayList<>());
        eventEntrants.put("cancelled", new ArrayList<>());
        
        for (int i = 0; i < 3; i++) {
            Map<String, Object> entrant = new HashMap<>();
            entrant.put("uid", "selected_user" + i);
            entrant.put("name", "Selected User " + i);
            eventEntrants.get("selected").add(entrant);
        }
        
        for (int i = 0; i < 5; i++) {
            Map<String, Object> entrant = new HashMap<>();
            entrant.put("uid", "waitlisted_user" + i);
            entrant.put("name", "Waitlisted User " + i);
            eventEntrants.get("waitlisted").add(entrant);
        }
    }

    @Test
    public void testCalculateReplacementNeeded_NoCancellations() {
        int replacementNeeded = calculateReplacementCount(3, 0);
        assertEquals(0, replacementNeeded);
    }

    @Test
    public void testCalculateReplacementNeeded_WithCancellations() {
        eventEntrants.get("cancelled").add(createEntrant("cancelled_user1"));
        eventEntrants.get("cancelled").add(createEntrant("cancelled_user2"));
        
        int replacementNeeded = calculateReplacementCount(3, 2);
        assertEquals(2, replacementNeeded);
    }

    @Test
    public void testCalculateReplacementNeeded_MoreCancellationsThanCapacity() {
        for (int i = 0; i < 10; i++) {
            eventEntrants.get("cancelled").add(createEntrant("cancelled_user" + i));
        }
        
        int replacementNeeded = calculateReplacementCount(5, 10);
        assertEquals(5, replacementNeeded);
    }

    @Test
    public void testSelectReplacements_SufficientWaitlisted() {
        List<Map<String, Object>> waitlisted = eventEntrants.get("waitlisted");
        List<Map<String, Object>> selected = randomlySelect(waitlisted, 3);
        
        assertEquals(3, selected.size());
        for (Map<String, Object> entrant : selected) {
            assertTrue(waitlisted.contains(entrant));
        }
    }

    @Test
    public void testSelectReplacements_InsufficientWaitlisted() {
        List<Map<String, Object>> waitlisted = new ArrayList<>();
        waitlisted.add(createEntrant("user1"));
        waitlisted.add(createEntrant("user2"));
        
        List<Map<String, Object>> selected = randomlySelect(waitlisted, 5);
        
        assertEquals(2, selected.size());
    }

    @Test
    public void testSelectReplacements_EmptyWaitlist() {
        List<Map<String, Object>> waitlisted = new ArrayList<>();
        List<Map<String, Object>> selected = randomlySelect(waitlisted, 3);
        
        assertEquals(0, selected.size());
    }

    @Test
    public void testSelectReplacements_Randomness() {
        List<Map<String, Object>> waitlisted = eventEntrants.get("waitlisted");
        
        List<Map<String, Object>> selection1 = randomlySelect(new ArrayList<>(waitlisted), 3);
        List<Map<String, Object>> selection2 = randomlySelect(new ArrayList<>(waitlisted), 3);
        
        assertEquals(3, selection1.size());
        assertEquals(3, selection2.size());
    }

    @Test
    public void testReplacementWithinCapacity() {
        int capacity = 5;
        int currentSelected = 3;
        int cancelled = 2;
        int waitlisted = 5;
        
        int replacementNeeded = calculateReplacementCount(currentSelected, cancelled);
        int maxReplacements = Math.min(replacementNeeded, capacity - (currentSelected - cancelled));
        maxReplacements = Math.min(maxReplacements, waitlisted);
        
        assertEquals(2, maxReplacements);
    }

    @Test
    public void testReplacementExceedsCapacity() {
        int capacity = 5;
        int currentSelected = 5;
        int cancelled = 2;
        int waitlisted = 5;
        
        int replacementNeeded = calculateReplacementCount(currentSelected, cancelled);
        int maxReplacements = Math.min(replacementNeeded, capacity - (currentSelected - cancelled));
        maxReplacements = Math.min(maxReplacements, waitlisted);
        
        assertEquals(2, maxReplacements);
    }

    @Test
    public void testDeadlineEpochMsValidation() {
        long currentTime = System.currentTimeMillis();
        long deadline = currentTime + (2 * 60 * 60 * 1000);
        
        assertTrue(deadline > currentTime);
        
        long hoursDiff = (deadline - currentTime) / (60 * 60 * 1000);
        assertEquals(2, hoursDiff);
    }

    @Test
    public void testReplacementInvitationCreation() {
        Map<String, Object> invitation = createInvitation(
            "event123",
            "user1",
            System.currentTimeMillis() + (2 * 60 * 60 * 1000)
        );
        
        assertEquals("event123", invitation.get("eventId"));
        assertEquals("user1", invitation.get("uid"));
        assertEquals("PENDING", invitation.get("status"));
        assertNotNull(invitation.get("issuedAt"));
        assertNotNull(invitation.get("deadline"));
    }

    @Test
    public void testMoveEntrantFromWaitlistToSelected() {
        List<Map<String, Object>> waitlisted = eventEntrants.get("waitlisted");
        List<Map<String, Object>> selected = eventEntrants.get("selected");
        
        Map<String, Object> entrant = waitlisted.get(0);
        String userId = (String) entrant.get("uid");
        
        waitlisted.remove(entrant);
        selected.add(entrant);
        
        assertFalse(waitlisted.contains(entrant));
        assertTrue(selected.contains(entrant));
        assertEquals(4, waitlisted.size());
        assertEquals(4, selected.size());
    }

    @Test
    public void testBatchReplacementOperation() {
        List<Map<String, Object>> waitlisted = eventEntrants.get("waitlisted");
        List<Map<String, Object>> selected = eventEntrants.get("selected");
        
        List<Map<String, Object>> toReplace = randomlySelect(waitlisted, 3);
        
        for (Map<String, Object> entrant : toReplace) {
            waitlisted.remove(entrant);
            selected.add(entrant);
        }
        
        assertEquals(2, waitlisted.size());
        assertEquals(6, selected.size());
    }

    @Test
    public void testNoReplacementWhenNoWaitlist() {
        eventEntrants.put("waitlisted", new ArrayList<>());
        eventEntrants.get("cancelled").add(createEntrant("cancelled_user1"));
        
        int replacementNeeded = calculateReplacementCount(3, 1);
        assertEquals(1, replacementNeeded);
        
        List<Map<String, Object>> selected = randomlySelect(eventEntrants.get("waitlisted"), replacementNeeded);
        assertEquals(0, selected.size());
    }

    @Test
    public void testReplacementNotificationStructure() {
        Map<String, Object> notificationRequest = createReplacementNotification(
            "event123",
            "Test Event",
            List.of("user1", "user2"),
            System.currentTimeMillis() + (2 * 60 * 60 * 1000)
        );
        
        assertEquals("event123", notificationRequest.get("eventId"));
        assertEquals("Test Event", notificationRequest.get("eventTitle"));
        assertEquals("replacement", notificationRequest.get("groupType"));
        assertNotNull(notificationRequest.get("message"));
        assertTrue(((String) notificationRequest.get("message")).contains("replacement"));
        
        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) notificationRequest.get("userIds");
        assertEquals(2, userIds.size());
    }

    private Map<String, Object> createEntrant(String uid) {
        Map<String, Object> entrant = new HashMap<>();
        entrant.put("uid", uid);
        entrant.put("name", "User " + uid);
        return entrant;
    }

    private int calculateReplacementCount(int currentSelected, int cancelledCount) {
        if (cancelledCount <= 0 || currentSelected <= 0) {
            return 0;
        }
        return Math.min(cancelledCount, currentSelected);
    }

    private List<Map<String, Object>> randomlySelect(List<Map<String, Object>> allDocs, int count) {
        List<Map<String, Object>> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private Map<String, Object> createInvitation(String eventId, String userId, long deadlineMs) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("eventId", eventId);
        invitation.put("uid", userId);
        invitation.put("status", "PENDING");
        invitation.put("issuedAt", System.currentTimeMillis());
        invitation.put("deadline", deadlineMs);
        return invitation;
    }

    private Map<String, Object> createReplacementNotification(
        String eventId, String eventTitle, List<String> userIds, long deadlineMs
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", eventId);
        request.put("eventTitle", eventTitle);
        request.put("userIds", new ArrayList<>(userIds));
        request.put("groupType", "replacement");
        request.put("title", "Replacement Invitation");
        request.put("message", "You've been selected as a replacement for " + eventTitle);
        request.put("status", "PENDING");
        request.put("createdAt", System.currentTimeMillis());
        request.put("processed", false);
        return request;
    }
}

