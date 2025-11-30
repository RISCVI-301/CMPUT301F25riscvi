package com.example.eventease.notifications;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend unit tests for notification badge logic.
 * Tests when the badge should be shown based on new notifications compared to last seen time.
 */
public class NotificationBadgeTest {

    private long lastSeenTime;
    private long currentTime;
    private List<Map<String, Object>> notifications;
    
    @Before
    public void setUp() {
        currentTime = System.currentTimeMillis();
        lastSeenTime = currentTime - 3600000;
        notifications = new ArrayList<>();
    }

    @Test
    public void testBadgeShownForNewNotification() {
        Map<String, Object> newNotif = createNotification(currentTime - 1000);
        notifications.add(newNotif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertTrue("Badge should be shown for new notification", hasNewNotifications);
    }

    @Test
    public void testBadgeNotShownForOldNotification() {
        Map<String, Object> oldNotif = createNotification(lastSeenTime - 1000);
        notifications.add(oldNotif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Badge should not be shown for old notification", hasNewNotifications);
    }

    @Test
    public void testBadgeShownForMixedNotifications() {
        notifications.add(createNotification(lastSeenTime - 2000));
        notifications.add(createNotification(currentTime - 1000));
        notifications.add(createNotification(lastSeenTime - 1000));
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertTrue("Badge should be shown when at least one notification is new", hasNewNotifications);
    }

    @Test
    public void testBadgeNotShownWhenAllOld() {
        notifications.add(createNotification(lastSeenTime - 1000));
        notifications.add(createNotification(lastSeenTime - 2000));
        notifications.add(createNotification(lastSeenTime - 3000));
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Badge should not be shown when all notifications are old", hasNewNotifications);
    }

    @Test
    public void testBadgeNotShownForEmptyList() {
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Badge should not be shown for empty notification list", hasNewNotifications);
    }

    @Test
    public void testNotificationExactlyAtLastSeenTime() {
        Map<String, Object> exactNotif = createNotification(lastSeenTime);
        notifications.add(exactNotif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Badge should not be shown for notification exactly at last seen time", hasNewNotifications);
    }

    @Test
    public void testNotificationOneMillisecondAfterLastSeen() {
        Map<String, Object> justNewNotif = createNotification(lastSeenTime + 1);
        notifications.add(justNewNotif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertTrue("Badge should be shown for notification 1ms after last seen", hasNewNotifications);
    }

    @Test
    public void testUserIdFiltering() {
        Map<String, Object> notifForOtherUser = new HashMap<>();
        notifForOtherUser.put("createdAt", currentTime - 1000);
        notifForOtherUser.put("userIds", List.of("otheruser"));
        notifications.add(notifForOtherUser);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Badge should not be shown for notifications not targeting the user", hasNewNotifications);
    }

    @Test
    public void testUserIdFilteringWithMultipleUsers() {
        Map<String, Object> notif = new HashMap<>();
        notif.put("createdAt", currentTime - 1000);
        notif.put("userIds", List.of("otheruser", "testuser", "anotheruser"));
        notifications.add(notif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertTrue("Badge should be shown when user is in the userIds list", hasNewNotifications);
    }

    @Test
    public void testSectioningNewVsEarlier() {
        List<Map<String, Object>> allNotifications = new ArrayList<>();
        allNotifications.add(createNotification(currentTime - 1000));
        allNotifications.add(createNotification(lastSeenTime - 1000));
        allNotifications.add(createNotification(currentTime - 500));
        allNotifications.add(createNotification(lastSeenTime - 2000));
        
        List<Map<String, Object>> newNotifs = new ArrayList<>();
        List<Map<String, Object>> earlierNotifs = new ArrayList<>();
        
        for (Map<String, Object> notif : allNotifications) {
            Long createdAt = (Long) notif.get("createdAt");
            if (createdAt > lastSeenTime) {
                newNotifs.add(notif);
            } else {
                earlierNotifs.add(notif);
            }
        }
        
        assertEquals("Should have 2 new notifications", 2, newNotifs.size());
        assertEquals("Should have 2 earlier notifications", 2, earlierNotifs.size());
    }

    @Test
    public void testLastSeenTimeUpdate() {
        long oldLastSeenTime = currentTime - 7200000;
        long newLastSeenTime = currentTime;
        
        Map<String, Object> notif = createNotification(currentTime - 3600000);
        
        Long createdAt = (Long) notif.get("createdAt");
        assertTrue("Notification should be new relative to old last seen time", createdAt > oldLastSeenTime);
        assertFalse("Notification should be earlier relative to new last seen time", createdAt > newLastSeenTime);
    }

    @Test
    public void testZeroLastSeenTime() {
        long zeroLastSeen = 0;
        Map<String, Object> notif = createNotification(currentTime - 86400000);
        notifications.add(notif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, zeroLastSeen, "testuser");
        
        assertTrue("All notifications should be new when last seen time is 0", hasNewNotifications);
    }

    @Test
    public void testVeryOldNotifications() {
        long veryOldTime = currentTime - (365L * 24 * 60 * 60 * 1000);
        Map<String, Object> oldNotif = createNotification(veryOldTime);
        notifications.add(oldNotif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Very old notifications should not show badge", hasNewNotifications);
    }

    @Test
    public void testNotificationWithNullCreatedAt() {
        Map<String, Object> notif = new HashMap<>();
        notif.put("createdAt", null);
        notif.put("userIds", List.of("testuser"));
        notifications.add(notif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Notifications with null createdAt should not show badge", hasNewNotifications);
    }

    @Test
    public void testNotificationWithNullUserIds() {
        Map<String, Object> notif = new HashMap<>();
        notif.put("createdAt", currentTime - 1000);
        notif.put("userIds", null);
        notifications.add(notif);
        
        boolean hasNewNotifications = checkForNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertFalse("Notifications with null userIds should not show badge", hasNewNotifications);
    }

    @Test
    public void testMultipleNewNotificationsCount() {
        for (int i = 0; i < 5; i++) {
            notifications.add(createNotification(currentTime - (i * 1000)));
        }
        
        int newCount = countNewNotifications(notifications, lastSeenTime, "testuser");
        
        assertEquals("Should have 5 new notifications", 5, newCount);
    }

    private Map<String, Object> createNotification(long createdAt) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("createdAt", createdAt);
        notif.put("userIds", List.of("testuser"));
        notif.put("title", "Test Notification");
        notif.put("message", "This is a test");
        return notif;
    }

    private boolean checkForNewNotifications(
        List<Map<String, Object>> notifications,
        long lastSeenTime,
        String userId
    ) {
        for (Map<String, Object> notif : notifications) {
            Long createdAt = (Long) notif.get("createdAt");
            if (createdAt == null) {
                continue;
            }
            
            Object userIdsObj = notif.get("userIds");
            if (!(userIdsObj instanceof List)) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            List<String> userIds = (List<String>) userIdsObj;
            if (!userIds.contains(userId)) {
                continue;
            }
            
            if (createdAt > lastSeenTime) {
                return true;
            }
        }
        return false;
    }

    private int countNewNotifications(
        List<Map<String, Object>> notifications,
        long lastSeenTime,
        String userId
    ) {
        int count = 0;
        for (Map<String, Object> notif : notifications) {
            Long createdAt = (Long) notif.get("createdAt");
            if (createdAt == null) {
                continue;
            }
            
            Object userIdsObj = notif.get("userIds");
            if (!(userIdsObj instanceof List)) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            List<String> userIds = (List<String>) userIdsObj;
            if (!userIds.contains(userId)) {
                continue;
            }
            
            if (createdAt > lastSeenTime) {
                count++;
            }
        }
        return count;
    }
}

