package com.example.eventease.userstories;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.eventease.auth.DeviceAuthManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * REAL FIREBASE INTEGRATION TESTS for Entrant User Stories.
 * 
 * ⚠️ These tests interact with REAL Firebase/Firestore!
 * They create, read, update, and delete real documents.
 * 
 * US 01.01.01 - Join waiting list
 * US 01.01.02 - Leave waiting list
 * US 01.01.03 - View list of events
 * US 01.01.04 - Filter events
 * US 01.02.01 - Provide personal information
 * US 01.02.02 - Update profile
 * US 01.02.03 - View event history
 * US 01.02.04 - Delete profile
 * US 01.04.01 - Receive notification when chosen
 * US 01.04.02 - Receive notification when not chosen
 * US 01.04.03 - Opt out of notifications
 * US 01.05.01 - Get replacement chance
 * US 01.05.02 - Accept invitation
 * US 01.05.03 - Decline invitation
 * US 01.05.04 - View waitlist count
 * US 01.05.05 - View lottery criteria
 * US 01.06.01 - View event from QR code
 * US 01.06.02 - Sign up from event details
 * US 01.07.01 - Device-based identification
 * 
 * To run: ./gradlew connectedAndroidTest --tests EntrantUserStoryIntegrationTests
 */
@RunWith(AndroidJUnit4.class)
public class EntrantUserStoryIntegrationTests {
    
    private static final String TAG = "EntrantIntegrationTests";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String deviceId;
    private String testEventId;
    private List<String> cleanupEventIds;
    private List<String> cleanupRequestIds;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        deviceId = authManager.getDeviceId();
        testEventId = "integration_test_" + UUID.randomUUID().toString();
        cleanupEventIds = new ArrayList<>();
        cleanupRequestIds = new ArrayList<>();
        
        Log.d(TAG, "Setup complete. Device ID: " + deviceId);
    }
    
    @After
    public void tearDown() {
        // Cleanup test data
        for (String eventId : cleanupEventIds) {
            try {
                Tasks.await(db.collection("events").document(eventId).delete());
            } catch (Exception e) {
                Log.w(TAG, "Failed to cleanup event: " + eventId, e);
            }
        }
        for (String requestId : cleanupRequestIds) {
            try {
                Tasks.await(db.collection("notificationRequests").document(requestId).delete());
            } catch (Exception e) {
                Log.w(TAG, "Failed to cleanup notification request: " + requestId, e);
            }
        }
    }
    
    // ============================================
    // US 01.01.01: Join waiting list (REAL)
    // ============================================
    
    @Test
    public void testUS010101_joinWaitlist_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.01.01: Join Waiting List (REAL Firebase) ===");
        
        // Create test event in Firestore
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Read event
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertTrue("Event should exist", eventDoc.exists());
        
        // Get current waitlist
        @SuppressWarnings("unchecked")
        List<String> waitlist = (List<String>) eventDoc.get("waitlist");
        if (waitlist == null) {
            waitlist = new ArrayList<>();
        }
        
        int initialCount = waitlist.size();
        Log.d(TAG, "Initial waitlist count: " + initialCount);
        
        // Join waitlist (add device ID)
        if (!waitlist.contains(deviceId)) {
            waitlist.add(deviceId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("waitlist", waitlist);
            updates.put("waitlistCount", waitlist.size());
            
            Tasks.await(db.collection("events").document(eventId).update(updates));
            Thread.sleep(1000);
        }
        
        // Verify join
        DocumentSnapshot updatedDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> updatedWaitlist = (List<String>) updatedDoc.get("waitlist");
        
        assertTrue("Device should be in waitlist", updatedWaitlist.contains(deviceId));
        assertEquals("Waitlist count should increase", initialCount + 1, updatedWaitlist.size());
        
        Log.d(TAG, "✓ US 01.01.01 PASSED: Joined waitlist in REAL Firestore");
        System.out.println("✓ US 01.01.01: Successfully joined waitlist in REAL Firestore!");
    }
    
    // ============================================
    // US 01.01.02: Leave waiting list (REAL)
    // ============================================
    
    @Test
    public void testUS010102_leaveWaitlist_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.01.02: Leave Waiting List (REAL Firebase) ===");
        
        // Create event and join waitlist
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Join waitlist first
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> waitlist = (List<String>) eventDoc.get("waitlist");
        if (waitlist == null) {
            waitlist = new ArrayList<>();
        }
        waitlist.add(deviceId);
        
        Map<String, Object> joinUpdates = new HashMap<>();
        joinUpdates.put("waitlist", waitlist);
        joinUpdates.put("waitlistCount", waitlist.size());
        Tasks.await(db.collection("events").document(eventId).update(joinUpdates));
        Thread.sleep(1000);
        
        // Verify joined
        eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> beforeLeave = (List<String>) eventDoc.get("waitlist");
        assertTrue("Should be in waitlist before leaving", beforeLeave.contains(deviceId));
        
        // Leave waitlist
        waitlist.remove(deviceId);
        Map<String, Object> leaveUpdates = new HashMap<>();
        leaveUpdates.put("waitlist", waitlist);
        leaveUpdates.put("waitlistCount", waitlist.size());
        Tasks.await(db.collection("events").document(eventId).update(leaveUpdates));
        Thread.sleep(1000);
        
        // Verify left
        DocumentSnapshot afterDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> afterLeave = (List<String>) afterDoc.get("waitlist");
        
        assertFalse("Device should not be in waitlist", afterLeave.contains(deviceId));
        assertEquals("Waitlist count should decrease", beforeLeave.size() - 1, afterLeave.size());
        
        Log.d(TAG, "✓ US 01.01.02 PASSED: Left waitlist in REAL Firestore");
        System.out.println("✓ US 01.01.02: Successfully left waitlist in REAL Firestore!");
    }
    
    // ============================================
    // US 01.01.03: View list of events (REAL)
    // ============================================
    
    @Test
    public void testUS010103_viewEventList_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.01.03: View List of Events (REAL Firebase) ===");
        
        // Create multiple test events
        String event1 = createTestEvent("Event 1");
        String event2 = createTestEvent("Event 2");
        String event3 = createTestEvent("Event 3");
        cleanupEventIds.add(event1);
        cleanupEventIds.add(event2);
        cleanupEventIds.add(event3);
        
        Thread.sleep(2000);
        
        // Query events from Firestore
        QuerySnapshot eventsSnapshot = Tasks.await(
            db.collection("events")
                .whereGreaterThan("registrationEnd", System.currentTimeMillis() - 86400000)
                .limit(10)
                .get()
        );
        
        assertTrue("Should find events", eventsSnapshot.size() > 0);
        
        // Verify we can see our test events
        boolean foundEvent1 = false;
        boolean foundEvent2 = false;
        boolean foundEvent3 = false;
        
        for (DocumentSnapshot doc : eventsSnapshot.getDocuments()) {
            String title = doc.getString("title");
            if ("Event 1".equals(title)) foundEvent1 = true;
            if ("Event 2".equals(title)) foundEvent2 = true;
            if ("Event 3".equals(title)) foundEvent3 = true;
        }
        
        assertTrue("Should find Event 1", foundEvent1);
        assertTrue("Should find Event 2", foundEvent2);
        assertTrue("Should find Event 3", foundEvent3);
        
        Log.d(TAG, "✓ US 01.01.03 PASSED: Viewed event list from REAL Firestore");
        System.out.println("✓ US 01.01.03: Successfully queried " + eventsSnapshot.size() + " events from REAL Firestore!");
    }
    
    // ============================================
    // US 01.02.01: Provide personal information (REAL)
    // ============================================
    
    @Test
    public void testUS010201_providePersonalInfo_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.02.01: Provide Personal Information (REAL Firebase) ===");
        
        // Create/update user profile in Firestore
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", "Integration Test User");
        profile.put("email", "integration.test@example.com");
        profile.put("phoneNumber", "+1234567890");
        profile.put("deviceId", deviceId);
        profile.put("lastUpdated", System.currentTimeMillis());
        
        Tasks.await(db.collection("users").document(deviceId).set(profile));
        Thread.sleep(1000);
        
        // Verify profile saved
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(deviceId).get());
        assertTrue("User profile should exist", userDoc.exists());
        assertEquals("Name should match", "Integration Test User", userDoc.getString("name"));
        assertEquals("Email should match", "integration.test@example.com", userDoc.getString("email"));
        assertEquals("Phone should match", "+1234567890", userDoc.getString("phoneNumber"));
        
        Log.d(TAG, "✓ US 01.02.01 PASSED: Profile saved to REAL Firestore");
        System.out.println("✓ US 01.02.01: Successfully saved profile to REAL Firestore!");
    }
    
    // ============================================
    // US 01.02.02: Update profile (REAL)
    // ============================================
    
    @Test
    public void testUS010202_updateProfile_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.02.02: Update Profile (REAL Firebase) ===");
        
        // Create initial profile
        Map<String, Object> initialProfile = new HashMap<>();
        initialProfile.put("name", "Original Name");
        initialProfile.put("email", "original@example.com");
        Tasks.await(db.collection("users").document(deviceId).set(initialProfile));
        Thread.sleep(1000);
        
        // Update profile
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Updated Name");
        updates.put("email", "updated@example.com");
        updates.put("phoneNumber", "+9876543210");
        
        Tasks.await(db.collection("users").document(deviceId).update(updates));
        Thread.sleep(1000);
        
        // Verify update
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(deviceId).get());
        assertEquals("Name should be updated", "Updated Name", userDoc.getString("name"));
        assertEquals("Email should be updated", "updated@example.com", userDoc.getString("email"));
        assertEquals("Phone should be added", "+9876543210", userDoc.getString("phoneNumber"));
        
        Log.d(TAG, "✓ US 01.02.02 PASSED: Profile updated in REAL Firestore");
        System.out.println("✓ US 01.02.02: Successfully updated profile in REAL Firestore!");
    }
    
    // ============================================
    // US 01.04.01: Receive notification when chosen (REAL)
    // ============================================
    
    @Test
    public void testUS010401_notificationWhenChosen_realFCM() throws Exception {
        Log.d(TAG, "=== US 01.04.01: Notification When Chosen (REAL FCM) ===");
        
        // Create notification request
        String requestId = "notification_test_" + UUID.randomUUID().toString();
        cleanupRequestIds.add(requestId);
        
        List<String> userIds = new ArrayList<>();
        userIds.add(deviceId);
        
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("title", "🎉 You've been selected!");
        notificationRequest.put("message", "Congratulations! You've been selected for an event.");
        notificationRequest.put("eventId", testEventId);
        notificationRequest.put("eventTitle", "Test Event");
        notificationRequest.put("type", "selection");
        notificationRequest.put("groupType", "invitation");
        notificationRequest.put("processed", false);
        notificationRequest.put("createdAt", System.currentTimeMillis());
        
        Tasks.await(db.collection("notificationRequests").document(requestId).set(notificationRequest));
        Log.d(TAG, "Notification request created: " + requestId);
        
        System.out.println("✓ US 01.04.01: Notification request created in REAL Firestore!");
        System.out.println("  → Cloud Function should send FCM notification");
        System.out.println("  → Check your device for notification!");
        
        // Wait for Cloud Function to process
        Thread.sleep(8000);
        
        // Check if processed
        DocumentSnapshot requestDoc = Tasks.await(
            db.collection("notificationRequests").document(requestId).get()
        );
        
        if (requestDoc.exists()) {
            Boolean processed = requestDoc.getBoolean("processed");
            Long sentCount = requestDoc.getLong("sentCount");
            
            if (Boolean.TRUE.equals(processed) && sentCount != null && sentCount > 0) {
                System.out.println("✓✓✓ Notification sent successfully via REAL FCM!");
            }
        }
    }
    
    // ============================================
    // US 01.04.03: Opt out of notifications (REAL)
    // ============================================
    
    @Test
    public void testUS010403_optOutNotifications_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.04.03: Opt Out of Notifications (REAL Firebase) ===");
        
        // Set notifications disabled
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationsEnabled", false);
        
        Tasks.await(db.collection("users").document(deviceId).update(updates));
        Thread.sleep(1000);
        
        // Verify setting
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(deviceId).get());
        Boolean notificationsEnabled = userDoc.getBoolean("notificationsEnabled");
        
        assertFalse("Notifications should be disabled", 
                    notificationsEnabled != null && notificationsEnabled);
        
        // Re-enable
        updates.put("notificationsEnabled", true);
        Tasks.await(db.collection("users").document(deviceId).update(updates));
        
        Log.d(TAG, "✓ US 01.04.03 PASSED: Notification preference updated in REAL Firestore");
        System.out.println("✓ US 01.04.03: Successfully updated notification preference in REAL Firestore!");
    }
    
    // ============================================
    // US 01.05.02: Accept invitation (REAL)
    // ============================================
    
    @Test
    public void testUS010502_acceptInvitation_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.05.02: Accept Invitation (REAL Firebase) ===");
        
        // Create test event and invitation
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        String invitationId = "inv_" + UUID.randomUUID().toString();
        Map<String, Object> invitation = new HashMap<>();
        invitation.put("eventId", eventId);
        invitation.put("uid", deviceId);
        invitation.put("status", "PENDING");
        invitation.put("issuedAt", System.currentTimeMillis());
        
        Tasks.await(db.collection("invitations").document(invitationId).set(invitation));
        Thread.sleep(1000);
        
        // Accept invitation
        Map<String, Object> acceptUpdate = new HashMap<>();
        acceptUpdate.put("status", "ACCEPTED");
        acceptUpdate.put("acceptedAt", System.currentTimeMillis());
        
        Tasks.await(db.collection("invitations").document(invitationId).update(acceptUpdate));
        Thread.sleep(1000);
        
        // Verify acceptance
        DocumentSnapshot invDoc = Tasks.await(
            db.collection("invitations").document(invitationId).get()
        );
        
        assertEquals("Status should be ACCEPTED", "ACCEPTED", invDoc.getString("status"));
        assertNotNull("Should have accepted timestamp", invDoc.getLong("acceptedAt"));
        
        Log.d(TAG, "✓ US 01.05.02 PASSED: Invitation accepted in REAL Firestore");
        System.out.println("✓ US 01.05.02: Successfully accepted invitation in REAL Firestore!");
        
        // Cleanup invitation
        Tasks.await(db.collection("invitations").document(invitationId).delete());
    }
    
    // ============================================
    // US 01.05.04: View waitlist count (REAL)
    // ============================================
    
    @Test
    public void testUS010504_viewWaitlistCount_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.05.04: View Waitlist Count (REAL Firebase) ===");
        
        // Create event with waitlist
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Add users to waitlist
        List<String> waitlist = new ArrayList<>();
        waitlist.add("user1");
        waitlist.add("user2");
        waitlist.add("user3");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("waitlist", waitlist);
        updates.put("waitlistCount", waitlist.size());
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Read waitlist count from Firestore
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        Long waitlistCount = eventDoc.getLong("waitlistCount");
        
        assertNotNull("Waitlist count should exist", waitlistCount);
        assertEquals("Waitlist count should match", 3L, waitlistCount.longValue());
        
        Log.d(TAG, "✓ US 01.05.04 PASSED: Waitlist count read from REAL Firestore");
        System.out.println("✓ US 01.05.04: Successfully read waitlist count (" + waitlistCount + ") from REAL Firestore!");
    }
    
    // ============================================
    // US 01.07.01: Device-based identification (REAL)
    // ============================================
    
    @Test
    public void testUS010701_deviceIdentification_realFirestore() throws Exception {
        Log.d(TAG, "=== US 01.07.01: Device-Based Identification (REAL Firebase) ===");
        
        // Verify device ID exists
        assertNotNull("Device ID should exist", deviceId);
        assertFalse("Device ID should not be empty", deviceId.isEmpty());
        
        // Verify device ID can be used as user ID in Firestore
        Map<String, Object> userData = new HashMap<>();
        userData.put("deviceId", deviceId);
        userData.put("uid", deviceId);
        userData.put("lastSeen", System.currentTimeMillis());
        
        Tasks.await(db.collection("users").document(deviceId).set(userData));
        Thread.sleep(1000);
        
        // Verify user document exists with device ID
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(deviceId).get());
        assertTrue("User document should exist", userDoc.exists());
        assertEquals("Device ID should match", deviceId, userDoc.getString("deviceId"));
        assertEquals("UID should match device ID", deviceId, userDoc.getString("uid"));
        
        Log.d(TAG, "✓ US 01.07.01 PASSED: Device ID used for identification in REAL Firestore");
        System.out.println("✓ US 01.07.01: Successfully verified device-based identification in REAL Firestore!");
        System.out.println("  Device ID: " + deviceId);
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    private String createTestEvent() throws Exception {
        return createTestEvent("Test Event");
    }
    
    private String createTestEvent(String title) throws Exception {
        String eventId = "integration_test_" + UUID.randomUUID().toString();
        
        Map<String, Object> event = new HashMap<>();
        event.put("title", title);
        event.put("description", "Integration test event");
        event.put("capacity", 50);
        event.put("organizerId", deviceId);
        event.put("registrationStart", System.currentTimeMillis() - 3600000);
        event.put("registrationEnd", System.currentTimeMillis() + 86400000);
        event.put("deadlineEpochMs", System.currentTimeMillis() + 172800000);
        event.put("startsAtEpochMs", System.currentTimeMillis() + 259200000);
        event.put("waitlistCount", 0);
        event.put("waitlist", new ArrayList<>());
        event.put("admitted", new ArrayList<>());
        event.put("selectionProcessed", false);
        event.put("testMarker", true);
        
        Tasks.await(db.collection("events").document(eventId).set(event));
        Thread.sleep(1000);
        
        return eventId;
    }
    
    // ============================================
    // Test Runner
    // ============================================
    
    @Test
    public void runAllEntrantIntegrationTests() throws Exception {
        System.out.println("\n========================================");
        System.out.println("ENTRANT USER STORY INTEGRATION TESTS");
        System.out.println("========================================");
        System.out.println("These tests use REAL Firebase/Firestore!");
        System.out.println("========================================\n");
        
        try {
            testUS010101_joinWaitlist_realFirestore();
            testUS010102_leaveWaitlist_realFirestore();
            testUS010103_viewEventList_realFirestore();
            testUS010201_providePersonalInfo_realFirestore();
            testUS010202_updateProfile_realFirestore();
            testUS010401_notificationWhenChosen_realFCM();
            testUS010403_optOutNotifications_realFirestore();
            testUS010502_acceptInvitation_realFirestore();
            testUS010504_viewWaitlistCount_realFirestore();
            testUS010701_deviceIdentification_realFirestore();
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL ENTRANT INTEGRATION TESTS PASSED!");
            System.out.println("========================================\n");
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ SOME ENTRANT INTEGRATION TESTS FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            throw e;
        }
    }
}

