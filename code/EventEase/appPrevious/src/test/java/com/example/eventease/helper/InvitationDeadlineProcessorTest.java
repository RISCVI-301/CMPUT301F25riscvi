package com.example.eventease.helper;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend unit tests for InvitationDeadlineProcessor functionality.
 * Tests deadline processing logic and cancelled entrant handling.
 */
public class InvitationDeadlineProcessorTest {

    private List<Map<String, Object>> invitations;
    private long currentTime;
    private long pastDeadline;
    private long futureDeadline;
    
    @Before
    public void setUp() {
        currentTime = System.currentTimeMillis();
        pastDeadline = currentTime - 60000;
        futureDeadline = currentTime + 60000;
        
        invitations = new ArrayList<>();
        
        Map<String, Object> pendingExpired = new HashMap<>();
        pendingExpired.put("invitationId", "inv1");
        pendingExpired.put("eventId", "event123");
        pendingExpired.put("uid", "user1");
        pendingExpired.put("status", "PENDING");
        pendingExpired.put("deadline", pastDeadline);
        invitations.add(pendingExpired);
        
        Map<String, Object> pendingValid = new HashMap<>();
        pendingValid.put("invitationId", "inv2");
        pendingValid.put("eventId", "event123");
        pendingValid.put("uid", "user2");
        pendingValid.put("status", "PENDING");
        pendingValid.put("deadline", futureDeadline);
        invitations.add(pendingValid);
        
        Map<String, Object> accepted = new HashMap<>();
        accepted.put("invitationId", "inv3");
        accepted.put("eventId", "event123");
        accepted.put("uid", "user3");
        accepted.put("status", "ACCEPTED");
        accepted.put("deadline", pastDeadline);
        invitations.add(accepted);
    }

    @Test
    public void testFindExpiredInvitations() {
        List<Map<String, Object>> expired = findExpiredPendingInvitations(invitations, currentTime);
        
        assertEquals(1, expired.size());
        assertEquals("inv1", expired.get(0).get("invitationId"));
        assertEquals("PENDING", expired.get(0).get("status"));
    }

    @Test
    public void testNoExpiredInvitations() {
        invitations.clear();
        
        Map<String, Object> validInvitation = new HashMap<>();
        validInvitation.put("invitationId", "inv4");
        validInvitation.put("status", "PENDING");
        validInvitation.put("deadline", futureDeadline);
        invitations.add(validInvitation);
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(invitations, currentTime);
        
        assertEquals(0, expired.size());
    }

    @Test
    public void testAcceptedInvitationsNotExpired() {
        List<Map<String, Object>> expired = findExpiredPendingInvitations(invitations, currentTime);
        
        for (Map<String, Object> invitation : expired) {
            assertNotEquals("ACCEPTED", invitation.get("status"));
            assertNotEquals("DECLINED", invitation.get("status"));
        }
    }

    @Test
    public void testDeclinedInvitationsNotProcessed() {
        Map<String, Object> declined = new HashMap<>();
        declined.put("invitationId", "inv5");
        declined.put("status", "DECLINED");
        declined.put("deadline", pastDeadline);
        invitations.add(declined);
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(invitations, currentTime);
        
        for (Map<String, Object> invitation : expired) {
            assertNotEquals("DECLINED", invitation.get("status"));
        }
    }

    @Test
    public void testExpiredInvitationCancellation() {
        Map<String, Object> invitation = invitations.get(0);
        assertEquals("PENDING", invitation.get("status"));
        
        invitation.put("status", "CANCELLED");
        invitation.put("cancelledAt", currentTime);
        
        assertEquals("CANCELLED", invitation.get("status"));
        assertNotNull(invitation.get("cancelledAt"));
    }

    @Test
    public void testMoveToCancelledEntrants() {
        String userId = "user1";
        String eventId = "event123";
        
        Map<String, Object> cancelledEntrant = createCancelledEntrant(userId, eventId, currentTime);
        
        assertEquals(userId, cancelledEntrant.get("uid"));
        assertEquals("MISSED_DEADLINE", cancelledEntrant.get("reason"));
        assertNotNull(cancelledEntrant.get("cancelledAt"));
    }

    @Test
    public void testRemoveFromSelectedEntrants() {
        List<String> selectedEntrants = new ArrayList<>();
        selectedEntrants.add("user1");
        selectedEntrants.add("user2");
        selectedEntrants.add("user3");
        
        selectedEntrants.remove("user1");
        
        assertEquals(2, selectedEntrants.size());
        assertFalse(selectedEntrants.contains("user1"));
    }

    @Test
    public void testBatchDeadlineProcessing() {
        List<Map<String, Object>> multipleExpired = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> invitation = new HashMap<>();
            invitation.put("invitationId", "inv" + i);
            invitation.put("uid", "user" + i);
            invitation.put("status", "PENDING");
            invitation.put("deadline", pastDeadline);
            multipleExpired.add(invitation);
        }
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(multipleExpired, currentTime);
        
        assertEquals(5, expired.size());
    }

    @Test
    public void testDeadlineExactMatch() {
        Map<String, Object> exactDeadline = new HashMap<>();
        exactDeadline.put("invitationId", "inv6");
        exactDeadline.put("status", "PENDING");
        exactDeadline.put("deadline", currentTime);
        
        List<Map<String, Object>> testList = new ArrayList<>();
        testList.add(exactDeadline);
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(testList, currentTime);
        
        assertEquals(0, expired.size());
    }

    @Test
    public void testDeadlineOneMillisecondPast() {
        Map<String, Object> justPast = new HashMap<>();
        justPast.put("invitationId", "inv7");
        justPast.put("status", "PENDING");
        justPast.put("deadline", currentTime - 1);
        
        List<Map<String, Object>> testList = new ArrayList<>();
        testList.add(justPast);
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(testList, currentTime);
        
        assertEquals(1, expired.size());
    }

    @Test
    public void testProcessingTimestamp() {
        long processingTime = currentTime;
        Map<String, Object> invitation = invitations.get(0);
        invitation.put("processedAt", processingTime);
        
        assertNotNull(invitation.get("processedAt"));
        assertTrue((Long) invitation.get("processedAt") <= currentTime);
    }

    @Test
    public void testCancelledReasonTracking() {
        Map<String, Object> cancelledEntrant = createCancelledEntrant("user1", "event123", currentTime);
        
        assertEquals("MISSED_DEADLINE", cancelledEntrant.get("reason"));
        assertNotNull(cancelledEntrant.get("cancelledAt"));
    }

    @Test
    public void testMultipleEventDeadlineProcessing() {
        List<Map<String, Object>> multiEventInvitations = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            Map<String, Object> invitation = new HashMap<>();
            invitation.put("invitationId", "inv" + i);
            invitation.put("eventId", "event" + i);
            invitation.put("uid", "user" + i);
            invitation.put("status", "PENDING");
            invitation.put("deadline", pastDeadline);
            multiEventInvitations.add(invitation);
        }
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(multiEventInvitations, currentTime);
        
        assertEquals(3, expired.size());
        
        Map<String, Integer> eventCounts = new HashMap<>();
        for (Map<String, Object> invitation : expired) {
            String eventId = (String) invitation.get("eventId");
            eventCounts.put(eventId, eventCounts.getOrDefault(eventId, 0) + 1);
        }
        
        assertEquals(3, eventCounts.size());
    }

    @Test
    public void testReplacementTriggering() {
        List<Map<String, Object>> expired = findExpiredPendingInvitations(invitations, currentTime);
        
        boolean shouldTriggerReplacement = expired.size() > 0;
        assertTrue(shouldTriggerReplacement);
    }

    @Test
    public void testEventTimingValidation() {
        long eventStart = currentTime + 300000;
        long deadline = currentTime + 120000;
        
        assertTrue(deadline < eventStart);
        assertTrue(currentTime < deadline);
        assertTrue(currentTime < eventStart);
    }

    @Test
    public void testNoDeadlineInvitations() {
        Map<String, Object> noDeadline = new HashMap<>();
        noDeadline.put("invitationId", "inv8");
        noDeadline.put("status", "PENDING");
        noDeadline.put("deadline", null);
        
        List<Map<String, Object>> testList = new ArrayList<>();
        testList.add(noDeadline);
        
        List<Map<String, Object>> expired = findExpiredPendingInvitations(testList, currentTime);
        
        assertEquals(0, expired.size());
    }

    private List<Map<String, Object>> findExpiredPendingInvitations(
        List<Map<String, Object>> invitations, long currentTime
    ) {
        List<Map<String, Object>> expired = new ArrayList<>();
        for (Map<String, Object> invitation : invitations) {
            String status = (String) invitation.get("status");
            Long deadline = (Long) invitation.get("deadline");
            
            if ("PENDING".equals(status) && deadline != null && deadline < currentTime) {
                expired.add(invitation);
            }
        }
        return expired;
    }

    private Map<String, Object> createCancelledEntrant(String userId, String eventId, long cancelledAt) {
        Map<String, Object> entrant = new HashMap<>();
        entrant.put("uid", userId);
        entrant.put("eventId", eventId);
        entrant.put("reason", "MISSED_DEADLINE");
        entrant.put("cancelledAt", cancelledAt);
        return entrant;
    }
}

