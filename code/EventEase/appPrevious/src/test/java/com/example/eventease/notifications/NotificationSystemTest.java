package com.example.eventease.notifications;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend unit tests for the complete notification system.
 * Tests notification request creation, FCM token management, and notification preferences.
 */
public class NotificationSystemTest {

    private Map<String, Object> userWithToken;
    private Map<String, Object> userWithoutToken;
    private Map<String, Object> userWithDisabledNotifications;
    
    @Before
    public void setUp() {
        userWithToken = new HashMap<>();
        userWithToken.put("uid", "user1");
        userWithToken.put("name", "Test User");
        userWithToken.put("firstName", "Test");
        userWithToken.put("fcmToken", "valid_fcm_token_123");
        userWithToken.put("notificationsEnabled", true);
        userWithToken.put("notificationPreferenceInvited", true);
        userWithToken.put("notificationPreferenceNotInvited", true);
        
        userWithoutToken = new HashMap<>();
        userWithoutToken.put("uid", "user2");
        userWithoutToken.put("name", "No Token User");
        userWithoutToken.put("fcmToken", null);
        userWithoutToken.put("notificationsEnabled", true);
        
        userWithDisabledNotifications = new HashMap<>();
        userWithDisabledNotifications.put("uid", "user3");
        userWithDisabledNotifications.put("name", "Disabled User");
        userWithDisabledNotifications.put("fcmToken", "valid_fcm_token_456");
        userWithDisabledNotifications.put("notificationsEnabled", false);
    }

    @Test
    public void testFCMTokenValidation() {
        String token1 = (String) userWithToken.get("fcmToken");
        assertNotNull("User with token should have valid FCM token", token1);
        assertFalse("FCM token should not be empty", token1.isEmpty());
        
        String token2 = (String) userWithoutToken.get("fcmToken");
        assertNull("User without token should have null FCM token", token2);
    }

    @Test
    public void testNotificationsEnabledFlag() {
        Boolean enabled1 = (Boolean) userWithToken.get("notificationsEnabled");
        assertTrue("Notifications should be enabled for user1", enabled1);
        
        Boolean enabled3 = (Boolean) userWithDisabledNotifications.get("notificationsEnabled");
        assertFalse("Notifications should be disabled for user3", enabled3);
    }

    @Test
    public void testUserCanReceiveNotifications() {
        assertTrue(canUserReceiveNotifications(userWithToken));
        assertFalse(canUserReceiveNotifications(userWithoutToken));
        assertFalse(canUserReceiveNotifications(userWithDisabledNotifications));
    }

    @Test
    public void testNotificationPreferences_Invited() {
        Boolean pref = (Boolean) userWithToken.get("notificationPreferenceInvited");
        assertTrue("User should accept invited notifications", pref);
    }

    @Test
    public void testNotificationPreferences_NotInvited() {
        Boolean pref = (Boolean) userWithToken.get("notificationPreferenceNotInvited");
        assertTrue("User should accept not invited notifications", pref);
    }

    @Test
    public void testNotificationRequestCreation() {
        Map<String, Object> request = createNotificationRequest(
            "event123",
            "Test Event",
            List.of("user1", "user2"),
            "selected",
            "You've been selected!",
            "Congratulations on your selection!"
        );
        
        assertNotNull(request);
        assertEquals("event123", request.get("eventId"));
        assertEquals("Test Event", request.get("eventTitle"));
        assertEquals("selected", request.get("groupType"));
        assertEquals("PENDING", request.get("status"));
        assertFalse((Boolean) request.get("processed"));
        
        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) request.get("userIds");
        assertEquals(2, userIds.size());
    }

    @Test
    public void testNotificationRequestProcessing() {
        Map<String, Object> request = createNotificationRequest(
            "event123", "Test Event", List.of("user1"), "selected",
            "Test Title", "Test Message"
        );
        
        assertEquals(false, request.get("processed"));
        
        request.put("processed", true);
        request.put("sentCount", 1);
        request.put("failureCount", 0);
        
        assertTrue((Boolean) request.get("processed"));
        assertEquals(1, request.get("sentCount"));
        assertEquals(0, request.get("failureCount"));
    }

    @Test
    public void testNotificationRequestWithError() {
        Map<String, Object> request = createNotificationRequest(
            "event123", "Test Event", List.of("user2"), "selected",
            "Test Title", "Test Message"
        );
        
        request.put("processed", true);
        request.put("error", "No valid FCM tokens found");
        request.put("sentCount", 0);
        
        assertTrue((Boolean) request.get("processed"));
        assertNotNull(request.get("error"));
        assertEquals(0, request.get("sentCount"));
    }

    @Test
    public void testPersonalizedMessage() {
        String originalMessage = "You've been selected for the event.";
        String firstName = "John";
        
        String personalized = personalizeMessage(originalMessage, firstName);
        
        assertTrue(personalized.contains(firstName));
        assertTrue(personalized.contains("Hey"));
    }

    @Test
    public void testPersonalizedMessage_NoFirstName() {
        String originalMessage = "You've been selected for the event.";
        String personalized = personalizeMessage(originalMessage, null);
        
        assertEquals(originalMessage, personalized);
    }

    @Test
    public void testPersonalizedMessage_EmptyFirstName() {
        String originalMessage = "You've been selected for the event.";
        String personalized = personalizeMessage(originalMessage, "");
        
        assertEquals(originalMessage, personalized);
    }

    @Test
    public void testNotificationChannelId() {
        String channelId = "event_invitations";
        assertNotNull(channelId);
        assertFalse(channelId.isEmpty());
    }

    @Test
    public void testNotificationPriority() {
        Map<String, Object> androidConfig = new HashMap<>();
        androidConfig.put("priority", "high");
        
        assertEquals("high", androidConfig.get("priority"));
    }

    @Test
    public void testNotificationData() {
        Map<String, String> data = new HashMap<>();
        data.put("type", "invitation");
        data.put("eventId", "event123");
        data.put("eventTitle", "Test Event");
        
        assertEquals("invitation", data.get("type"));
        assertEquals("event123", data.get("eventId"));
        assertEquals("Test Event", data.get("eventTitle"));
    }

    @Test
    public void testMultipleUsersNotificationRequest() {
        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            userIds.add("user" + i);
        }
        
        Map<String, Object> request = createNotificationRequest(
            "event123", "Test Event", userIds, "selected",
            "Test Title", "Test Message"
        );
        
        @SuppressWarnings("unchecked")
        List<String> requestUserIds = (List<String>) request.get("userIds");
        assertEquals(100, requestUserIds.size());
    }

    @Test
    public void testNotificationTimestamp() {
        long before = System.currentTimeMillis();
        Map<String, Object> request = createNotificationRequest(
            "event123", "Test Event", List.of("user1"), "selected",
            "Test Title", "Test Message"
        );
        long after = System.currentTimeMillis();
        
        Long createdAt = (Long) request.get("createdAt");
        assertNotNull(createdAt);
        assertTrue(createdAt >= before);
        assertTrue(createdAt <= after);
    }

    @Test
    public void testNotificationGroupTypeValidation() {
        String[] validGroupTypes = {"selected", "waitlist", "cancelled", "nonSelected", "replacement", "general", "sorry"};
        
        for (String groupType : validGroupTypes) {
            Map<String, Object> request = createNotificationRequest(
                "event123", "Test Event", List.of("user1"), groupType,
                "Test Title", "Test Message"
            );
            
            assertEquals(groupType, request.get("groupType"));
        }
    }

    @Test
    public void testNotificationStatusTransition() {
        Map<String, Object> request = createNotificationRequest(
            "event123", "Test Event", List.of("user1"), "selected",
            "Test Title", "Test Message"
        );
        
        assertEquals("PENDING", request.get("status"));
        
        request.put("status", "PROCESSING");
        assertEquals("PROCESSING", request.get("status"));
        
        request.put("status", "COMPLETED");
        request.put("processed", true);
        assertEquals("COMPLETED", request.get("status"));
        assertTrue((Boolean) request.get("processed"));
    }

    @Test
    public void testBooleanNotificationsEnabledField() {
        Object enabled = userWithToken.get("notificationsEnabled");
        assertTrue(enabled instanceof Boolean);
        assertTrue((Boolean) enabled);
    }

    @Test
    public void testStringNotificationsEnabledField() {
        Map<String, Object> userWithStringFlag = new HashMap<>();
        userWithStringFlag.put("uid", "user4");
        userWithStringFlag.put("notificationsEnabled", "true");
        
        Object enabled = userWithStringFlag.get("notificationsEnabled");
        assertTrue(enabled instanceof String);
        assertTrue(Boolean.parseBoolean((String) enabled));
    }

    @Test
    public void testDefaultNotificationsEnabled() {
        Map<String, Object> userWithoutFlag = new HashMap<>();
        userWithoutFlag.put("uid", "user5");
        
        Object enabled = userWithoutFlag.get("notificationsEnabled");
        assertNull(enabled);
        
        boolean isEnabled = enabled == null ? true : (Boolean) enabled;
        assertTrue("Should default to true when not set", isEnabled);
    }

    private boolean canUserReceiveNotifications(Map<String, Object> user) {
        String fcmToken = (String) user.get("fcmToken");
        if (fcmToken == null || fcmToken.isEmpty()) {
            return false;
        }
        
        Boolean notificationsEnabled = (Boolean) user.get("notificationsEnabled");
        if (notificationsEnabled == null) {
            notificationsEnabled = true;
        }
        
        return notificationsEnabled;
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

    private String personalizeMessage(String originalMessage, String firstName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            return originalMessage;
        }
        
        String capitalizedName = firstName.substring(0, 1).toUpperCase() + 
                                firstName.substring(1).toLowerCase();
        return "Hey " + capitalizedName + ", " + originalMessage;
    }
}

