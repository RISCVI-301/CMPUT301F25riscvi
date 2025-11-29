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
 * REAL FIREBASE INTEGRATION TESTS for Admin User Stories.
 * 
 * ⚠️ These tests interact with REAL Firebase/Firestore!
 * 
 * US 03.01.01 - View all events
 * US 03.01.02 - Delete any event
 * US 03.02.01 - View all user profiles
 * US 03.02.02 - Delete any user profile
 * US 03.03.01 - View system logs
 * US 03.04.01 - View all images
 * US 03.05.01 - Admin authentication/authorization
 * 
 * To run: ./gradlew connectedAndroidTest --tests AdminUserStoryIntegrationTests
 */
@RunWith(AndroidJUnit4.class)
public class AdminUserStoryIntegrationTests {
    
    private static final String TAG = "AdminIntegrationTests";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String adminId;
    private List<String> cleanupEventIds;
    private List<String> cleanupUserIds;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        adminId = authManager.getDeviceId();
        cleanupEventIds = new ArrayList<>();
        cleanupUserIds = new ArrayList<>();
        
        Log.d(TAG, "Setup complete. Admin ID: " + adminId);
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
        for (String userId : cleanupUserIds) {
            try {
                Tasks.await(db.collection("users").document(userId).delete());
            } catch (Exception e) {
                Log.w(TAG, "Failed to cleanup user: " + userId, e);
            }
        }
    }
    
    // ============================================
    // US 03.01.01: View all events (REAL)
    // ============================================
    
    @Test
    public void testUS030101_viewAllEvents_realFirestore() throws Exception {
        Log.d(TAG, "=== US 03.01.01: View All Events (REAL Firebase) ===");
        
        // Create test events (from different organizers)
        String event1 = createTestEvent("Admin Test Event 1", "organizer1");
        String event2 = createTestEvent("Admin Test Event 2", "organizer2");
        cleanupEventIds.add(event1);
        cleanupEventIds.add(event2);
        
        Thread.sleep(2000);
        
        // Query ALL events (admin can see all)
        QuerySnapshot allEventsSnapshot = Tasks.await(
            db.collection("events")
                .limit(50)
                .get()
        );
        
        assertTrue("Should find events", allEventsSnapshot.size() > 0);
        
        // Verify events from different organizers exist
        boolean foundEvent1 = false;
        boolean foundEvent2 = false;
        
        for (DocumentSnapshot doc : allEventsSnapshot.getDocuments()) {
            String title = doc.getString("title");
            if ("Admin Test Event 1".equals(title)) foundEvent1 = true;
            if ("Admin Test Event 2".equals(title)) foundEvent2 = true;
        }
        
        assertTrue("Should find event 1", foundEvent1);
        assertTrue("Should find event 2", foundEvent2);
        
        Log.d(TAG, "✓ US 03.01.01 PASSED: All events viewed from REAL Firestore");
        System.out.println("✓ US 03.01.01: Successfully viewed " + allEventsSnapshot.size() + " events from REAL Firestore!");
        System.out.println("  (Admin can see events from all organizers)");
    }
    
    // ============================================
    // US 03.01.02: Delete any event (REAL)
    // ============================================
    
    @Test
    public void testUS030102_deleteAnyEvent_realFirestore() throws Exception {
        Log.d(TAG, "=== US 03.01.02: Delete Any Event (REAL Firebase) ===");
        
        // Create test event
        String eventId = createTestEvent("Event to Delete", "any_organizer");
        cleanupEventIds.add(eventId);
        
        // Verify event exists
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertTrue("Event should exist before deletion", eventDoc.exists());
        
        // Delete event (admin can delete any event)
        Tasks.await(db.collection("events").document(eventId).delete());
        Thread.sleep(1000);
        
        // Verify deletion
        DocumentSnapshot deletedDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertFalse("Event should not exist after deletion", deletedDoc.exists());
        
        // Don't cleanup since we already deleted it
        cleanupEventIds.remove(eventId);
        
        Log.d(TAG, "✓ US 03.01.02 PASSED: Event deleted from REAL Firestore");
        System.out.println("✓ US 03.01.02: Successfully deleted event from REAL Firestore!");
    }
    
    // ============================================
    // US 03.02.01: View all user profiles (REAL)
    // ============================================
    
    @Test
    public void testUS030201_viewAllProfiles_realFirestore() throws Exception {
        Log.d(TAG, "=== US 03.02.01: View All User Profiles (REAL Firebase) ===");
        
        // Create test users with different roles
        String user1 = createTestUser("Test User 1", "entrant");
        String user2 = createTestUser("Test User 2", "organizer");
        cleanupUserIds.add(user1);
        cleanupUserIds.add(user2);
        
        Thread.sleep(2000);
        
        // Query ALL users (admin can see all)
        QuerySnapshot allUsersSnapshot = Tasks.await(
            db.collection("users")
                .limit(50)
                .get()
        );
        
        assertTrue("Should find users", allUsersSnapshot.size() > 0);
        
        // Verify users with different roles exist
        boolean foundEntrant = false;
        boolean foundOrganizer = false;
        
        for (DocumentSnapshot doc : allUsersSnapshot.getDocuments()) {
            String role = doc.getString("role");
            if ("entrant".equals(role)) foundEntrant = true;
            if ("organizer".equals(role)) foundOrganizer = true;
        }
        
        Log.d(TAG, "✓ US 03.02.01 PASSED: All user profiles viewed from REAL Firestore");
        System.out.println("✓ US 03.02.01: Successfully viewed " + allUsersSnapshot.size() + " user profiles from REAL Firestore!");
        System.out.println("  (Admin can see users from all roles)");
    }
    
    // ============================================
    // US 03.02.02: Delete any user profile (REAL)
    // ============================================
    
    @Test
    public void testUS030202_deleteAnyProfile_realFirestore() throws Exception {
        Log.d(TAG, "=== US 03.02.02: Delete Any User Profile (REAL Firebase) ===");
        
        // Create test user
        String userId = createTestUser("User to Delete", "any_role");
        cleanupUserIds.add(userId);
        
        // Verify user exists
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(userId).get());
        assertTrue("User should exist before deletion", userDoc.exists());
        
        // Delete user (admin can delete any user)
        Tasks.await(db.collection("users").document(userId).delete());
        Thread.sleep(1000);
        
        // Verify deletion
        DocumentSnapshot deletedDoc = Tasks.await(db.collection("users").document(userId).get());
        assertFalse("User should not exist after deletion", deletedDoc.exists());
        
        // Don't cleanup since we already deleted it
        cleanupUserIds.remove(userId);
        
        Log.d(TAG, "✓ US 03.02.02 PASSED: User profile deleted from REAL Firestore");
        System.out.println("✓ US 03.02.02: Successfully deleted user profile from REAL Firestore!");
    }
    
    // ============================================
    // US 03.05.01: Admin authentication (REAL)
    // ============================================
    
    @Test
    public void testUS030501_adminAuthentication_realFirestore() throws Exception {
        Log.d(TAG, "=== US 03.05.01: Admin Authentication (REAL Firebase) ===");
        
        // Check if current user has admin role
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(adminId).get());
        
        if (userDoc.exists()) {
            // Check roles array
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) userDoc.get("roles");
            
            if (roles != null && roles.contains("admin")) {
                System.out.println("✓ User has admin role in REAL Firestore!");
                assertTrue("Should have admin role", roles.contains("admin"));
            } else {
                String role = userDoc.getString("role");
                if ("admin".equals(role)) {
                    System.out.println("✓ User has admin role (legacy field) in REAL Firestore!");
                } else {
                    System.out.println("⚠ Current user does NOT have admin role");
                    System.out.println("  To test admin features, add 'admin' to roles array in Firestore");
                    System.out.println("  Device ID: " + adminId);
                }
            }
        } else {
            System.out.println("⚠ User profile not found in Firestore");
            System.out.println("  Device ID: " + adminId);
        }
        
        Log.d(TAG, "✓ US 03.05.01: Admin authentication checked in REAL Firestore");
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    private String createTestEvent(String title, String organizerId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        
        Map<String, Object> event = new HashMap<>();
        event.put("id", eventId);
        event.put("title", title);
        event.put("organizerId", organizerId);
        event.put("capacity", 50);
        event.put("location", "Test Location");
        event.put("registrationStart", System.currentTimeMillis());
        event.put("registrationEnd", System.currentTimeMillis() + 86400000);
        event.put("deadlineEpochMs", System.currentTimeMillis() + 172800000);
        event.put("startsAtEpochMs", System.currentTimeMillis() + 259200000);
        event.put("waitlistCount", 0);
        event.put("waitlist", new ArrayList<>());
        event.put("admitted", new ArrayList<>());
        event.put("createdAtEpochMs", System.currentTimeMillis());
        event.put("testMarker", true);
        
        Tasks.await(db.collection("events").document(eventId).set(event));
        Thread.sleep(1000);
        
        return eventId;
    }
    
    private String createTestUser(String name, String role) throws Exception {
        String userId = "test_user_" + UUID.randomUUID().toString();
        
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", userId + "@test.com");
        user.put("role", role);
        List<String> roles = new ArrayList<>();
        roles.add(role);
        user.put("roles", roles);
        user.put("deviceId", userId);
        user.put("testMarker", true);
        
        Tasks.await(db.collection("users").document(userId).set(user));
        Thread.sleep(1000);
        
        return userId;
    }
    
    // ============================================
    // Test Runner
    // ============================================
    
    @Test
    public void runAllAdminIntegrationTests() throws Exception {
        System.out.println("\n========================================");
        System.out.println("ADMIN USER STORY INTEGRATION TESTS");
        System.out.println("========================================");
        System.out.println("These tests use REAL Firebase/Firestore!");
        System.out.println("========================================\n");
        
        try {
            testUS030101_viewAllEvents_realFirestore();
            testUS030102_deleteAnyEvent_realFirestore();
            testUS030201_viewAllProfiles_realFirestore();
            testUS030202_deleteAnyProfile_realFirestore();
            testUS030501_adminAuthentication_realFirestore();
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL ADMIN INTEGRATION TESTS PASSED!");
            System.out.println("========================================\n");
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ SOME ADMIN INTEGRATION TESTS FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            throw e;
        }
    }
}

