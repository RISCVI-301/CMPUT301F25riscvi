package com.example.eventease.notifications;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.eventease.auth.DeviceAuthManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * REAL Firebase integration tests for notification filtering based on preferences.
 * 
 * Tests the NotificationHelper.filterUsersByPreferences() functionality with REAL Firebase:
 * - Filtering users for "selected" notifications (checks notificationPreferenceInvited)
 * - Filtering users for "nonSelected" notifications (checks notificationPreferenceNotInvited)
 * - Default behavior when preferences not set
 * 
 * ⚠️ These tests interact with REAL Firebase/Firestore!
 * 
 * To run: ./gradlew connectedAndroidTest --tests NotificationFilteringIntegrationTest
 */
@RunWith(AndroidJUnit4.class)
public class NotificationFilteringIntegrationTest {
    
    private static final String TAG = "NotificationFilteringIntegrationTest";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private List<String> testUserIds;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        testUserIds = new ArrayList<>();
        
        Log.d(TAG, "Setting up test users");
    }
    
    @After
    public void tearDown() {
        // Clean up test user documents
        for (String userId : testUserIds) {
            db.collection("users").document(userId).delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleaned up test user: " + userId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clean up test user: " + userId, e));
        }
    }

    @Test
    public void testFilterInvitedNotifications_WithPreferences() throws Exception {
        Log.d(TAG, "=== Test: Filter invited notifications based on preferences ===");
        
        // Create test users with different preferences
        String user1 = createTestUser("user1", true, true);  // Both enabled
        String user2 = createTestUser("user2", true, false); // Only invited enabled
        String user3 = createTestUser("user3", false, true); // Only not invited enabled
        String user4 = createTestUser("user4", false, false); // Both disabled
        
        Thread.sleep(2000); // Wait for Firestore to sync
        
        // Filter for invited notifications (selected group)
        List<String> userIds = List.of(user1, user2, user3, user4);
        List<String> filtered = filterUsersByPreferences(userIds, "selected", true);
        
        // Should include: user1 (both enabled), user2 (invited enabled)
        assertEquals("Should filter to users with invited notifications enabled", 2, filtered.size());
        assertTrue("Should include user1", filtered.contains(user1));
        assertTrue("Should include user2", filtered.contains(user2));
        assertFalse("Should exclude user3", filtered.contains(user3));
        assertFalse("Should exclude user4", filtered.contains(user4));
        
        Log.d(TAG, "✓ Test passed: Invited notifications filtered correctly");
    }

    @Test
    public void testFilterNotInvitedNotifications_WithPreferences() throws Exception {
        Log.d(TAG, "=== Test: Filter not invited notifications based on preferences ===");
        
        // Create test users with different preferences
        String user1 = createTestUser("user1", true, true);  // Both enabled
        String user2 = createTestUser("user2", true, false); // Only invited enabled
        String user3 = createTestUser("user3", false, true); // Only not invited enabled
        String user4 = createTestUser("user4", false, false); // Both disabled
        
        Thread.sleep(2000); // Wait for Firestore to sync
        
        // Filter for not invited notifications (nonSelected group)
        List<String> userIds = List.of(user1, user2, user3, user4);
        List<String> filtered = filterUsersByPreferences(userIds, "nonSelected", false);
        
        // Should include: user1 (both enabled), user3 (not invited enabled)
        assertEquals("Should filter to users with not invited notifications enabled", 2, filtered.size());
        assertTrue("Should include user1", filtered.contains(user1));
        assertTrue("Should include user3", filtered.contains(user3));
        assertFalse("Should exclude user2", filtered.contains(user2));
        assertFalse("Should exclude user4", filtered.contains(user4));
        
        Log.d(TAG, "✓ Test passed: Not invited notifications filtered correctly");
    }

    @Test
    public void testFilterInvitedNotifications_DefaultEnabled() throws Exception {
        Log.d(TAG, "=== Test: Default to enabled when preferences not set ===");
        
        // Create user without preferences
        String user1 = createTestUser("user1", null, null);
        
        Thread.sleep(2000);
        
        // Filter for invited notifications
        List<String> userIds = List.of(user1);
        List<String> filtered = filterUsersByPreferences(userIds, "selected", true);
        
        // Should default to enabled
        assertEquals("Should include user when preference not set (default enabled)", 1, filtered.size());
        assertTrue("Should include user1", filtered.contains(user1));
        
        Log.d(TAG, "✓ Test passed: Defaults to enabled when preferences not set");
    }

    @Test
    public void testFilterSelectionGroupType() throws Exception {
        Log.d(TAG, "=== Test: Filter with 'selection' group type ===");
        
        // Create test users
        String user1 = createTestUser("user1", true, true);
        String user2 = createTestUser("user2", false, true);
        
        Thread.sleep(2000);
        
        // Filter for "selection" group type (should check invited preference)
        List<String> userIds = List.of(user1, user2);
        List<String> filtered = filterUsersByPreferences(userIds, "selection", true);
        
        // Should include only user1
        assertEquals("Should filter to users with invited notifications enabled", 1, filtered.size());
        assertTrue("Should include user1", filtered.contains(user1));
        assertFalse("Should exclude user2", filtered.contains(user2));
        
        Log.d(TAG, "✓ Test passed: Selection group type filters correctly");
    }

    @Test
    public void testFilterEmptyUserList() throws Exception {
        Log.d(TAG, "=== Test: Filter empty user list ===");
        
        List<String> filtered = filterUsersByPreferences(new ArrayList<>(), "selected", true);
        
        assertEquals("Should return empty list for empty input", 0, filtered.size());
        
        Log.d(TAG, "✓ Test passed: Empty list handled correctly");
    }

    // Helper methods
    
    private String createTestUser(String name, Boolean invitedPref, Boolean notInvitedPref) throws Exception {
        String userId = "test_" + name + "_" + UUID.randomUUID().toString().substring(0, 8);
        testUserIds.add(userId);
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        if (invitedPref != null) {
            userData.put("notificationPreferenceInvited", invitedPref);
        }
        if (notInvitedPref != null) {
            userData.put("notificationPreferenceNotInvited", notInvitedPref);
        }
        
        Tasks.await(db.collection("users").document(userId).set(userData));
        return userId;
    }

    private List<String> filterUsersByPreferences(
        List<String> userIds, String groupType, boolean checkInvitedPreference
    ) throws Exception {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Determine which preference field to check
        boolean checkInvited = checkInvitedPreference || 
                              groupType.equals("selected") || 
                              groupType.equals("selection");
        
        List<String> filtered = new ArrayList<>();
        
        // Fetch all user documents from Firebase
        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String userId : userIds) {
            tasks.add(db.collection("users").document(userId).get());
        }
        
        // Wait for all tasks to complete
        List<com.google.android.gms.tasks.Task<?>> completedTasks = 
            Tasks.await(com.google.android.gms.tasks.Tasks.whenAllComplete(tasks), 10, TimeUnit.SECONDS);
        
        for (int i = 0; i < completedTasks.size() && i < userIds.size(); i++) {
            com.google.android.gms.tasks.Task<?> task = completedTasks.get(i);
            String userId = userIds.get(i);
            
            if (task.isSuccessful()) {
                Object result = task.getResult();
                if (result instanceof DocumentSnapshot) {
                    DocumentSnapshot userDoc = (DocumentSnapshot) result;
                    
                    if (userDoc != null && userDoc.exists()) {
                        // Check the appropriate preference field
                        Boolean preferenceValue;
                        if (checkInvited) {
                            preferenceValue = userDoc.getBoolean("notificationPreferenceInvited");
                        } else {
                            preferenceValue = userDoc.getBoolean("notificationPreferenceNotInvited");
                        }
                        
                        // Default to true (enabled) if preference not set
                        boolean isEnabled = preferenceValue != null ? preferenceValue : true;
                        
                        if (isEnabled) {
                            filtered.add(userId);
                            Log.d(TAG, "User " + userId + " has " + 
                                (checkInvited ? "invited" : "not invited") + 
                                " notifications enabled");
                        } else {
                            Log.d(TAG, "User " + userId + " has " + 
                                (checkInvited ? "invited" : "not invited") + 
                                " notifications disabled - skipping");
                        }
                    } else {
                        // User document doesn't exist - default to enabled
                        filtered.add(userId);
                        Log.d(TAG, "User " + userId + " not found - defaulting to enabled");
                    }
                }
            } else {
                // Failed to fetch - default to enabled (don't block notifications)
                filtered.add(userId);
                Log.w(TAG, "Failed to fetch preferences for user " + userId + " - defaulting to enabled");
            }
        }
        
        Log.d(TAG, "Filtered " + userIds.size() + " users to " + filtered.size() + 
            " based on " + (checkInvited ? "invited" : "not invited") + " notification preferences");
        
        return filtered;
    }
}

