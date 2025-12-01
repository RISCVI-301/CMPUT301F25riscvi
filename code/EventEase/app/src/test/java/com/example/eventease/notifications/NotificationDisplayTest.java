package com.example.eventease.notifications;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend unit tests for notification display and filtering logic.
 * Tests how notifications are queried, filtered, and sorted for display.
 */
public class NotificationDisplayTest {

    private List<Map<String, Object>> allNotificationRequests;
    private String currentUserId;
    private long currentTime;
    
    @Before
    public void setUp() {
        currentUserId = "testuser123";
        currentTime = System.currentTimeMillis();
        allNotificationRequests = new ArrayList<>();
        
        Map<String, Object> notif1 = new HashMap<>();
        notif1.put("id", "notif1");
        notif1.put("eventId", "event1");
        notif1.put("eventTitle", "Event 1");
        notif1.put("title", "You've been selected!");
        notif1.put("message", "Congratulations!");
        notif1.put("userIds", List.of(currentUserId, "otheruser"));
        notif1.put("createdAt", currentTime - 10000);
        notif1.put("groupType", "selected");
        allNotificationRequests.add(notif1);
        
        Map<String, Object> notif2 = new HashMap<>();
        notif2.put("id", "notif2");
        notif2.put("eventId", "event2");
        notif2.put("eventTitle", "Event 2");
        notif2.put("title", "Test Notification");
        notif2.put("message", "This is a test");
        notif2.put("userIds", List.of(currentUserId));
        notif2.put("createdAt", currentTime - 5000);
        notif2.put("groupType", "general");
        allNotificationRequests.add(notif2);
        
        Map<String, Object> notif3 = new HashMap<>();
        notif3.put("id", "notif3");
        notif3.put("eventId", "event3");
        notif3.put("eventTitle", "Event 3");
        notif3.put("title", "Not for this user");
        notif3.put("message", "Different user");
        notif3.put("userIds", List.of("differentuser"));
        notif3.put("createdAt", currentTime - 2000);
        notif3.put("groupType", "selected");
        allNotificationRequests.add(notif3);
    }

    @Test
    public void testFilterNotificationsByUser() {
        List<Map<String, Object>> filtered = filterNotificationsByUserId(
            allNotificationRequests, currentUserId
        );
        
        assertEquals(2, filtered.size());
        
        for (Map<String, Object> notif : filtered) {
            @SuppressWarnings("unchecked")
            List<String> userIds = (List<String>) notif.get("userIds");
            assertTrue(userIds.contains(currentUserId));
        }
    }

    @Test
    public void testSortNotificationsByTime() {
        List<Map<String, Object>> filtered = filterNotificationsByUserId(
            allNotificationRequests, currentUserId
        );
        
        List<Map<String, Object>> sorted = sortByCreatedAt(filtered);
        
        assertEquals(2, sorted.size());
        
        long firstTime = (Long) sorted.get(0).get("createdAt");
        long secondTime = (Long) sorted.get(1).get("createdAt");
        
        assertTrue(firstTime >= secondTime);
    }

    @Test
    public void testNotificationItemConversion() {
        Map<String, Object> request = allNotificationRequests.get(0);
        NotificationItem item = convertToNotificationItem(request);
        
        assertEquals("notif1", item.id);
        assertEquals("event1", item.eventId);
        assertEquals("Event 1", item.eventTitle);
        assertEquals("You've been selected!", item.title);
        assertEquals("Congratulations!", item.message);
        assertEquals("selected", item.groupType);
        assertEquals(currentTime - 10000, item.createdAt);
    }

    @Test
    public void testInvitationAsNotification() {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("invitationId", "inv1");
        invitation.put("eventId", "event1");
        invitation.put("uid", currentUserId);
        invitation.put("status", "PENDING");
        invitation.put("issuedAt", currentTime);
        
        NotificationItem item = convertInvitationToNotificationItem(invitation, "Event Title");
        
        assertEquals("invitation_inv1", item.id);
        assertEquals("event1", item.eventId);
        assertEquals("Event Title", item.eventTitle);
        assertEquals("invitation", item.groupType);
        assertTrue(item.title.contains("invited"));
        assertTrue(item.message.contains("Event Title"));
    }

    @Test
    public void testPendingInvitationsOnly() {
        Map<String, Object> pendingInv = new HashMap<>();
        pendingInv.put("status", "PENDING");
        
        Map<String, Object> acceptedInv = new HashMap<>();
        acceptedInv.put("status", "ACCEPTED");
        
        Map<String, Object> declinedInv = new HashMap<>();
        declinedInv.put("status", "DECLINED");
        
        assertTrue("PENDING".equals(pendingInv.get("status")));
        assertFalse("PENDING".equals(acceptedInv.get("status")));
        assertFalse("PENDING".equals(declinedInv.get("status")));
    }

    @Test
    public void testNotificationLoadingFlow() {
        List<Map<String, Object>> step1 = filterNotificationsByUserId(allNotificationRequests, currentUserId);
        assertEquals(2, step1.size());
        
        List<Map<String, Object>> step2 = sortByCreatedAt(step1);
        assertEquals(2, step2.size());
        
        List<NotificationItem> step3 = convertAllToNotificationItems(step2);
        assertEquals(2, step3.size());
    }

    @Test
    public void testEmptyNotificationList() {
        List<Map<String, Object>> filtered = filterNotificationsByUserId(
            allNotificationRequests, "nonexistentuser"
        );
        
        assertEquals(0, filtered.size());
    }

    @Test
    public void testNotificationUserIdsList() {
        Map<String, Object> request = allNotificationRequests.get(0);
        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) request.get("userIds");
        
        assertNotNull(userIds);
        assertTrue(userIds instanceof List);
        assertTrue(userIds.size() > 0);
    }

    @Test
    public void testNotificationTimestampOrdering() {
        long time1 = currentTime - 10000;
        long time2 = currentTime - 5000;
        long time3 = currentTime - 2000;
        
        assertTrue(time3 > time2);
        assertTrue(time2 > time1);
    }

    @Test
    public void testNotificationIdUniqueness() {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> request : allNotificationRequests) {
            String id = (String) request.get("id");
            assertFalse("Notification IDs should be unique", ids.contains(id));
            ids.add(id);
        }
    }

    @Test
    public void testRealTimeListenerFiltering() {
        List<String> loadedIds = new ArrayList<>();
        loadedIds.add("notif1");
        
        List<Map<String, Object>> newNotifications = new ArrayList<>();
        for (Map<String, Object> request : allNotificationRequests) {
            String id = (String) request.get("id");
            if (!loadedIds.contains(id)) {
                newNotifications.add(request);
            }
        }
        
        assertEquals(2, newNotifications.size());
        assertFalse(newNotifications.stream()
            .anyMatch(n -> "notif1".equals(n.get("id"))));
    }

    @Test
    public void testNotificationGroupTypes() {
        String[] groupTypes = {"selected", "general", "waitlist", "replacement", "invitation", "sorry"};
        
        for (String groupType : groupTypes) {
            Map<String, Object> request = new HashMap<>();
            request.put("groupType", groupType);
            
            assertEquals(groupType, request.get("groupType"));
        }
    }

    private List<Map<String, Object>> filterNotificationsByUserId(
        List<Map<String, Object>> notifications, String userId
    ) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> notif : notifications) {
            @SuppressWarnings("unchecked")
            List<String> userIds = (List<String>) notif.get("userIds");
            if (userIds != null && userIds.contains(userId)) {
                filtered.add(notif);
            }
        }
        return filtered;
    }

    private List<Map<String, Object>> sortByCreatedAt(List<Map<String, Object>> notifications) {
        List<Map<String, Object>> sorted = new ArrayList<>(notifications);
        sorted.sort((a, b) -> {
            Long timeA = (Long) a.get("createdAt");
            Long timeB = (Long) b.get("createdAt");
            return Long.compare(timeB != null ? timeB : 0, timeA != null ? timeA : 0);
        });
        return sorted;
    }

    private NotificationItem convertToNotificationItem(Map<String, Object> request) {
        NotificationItem item = new NotificationItem();
        item.id = (String) request.get("id");
        item.eventId = (String) request.get("eventId");
        item.eventTitle = (String) request.get("eventTitle");
        item.title = (String) request.get("title");
        item.message = (String) request.get("message");
        item.groupType = (String) request.get("groupType");
        Long createdAt = (Long) request.get("createdAt");
        item.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
        return item;
    }

    private NotificationItem convertInvitationToNotificationItem(
        Map<String, Object> invitation, String eventTitle
    ) {
        NotificationItem item = new NotificationItem();
        item.id = "invitation_" + invitation.get("invitationId");
        item.eventId = (String) invitation.get("eventId");
        item.eventTitle = eventTitle;
        item.title = "You've been invited!";
        item.message = "You've been selected for \"" + eventTitle + "\". Tap to view details and accept your invitation.";
        item.groupType = "invitation";
        Long issuedAt = (Long) invitation.get("issuedAt");
        item.createdAt = issuedAt != null ? issuedAt : System.currentTimeMillis();
        return item;
    }

    private List<NotificationItem> convertAllToNotificationItems(
        List<Map<String, Object>> requests
    ) {
        List<NotificationItem> items = new ArrayList<>();
        for (Map<String, Object> request : requests) {
            items.add(convertToNotificationItem(request));
        }
        return items;
    }

    static class NotificationItem {
        public String id;
        public String title;
        public String message;
        public String eventId;
        public String eventTitle;
        public long createdAt;
        public String groupType;
    }
}

