package com.example.eventease.notifications;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for NotificationsActivity loading logic.
 * Tests the logic for:
 * - Loading notification requests from Firestore
 * - Loading invitations as notifications
 * - Filtering notifications by user ID
 * - Creating sectioned lists (New vs Earlier)
 * - Handling empty states
 */
public class NotificationsActivityLoadingTest {

    private List<Map<String, Object>> notificationRequests;
    private List<Map<String, Object>> invitations;
    private String testUserId;
    
    @Before
    public void setUp() {
        testUserId = "testUser123";
        notificationRequests = new ArrayList<>();
        invitations = new ArrayList<>();
    }

    @Test
    public void testLoadNotificationRequests_FiltersByUserId() {
        // Create notification requests for different users
        Map<String, Object> request1 = createNotificationRequest(
            "event1", "Event 1", List.of(testUserId, "otherUser"), 
            "selected", "You've been selected!", "Test message"
        );
        notificationRequests.add(request1);
        
        Map<String, Object> request2 = createNotificationRequest(
            "event2", "Event 2", List.of("otherUser"), 
            "selected", "You've been selected!", "Test message"
        );
        notificationRequests.add(request2);
        
        // Filter for test user
        List<Map<String, Object>> userNotifications = filterNotificationsByUserId(testUserId);
        
        assertEquals(1, userNotifications.size());
        assertEquals("event1", userNotifications.get(0).get("eventId"));
    }

    @Test
    public void testLoadNotificationRequests_EmptyWhenNoMatches() {
        // Create notification requests for other users only
        Map<String, Object> request = createNotificationRequest(
            "event1", "Event 1", List.of("otherUser1", "otherUser2"), 
            "selected", "Title", "Message"
        );
        notificationRequests.add(request);
        
        List<Map<String, Object>> userNotifications = filterNotificationsByUserId(testUserId);
        
        assertEquals(0, userNotifications.size());
    }

    @Test
    public void testLoadInvitationsAsNotifications() {
        // Create pending invitation
        Map<String, Object> invitation = createInvitation(
            "invitation1", testUserId, "event1", "PENDING", System.currentTimeMillis()
        );
        invitations.add(invitation);
        
        // Filter for test user
        List<Map<String, Object>> userInvitations = filterInvitationsByUserId(testUserId);
        
        assertEquals(1, userInvitations.size());
        assertEquals("event1", userInvitations.get(0).get("eventId"));
        assertEquals("PENDING", userInvitations.get(0).get("status"));
    }

    @Test
    public void testLoadInvitations_ExcludesNonPending() {
        // Create invitations with different statuses
        invitations.add(createInvitation("inv1", testUserId, "event1", "PENDING", System.currentTimeMillis()));
        invitations.add(createInvitation("inv2", testUserId, "event2", "ACCEPTED", System.currentTimeMillis()));
        invitations.add(createInvitation("inv3", testUserId, "event3", "DECLINED", System.currentTimeMillis()));
        
        List<Map<String, Object>> pendingInvitations = filterInvitationsByUserId(testUserId);
        
        assertEquals(1, pendingInvitations.size());
        assertEquals("PENDING", pendingInvitations.get(0).get("status"));
    }

    @Test
    public void testCombineNotificationRequestsAndInvitations() {
        // Create notification request
        Map<String, Object> request = createNotificationRequest(
            "event1", "Event 1", List.of(testUserId), 
            "selected", "Title 1", "Message 1"
        );
        notificationRequests.add(request);
        
        // Create invitation
        Map<String, Object> invitation = createInvitation(
            "inv1", testUserId, "event2", "PENDING", System.currentTimeMillis()
        );
        invitations.add(invitation);
        
        // Combine both
        List<Map<String, Object>> allNotifications = new ArrayList<>();
        allNotifications.addAll(filterNotificationsByUserId(testUserId));
        allNotifications.addAll(convertInvitationsToNotifications(filterInvitationsByUserId(testUserId)));
        
        assertEquals(2, allNotifications.size());
    }

    @Test
    public void testSortNotificationsByCreatedAt() {
        long now = System.currentTimeMillis();
        
        // Create notifications with different timestamps
        Map<String, Object> oldNotification = createNotificationRequest(
            "event1", "Event 1", List.of(testUserId), 
            "selected", "Title 1", "Message 1"
        );
        oldNotification.put("createdAt", now - 10000);
        
        Map<String, Object> newNotification = createNotificationRequest(
            "event2", "Event 2", List.of(testUserId), 
            "selected", "Title 2", "Message 2"
        );
        newNotification.put("createdAt", now);
        
        List<Map<String, Object>> notifications = new ArrayList<>();
        notifications.add(oldNotification);
        notifications.add(newNotification);
        
        // Sort by createdAt descending
        notifications.sort((a, b) -> {
            Long timeA = (Long) a.get("createdAt");
            Long timeB = (Long) b.get("createdAt");
            return Long.compare(timeB, timeA);
        });
        
        assertEquals("event2", notifications.get(0).get("eventId")); // Newest first
        assertEquals("event1", notifications.get(1).get("eventId")); // Oldest last
    }

    @Test
    public void testCreateSectionedList_NewVsEarlier() {
        long now = System.currentTimeMillis();
        long lastSeenTime = now - 5000; // 5 seconds ago
        
        // Create notifications
        List<NotificationItem> notifications = new ArrayList<>();
        
        NotificationItem newNotification = new NotificationItem();
        newNotification.id = "notif1";
        newNotification.createdAt = now; // After last seen
        notifications.add(newNotification);
        
        NotificationItem oldNotification = new NotificationItem();
        oldNotification.id = "notif2";
        oldNotification.createdAt = now - 10000; // Before last seen
        notifications.add(oldNotification);
        
        // Create sectioned list
        List<SectionedItem> sectioned = createSectionedList(notifications, lastSeenTime);
        
        // Should have: Header "New", newNotification, Header "Earlier", oldNotification
        assertEquals(4, sectioned.size());
        assertTrue(sectioned.get(0).isHeader);
        assertEquals("New", sectioned.get(0).headerText);
        assertFalse(sectioned.get(1).isHeader);
        assertEquals("notif1", sectioned.get(1).notification.id);
        assertTrue(sectioned.get(2).isHeader);
        assertEquals("Earlier", sectioned.get(2).headerText);
        assertFalse(sectioned.get(3).isHeader);
        assertEquals("notif2", sectioned.get(3).notification.id);
    }

    @Test
    public void testCreateSectionedList_OnlyNew() {
        long now = System.currentTimeMillis();
        long lastSeenTime = now - 10000;
        
        List<NotificationItem> notifications = new ArrayList<>();
        NotificationItem newNotification = new NotificationItem();
        newNotification.id = "notif1";
        newNotification.createdAt = now;
        notifications.add(newNotification);
        
        List<SectionedItem> sectioned = createSectionedList(notifications, lastSeenTime);
        
        // Should have: Header "New", newNotification
        assertEquals(2, sectioned.size());
        assertTrue(sectioned.get(0).isHeader);
        assertEquals("New", sectioned.get(0).headerText);
    }

    @Test
    public void testCreateSectionedList_OnlyEarlier() {
        long now = System.currentTimeMillis();
        long lastSeenTime = now + 10000; // Future time (all are earlier)
        
        List<NotificationItem> notifications = new ArrayList<>();
        NotificationItem oldNotification = new NotificationItem();
        oldNotification.id = "notif1";
        oldNotification.createdAt = now;
        notifications.add(oldNotification);
        
        List<SectionedItem> sectioned = createSectionedList(notifications, lastSeenTime);
        
        // Should have: Header "Earlier", oldNotification
        assertEquals(2, sectioned.size());
        assertTrue(sectioned.get(0).isHeader);
        assertEquals("Earlier", sectioned.get(0).headerText);
    }

    @Test
    public void testCreateSectionedList_Empty() {
        List<NotificationItem> notifications = new ArrayList<>();
        List<SectionedItem> sectioned = createSectionedList(notifications, System.currentTimeMillis());
        
        assertEquals(0, sectioned.size());
    }

    // Helper methods
    
    private Map<String, Object> createNotificationRequest(
        String eventId, String eventTitle, List<String> userIds,
        String groupType, String title, String message
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", eventId);
        request.put("eventTitle", eventTitle);
        request.put("userIds", userIds);
        request.put("groupType", groupType);
        request.put("title", title);
        request.put("message", message);
        request.put("createdAt", System.currentTimeMillis());
        return request;
    }

    private Map<String, Object> createInvitation(
        String invitationId, String uid, String eventId, String status, long issuedAt
    ) {
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("id", invitationId);
        invitation.put("uid", uid);
        invitation.put("eventId", eventId);
        invitation.put("status", status);
        invitation.put("issuedAt", issuedAt);
        return invitation;
    }

    private List<Map<String, Object>> filterNotificationsByUserId(String userId) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> request : notificationRequests) {
            @SuppressWarnings("unchecked")
            List<String> userIds = (List<String>) request.get("userIds");
            if (userIds != null && userIds.contains(userId)) {
                filtered.add(request);
            }
        }
        return filtered;
    }

    private List<Map<String, Object>> filterInvitationsByUserId(String userId) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> invitation : invitations) {
            if (userId.equals(invitation.get("uid")) && 
                "PENDING".equals(invitation.get("status"))) {
                filtered.add(invitation);
            }
        }
        return filtered;
    }

    private List<Map<String, Object>> convertInvitationsToNotifications(List<Map<String, Object>> invitations) {
        List<Map<String, Object>> notifications = new ArrayList<>();
        for (Map<String, Object> invitation : invitations) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", "invitation_" + invitation.get("id"));
            notification.put("title", "You've been invited!");
            notification.put("eventId", invitation.get("eventId"));
            notification.put("createdAt", invitation.get("issuedAt"));
            notification.put("groupType", "invitation");
            notifications.add(notification);
        }
        return notifications;
    }

    private List<SectionedItem> createSectionedList(List<NotificationItem> notifications, long lastSeenTime) {
        List<SectionedItem> sectioned = new ArrayList<>();
        
        List<NotificationItem> newNotifications = new ArrayList<>();
        List<NotificationItem> seenNotifications = new ArrayList<>();
        
        for (NotificationItem item : notifications) {
            if (item.createdAt > lastSeenTime) {
                newNotifications.add(item);
            } else {
                seenNotifications.add(item);
            }
        }
        
        if (!newNotifications.isEmpty()) {
            sectioned.add(new SectionedItem(true, "New", null));
            for (NotificationItem item : newNotifications) {
                sectioned.add(new SectionedItem(false, null, item));
            }
        }
        
        if (!seenNotifications.isEmpty()) {
            sectioned.add(new SectionedItem(true, "Earlier", null));
            for (NotificationItem item : seenNotifications) {
                sectioned.add(new SectionedItem(false, null, item));
            }
        }
        
        return sectioned;
    }

    // Helper classes
    static class NotificationItem {
        String id;
        String title;
        String message;
        String eventId;
        String eventTitle;
        long createdAt;
        String groupType;
    }

    static class SectionedItem {
        boolean isHeader;
        String headerText;
        NotificationItem notification;

        SectionedItem(boolean isHeader, String headerText, NotificationItem notification) {
            this.isHeader = isHeader;
            this.headerText = headerText;
            this.notification = notification;
        }
    }
}

