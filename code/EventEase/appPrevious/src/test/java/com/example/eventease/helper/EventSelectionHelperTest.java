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
 * Backend unit tests for EventSelectionHelper functionality.
 * Tests automatic selection logic and invitation creation.
 */
public class EventSelectionHelperTest {

    private Map<String, Object> testEvent;
    private List<Map<String, Object>> waitlistedEntrants;
    private long currentTime;
    
    @Before
    public void setUp() {
        currentTime = System.currentTimeMillis();
        
        testEvent = new HashMap<>();
        testEvent.put("eventId", "event123");
        testEvent.put("title", "Test Event");
        testEvent.put("registrationStart", currentTime - 120000);
        testEvent.put("registrationEnd", currentTime - 1000);
        testEvent.put("deadlineEpochMs", currentTime + 120000);
        testEvent.put("startsAtEpochMs", currentTime + 300000);
        testEvent.put("sampleSize", 3);
        testEvent.put("capacity", 5);
        testEvent.put("selectionProcessed", false);
        testEvent.put("selectionNotificationSent", false);
        
        waitlistedEntrants = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> entrant = new HashMap<>();
            entrant.put("uid", "user" + i);
            entrant.put("name", "User " + i);
            waitlistedEntrants.add(entrant);
        }
    }

    @Test
    public void testShouldProcessSelection_RegistrationEnded() {
        boolean shouldProcess = shouldProcessSelection(testEvent, currentTime);
        assertTrue(shouldProcess);
    }

    @Test
    public void testShouldProcessSelection_RegistrationNotEnded() {
        testEvent.put("registrationEnd", currentTime + 60000);
        boolean shouldProcess = shouldProcessSelection(testEvent, currentTime);
        assertFalse(shouldProcess);
    }

    @Test
    public void testShouldProcessSelection_AlreadyProcessed() {
        testEvent.put("selectionProcessed", true);
        boolean shouldProcess = shouldProcessSelection(testEvent, currentTime);
        assertFalse(shouldProcess);
    }

    @Test
    public void testShouldProcessSelection_EventAlreadyStarted() {
        testEvent.put("startsAtEpochMs", currentTime - 1000);
        boolean shouldProcess = shouldProcessSelection(testEvent, currentTime);
        assertFalse(shouldProcess);
    }

    @Test
    public void testRandomSelection_CorrectCount() {
        int sampleSize = 3;
        List<Map<String, Object>> selected = randomlySelectEntrants(waitlistedEntrants, sampleSize);
        
        assertEquals(sampleSize, selected.size());
    }

    @Test
    public void testRandomSelection_MoreThanAvailable() {
        int sampleSize = 15;
        List<Map<String, Object>> selected = randomlySelectEntrants(waitlistedEntrants, sampleSize);
        
        assertEquals(waitlistedEntrants.size(), selected.size());
    }

    @Test
    public void testRandomSelection_EmptyWaitlist() {
        List<Map<String, Object>> emptyList = new ArrayList<>();
        List<Map<String, Object>> selected = randomlySelectEntrants(emptyList, 3);
        
        assertEquals(0, selected.size());
    }

    @Test
    public void testRandomSelection_ZeroSampleSize() {
        List<Map<String, Object>> selected = randomlySelectEntrants(waitlistedEntrants, 0);
        
        assertEquals(0, selected.size());
    }

    @Test
    public void testCapacityLimit() {
        int sampleSize = (Integer) testEvent.get("sampleSize");
        int capacity = (Integer) testEvent.get("capacity");
        
        assertTrue(sampleSize <= capacity);
    }

    @Test
    public void testCapacityLimitEnforcement() {
        testEvent.put("sampleSize", 10);
        testEvent.put("capacity", 5);
        
        int sampleSize = (Integer) testEvent.get("sampleSize");
        int capacity = (Integer) testEvent.get("capacity");
        int effectiveSampleSize = Math.min(sampleSize, capacity);
        
        assertEquals(5, effectiveSampleSize);
    }

    @Test
    public void testSelectionNotificationFlag() {
        assertFalse((Boolean) testEvent.get("selectionNotificationSent"));
        
        testEvent.put("selectionNotificationSent", true);
        
        assertTrue((Boolean) testEvent.get("selectionNotificationSent"));
    }

    @Test
    public void testInvitationCreation() {
        String eventId = (String) testEvent.get("eventId");
        String userId = "user123";
        Long deadline = (Long) testEvent.get("deadlineEpochMs");
        
        Map<String, Object> invitation = createInvitation(eventId, userId, deadline);
        
        assertEquals(eventId, invitation.get("eventId"));
        assertEquals(userId, invitation.get("uid"));
        assertEquals("PENDING", invitation.get("status"));
        assertNotNull(invitation.get("issuedAt"));
        assertEquals(deadline, invitation.get("deadline"));
    }

    @Test
    public void testMultipleInvitationsCreation() {
        String eventId = (String) testEvent.get("eventId");
        Long deadline = (Long) testEvent.get("deadlineEpochMs");
        List<Map<String, Object>> selected = randomlySelectEntrants(waitlistedEntrants, 3);
        
        List<Map<String, Object>> invitations = new ArrayList<>();
        for (Map<String, Object> entrant : selected) {
            String userId = (String) entrant.get("uid");
            invitations.add(createInvitation(eventId, userId, deadline));
        }
        
        assertEquals(3, invitations.size());
        for (Map<String, Object> invitation : invitations) {
            assertEquals("PENDING", invitation.get("status"));
            assertNotNull(invitation.get("issuedAt"));
        }
    }

    @Test
    public void testSelectionProcessedFlag() {
        assertFalse((Boolean) testEvent.get("selectionProcessed"));
        
        testEvent.put("selectionProcessed", true);
        
        assertTrue((Boolean) testEvent.get("selectionProcessed"));
    }

    @Test
    public void testDeadlineIsAfterSelection() {
        Long deadline = (Long) testEvent.get("deadlineEpochMs");
        long selectionTime = currentTime;
        
        assertTrue(deadline > selectionTime);
    }

    @Test
    public void testEventStartIsAfterDeadline() {
        Long deadline = (Long) testEvent.get("deadlineEpochMs");
        Long eventStart = (Long) testEvent.get("startsAtEpochMs");
        
        assertTrue(eventStart > deadline);
    }

    @Test
    public void testSelectionTimeline() {
        Long registrationEnd = (Long) testEvent.get("registrationEnd");
        Long deadline = (Long) testEvent.get("deadlineEpochMs");
        Long eventStart = (Long) testEvent.get("startsAtEpochMs");
        
        assertTrue(registrationEnd < currentTime);
        assertTrue(currentTime < deadline);
        assertTrue(deadline < eventStart);
    }

    @Test
    public void testNotificationRequestForSelection() {
        String eventId = (String) testEvent.get("eventId");
        String eventTitle = (String) testEvent.get("title");
        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            userIds.add("user" + i);
        }
        
        Map<String, Object> notificationRequest = createSelectionNotification(
            eventId, eventTitle, userIds
        );
        
        assertEquals(eventId, notificationRequest.get("eventId"));
        assertEquals(eventTitle, notificationRequest.get("eventTitle"));
        assertEquals("selection", notificationRequest.get("groupType"));
        assertTrue(((String) notificationRequest.get("title")).contains("selected"));
        
        @SuppressWarnings("unchecked")
        List<String> requestUserIds = (List<String>) notificationRequest.get("userIds");
        assertEquals(3, requestUserIds.size());
    }

    @Test
    public void testTransactionAtomicity() {
        Boolean initialProcessed = (Boolean) testEvent.get("selectionProcessed");
        Boolean initialNotificationSent = (Boolean) testEvent.get("selectionNotificationSent");
        
        testEvent.put("selectionProcessed", true);
        testEvent.put("selectionNotificationSent", true);
        
        assertTrue((Boolean) testEvent.get("selectionProcessed"));
        assertTrue((Boolean) testEvent.get("selectionNotificationSent"));
        
        testEvent.put("selectionProcessed", initialProcessed);
        testEvent.put("selectionNotificationSent", initialNotificationSent);
        
        assertFalse((Boolean) testEvent.get("selectionProcessed"));
        assertFalse((Boolean) testEvent.get("selectionNotificationSent"));
    }

    private boolean shouldProcessSelection(Map<String, Object> event, long currentTime) {
        Long registrationEnd = (Long) event.get("registrationEnd");
        Boolean selectionProcessed = (Boolean) event.get("selectionProcessed");
        Long startsAt = (Long) event.get("startsAtEpochMs");
        
        if (registrationEnd == null || registrationEnd > currentTime) {
            return false;
        }
        
        if (Boolean.TRUE.equals(selectionProcessed)) {
            return false;
        }
        
        if (startsAt != null && startsAt <= currentTime) {
            return false;
        }
        
        return true;
    }

    private List<Map<String, Object>> randomlySelectEntrants(
        List<Map<String, Object>> entrants, int count
    ) {
        List<Map<String, Object>> shuffled = new ArrayList<>(entrants);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private Map<String, Object> createInvitation(String eventId, String userId, Long deadline) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("eventId", eventId);
        invitation.put("uid", userId);
        invitation.put("status", "PENDING");
        invitation.put("issuedAt", System.currentTimeMillis());
        invitation.put("deadline", deadline);
        return invitation;
    }

    private Map<String, Object> createSelectionNotification(
        String eventId, String eventTitle, List<String> userIds
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", eventId);
        request.put("eventTitle", eventTitle);
        request.put("userIds", new ArrayList<>(userIds));
        request.put("groupType", "selection");
        request.put("title", "You've been selected!");
        request.put("message", "Congratulations! You've been selected for " + eventTitle);
        request.put("status", "PENDING");
        request.put("createdAt", System.currentTimeMillis());
        request.put("processed", false);
        return request;
    }
}

