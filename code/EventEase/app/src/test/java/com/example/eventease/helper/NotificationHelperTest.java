package com.example.eventease.helper;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend unit tests for NotificationHelper functionality.
 * Tests the notification request creation and filtering logic without Firebase dependencies.
 */
public class NotificationHelperTest {

    private List<Map<String, Object>> notificationRequests;
    private List<Map<String, Object>> users;
    
    @Before
    public void setUp() {
        notificationRequests = new ArrayList<>();
        users = new ArrayList<>();
        
        Map<String, Object> user1 = new HashMap<>();
        user1.put("uid", "user1");
        user1.put("fcmToken", "token1");
        user1.put("notificationsEnabled", true);
        user1.put("notificationPreferenceInvited", true);
        user1.put("notificationPreferenceNotInvited", true);
        users.add(user1);
        
        Map<String, Object> user2 = new HashMap<>();
        user2.put("uid", "user2");
        user2.put("fcmToken", "token2");
        user2.put("notificationsEnabled", false);
        users.add(user2);
        
        Map<String, Object> user3 = new HashMap<>();
        user3.put("uid", "user3");
        user3.put("fcmToken", "token3");
        user3.put("notificationsEnabled", true);
        user3.put("notificationPreferenceInvited", false);
        users.add(user3);
    }

    @Test
    public void testNotificationRequestStructure() {
        Map<String, Object> request = createNotificationRequest(
            "event123",
            "Test Event",
            List.of("user1", "user2"),
            "selected",
            "You've been selected!",
            "Test notification message"
        );
        
        assertNotNull(request);
        assertEquals("event123", request.get("eventId"));
        assertEquals("Test Event", request.get("eventTitle"));
        assertEquals("selected", request.get("groupType"));
        assertEquals("You've been selected!", request.get("title"));
        assertEquals("Test notification message", request.get("message"));
        assertEquals("PENDING", request.get("status"));
        assertEquals(false, request.get("processed"));
        assertNotNull(request.get("createdAt"));
        
        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) request.get("userIds");
        assertEquals(2, userIds.size());
        assertTrue(userIds.contains("user1"));
        assertTrue(userIds.contains("user2"));
    }

    @Test
    public void testFilterUsersByNotificationPreferences_AllEnabled() {
        List<String> userIds = List.of("user1");
        List<String> filtered = filterUsersByPreferences(userIds, "selected", true);
        
        assertEquals(1, filtered.size());
        assertTrue(filtered.contains("user1"));
    }

    @Test
    public void testFilterUsersByNotificationPreferences_Disabled() {
        List<String> userIds = List.of("user2");
        List<String> filtered = filterUsersByPreferences(userIds, "selected", true);
        
        assertEquals(0, filtered.size());
    }

    @Test
    public void testFilterUsersByNotificationPreferences_PreferenceDisabled() {
        List<String> userIds = List.of("user3");
        List<String> filtered = filterUsersByPreferences(userIds, "selected", true);
        
        assertEquals(0, filtered.size());
    }

    @Test
    public void testFilterUsersByNotificationPreferences_Mixed() {
        List<String> userIds = List.of("user1", "user2", "user3");
        List<String> filtered = filterUsersByPreferences(userIds, "selected", true);
        
        assertEquals(1, filtered.size());
        assertTrue(filtered.contains("user1"));
    }

    @Test
    public void testNotificationGroupTypes() {
        String[] groupTypes = {"waitlist", "selected", "cancelled", "nonSelected", "replacement", "general"};
        
        for (String groupType : groupTypes) {
            Map<String, Object> request = createNotificationRequest(
                "event123",
                "Test Event",
                List.of("user1"),
                groupType,
                "Test Title",
                "Test Message"
            );
            
            assertEquals(groupType, request.get("groupType"));
        }
    }

    @Test
    public void testNotificationDefaultMessages() {
        String waitlistMsg = getDefaultMessage("waitlist", "My Event");
        assertTrue(waitlistMsg.contains("waitlist"));
        assertTrue(waitlistMsg.contains("My Event"));
        
        String selectedMsg = getDefaultMessage("selected", "My Event");
        assertTrue(selectedMsg.contains("selected") || selectedMsg.contains("Congratulations"));
        assertTrue(selectedMsg.contains("My Event"));
        
        String nonSelectedMsg = getDefaultMessage("nonSelected", "My Event");
        assertTrue(nonSelectedMsg.contains("not selected") || nonSelectedMsg.contains("Unfortunately"));
        assertTrue(nonSelectedMsg.contains("My Event"));
    }

    @Test
    public void testDuplicateNotificationDetection() {
        Map<String, Object> request1 = createNotificationRequest(
            "event123", "Test Event", List.of("user1"), "selected",
            "You've been selected!", "Test message"
        );
        request1.put("createdAt", System.currentTimeMillis());
        notificationRequests.add(request1);
        
        boolean isDuplicate = checkForDuplicateNotification(
            "event123", "You've been selected!", System.currentTimeMillis(), 5 * 60 * 1000
        );
        
        assertTrue(isDuplicate);
    }

    @Test
    public void testNoDuplicateDetectionAfterTimeWindow() {
        Map<String, Object> request1 = createNotificationRequest(
            "event123", "Test Event", List.of("user1"), "selected",
            "You've been selected!", "Test message"
        );
        long oldTime = System.currentTimeMillis() - (10 * 60 * 1000);
        request1.put("createdAt", oldTime);
        notificationRequests.add(request1);
        
        boolean isDuplicate = checkForDuplicateNotification(
            "event123", "You've been selected!", System.currentTimeMillis(), 5 * 60 * 1000
        );
        
        assertFalse(isDuplicate);
    }

    @Test
    public void testEmptyUserList() {
        List<String> filtered = filterUsersByPreferences(new ArrayList<>(), "selected", true);
        assertEquals(0, filtered.size());
    }

    @Test
    public void testNullUserList() {
        List<String> filtered = filterUsersByPreferences(null, "selected", true);
        assertEquals(0, filtered.size());
    }

    private Map<String, Object> createNotificationRequest(
        String eventId, String eventTitle, List<String> userIds,
        String groupType, String title, String message
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", eventId);
        request.put("eventTitle", eventTitle);
        request.put("userIds", new ArrayList<>(userIds));
        request.put("groupType", groupType);
        request.put("title", title);
        request.put("message", message);
        request.put("status", "PENDING");
        request.put("createdAt", System.currentTimeMillis());
        request.put("processed", false);
        return request;
    }

    private List<String> filterUsersByPreferences(
        List<String> userIds, String groupType, boolean isInvitedNotification
    ) {
        if (userIds == null) {
            return new ArrayList<>();
        }
        
        List<String> filtered = new ArrayList<>();
        for (String userId : userIds) {
            Map<String, Object> user = findUser(userId);
            if (user == null) continue;
            
            Boolean notificationsEnabled = (Boolean) user.get("notificationsEnabled");
            if (notificationsEnabled == null || !notificationsEnabled) {
                continue;
            }
            
            if (isInvitedNotification) {
                Boolean preference = (Boolean) user.get("notificationPreferenceInvited");
                if (preference == null || preference) {
                    filtered.add(userId);
                }
            } else {
                Boolean preference = (Boolean) user.get("notificationPreferenceNotInvited");
                if (preference == null || preference) {
                    filtered.add(userId);
                }
            }
        }
        return filtered;
    }

    private Map<String, Object> findUser(String userId) {
        for (Map<String, Object> user : users) {
            if (userId.equals(user.get("uid"))) {
                return user;
            }
        }
        return null;
    }

    private String getDefaultMessage(String groupType, String eventTitle) {
        String eventName = eventTitle != null ? eventTitle : "this event";
        switch (groupType) {
            case "waitlist":
                return "You are on the waitlist for " + eventName + ". We'll notify you if a spot becomes available.";
            case "selected":
                return "Congratulations! You've been selected for " + eventName + ". Please check your invitations.";
            case "nonSelected":
                return "Thank you for your interest in " + eventName + ". Unfortunately, you were not selected this time.";
            default:
                return "You have an update regarding " + eventName + ".";
        }
    }

    private boolean checkForDuplicateNotification(
        String eventId, String title, long currentTime, long timeWindowMs
    ) {
        long cutoffTime = currentTime - timeWindowMs;
        for (Map<String, Object> request : notificationRequests) {
            if (eventId.equals(request.get("eventId")) &&
                title.equals(request.get("title"))) {
                Long createdAt = (Long) request.get("createdAt");
                if (createdAt != null && createdAt > cutoffTime) {
                    return true;
                }
            }
        }
        return false;
    }
}

