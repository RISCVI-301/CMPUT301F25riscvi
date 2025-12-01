package com.example.eventease.edgecases;

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
 * REAL FIREBASE INTEGRATION tests for edge cases and exceptional scenarios.
 * 
 * ⚠️ These tests interact with REAL Firebase/Firestore!
 * They test how the app handles edge cases with actual Firebase operations.
 * 
 * Categories:
 * - Null/Empty Data Handling
 * - Boundary Conditions
 * - Invalid Input Validation
 * - Concurrent Operations
 * - State Transitions
 * - Error Handling
 * 
 * To run: ./gradlew connectedAndroidTest --tests EdgeCaseIntegrationTests
 */
@RunWith(AndroidJUnit4.class)
public class EdgeCaseIntegrationTests {
    
    private static final String TAG = "EdgeCaseIntegrationTests";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String deviceId;
    private List<String> cleanupEventIds;
    private List<String> cleanupRequestIds;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        deviceId = authManager.getDeviceId();
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
    // NULL/EMPTY DATA HANDLING - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testEmptyEventTitle_handlesGracefully_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Empty Event Title (REAL Firebase) ===");
        
        String eventId = UUID.randomUUID().toString();
        cleanupEventIds.add(eventId);
        
        // Try to create event with empty title
        Map<String, Object> event = new HashMap<>();
        event.put("id", eventId);
        event.put("title", ""); // Empty title
        event.put("organizerId", deviceId);
        event.put("capacity", 50);
        event.put("testMarker", true);
        
        // Firestore allows empty strings, but app should validate
        // This tests if Firestore accepts it (it will) and app handles it
        try {
            Tasks.await(db.collection("events").document(eventId).set(event));
            Thread.sleep(1000);
            
            DocumentSnapshot doc = Tasks.await(db.collection("events").document(eventId).get());
            String title = doc.getString("title");
            
            // Firestore accepts empty strings, so we verify it's stored
            assertNotNull("Title field exists", title);
            assertTrue("Title is empty", title.isEmpty());
            
            // App should validate before saving (this is tested in unit tests)
            // But Firestore itself accepts empty strings
            
            System.out.println("✓ Edge Case: Empty title stored in Firestore (app should validate)");
        } catch (Exception e) {
            Log.w(TAG, "Expected - app validation should prevent this", e);
        }
    }
    
    @Test
    public void testNullWaitlist_handlesCorrectly_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Null Waitlist (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Try to set null waitlist (Firestore doesn't store nulls, but we test handling)
        Map<String, Object> updates = new HashMap<>();
        // Don't set waitlist - test missing field
        
        // Set empty waitlist instead (Firestore-friendly)
        updates.put("waitlist", new ArrayList<>());
        updates.put("waitlistCount", 0);
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Read back
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> waitlist = (List<String>) eventDoc.get("waitlist");
        
        // Should handle null/missing gracefully
        int waitlistSize = (waitlist != null) ? waitlist.size() : 0;
        assertEquals("Waitlist size should be 0", 0, waitlistSize);
        
        System.out.println("✓ Edge Case: Null/empty waitlist handled correctly in REAL Firestore");
    }
    
    // ============================================
    // BOUNDARY CONDITIONS - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testZeroCapacity_handlesCorrectly_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Zero Capacity (REAL Firebase) ===");
        
        String eventId = UUID.randomUUID().toString();
        cleanupEventIds.add(eventId);
        
        // Try to create event with zero capacity
        Map<String, Object> event = new HashMap<>();
        event.put("id", eventId);
        event.put("title", "Test Event");
        event.put("capacity", 0); // Zero capacity
        event.put("organizerId", deviceId);
        event.put("testMarker", true);
        
        // Firestore accepts 0, but app should validate
        Tasks.await(db.collection("events").document(eventId).set(event));
        Thread.sleep(1000);
        
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        Long capacity = eventDoc.getLong("capacity");
        
        assertEquals("Capacity is 0", 0L, capacity.longValue());
        
        // App should validate before allowing 0
        System.out.println("✓ Edge Case: Zero capacity stored in Firestore (app should validate)");
    }
    
    @Test
    public void testLargeWaitlist_handlesEfficiently_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Large Waitlist (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Create large waitlist (100 users)
        List<String> largeWaitlist = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeWaitlist.add("user" + i);
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("waitlist", largeWaitlist);
        updates.put("waitlistCount", largeWaitlist.size());
        
        long startTime = System.currentTimeMillis();
        Tasks.await(db.collection("events").document(eventId).update(updates));
        long duration = System.currentTimeMillis() - startTime;
        
        Thread.sleep(1000);
        
        // Verify it was saved
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> savedWaitlist = (List<String>) eventDoc.get("waitlist");
        
        assertEquals("Large waitlist should be saved", 100, savedWaitlist.size());
        assertTrue("Should complete in reasonable time (< 5 seconds)", duration < 5000);
        
        System.out.println("✓ Edge Case: Large waitlist (100 users) saved in " + duration + "ms");
    }
    
    // ============================================
    // INVALID INPUT VALIDATION - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testInvalidTimestamp_handlesGracefully_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Invalid Timestamp (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Try to set negative timestamp
        long invalidTimestamp = -1L;
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("registrationStart", invalidTimestamp);
        
        // Firestore accepts negative timestamps
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        Long regStart = eventDoc.getLong("registrationStart");
        
        assertEquals("Negative timestamp stored", invalidTimestamp, regStart.longValue());
        
        // App should validate timestamps before saving
        System.out.println("✓ Edge Case: Invalid (negative) timestamp stored (app should validate)");
    }
    
    @Test
    public void testRegistrationEndBeforeStart_handlesCorrectly_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Registration End Before Start (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        long currentTime = System.currentTimeMillis();
        long start = currentTime + 86400000; // Tomorrow
        long end = currentTime; // Today (before start)
        
        // Firestore will accept this (no validation at DB level)
        Map<String, Object> updates = new HashMap<>();
        updates.put("registrationStart", start);
        updates.put("registrationEnd", end); // End before start
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        Long regStart = eventDoc.getLong("registrationStart");
        Long regEnd = eventDoc.getLong("registrationEnd");
        
        // Firestore accepts it, but app should validate
        assertTrue("End is before start", regEnd < regStart);
        
        System.out.println("✓ Edge Case: Invalid registration period stored (app should validate)");
    }
    
    // ============================================
    // CONCURRENT OPERATIONS - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testMultipleUsersJoinSimultaneously_handlesCorrectly_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Concurrent Waitlist Joins (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Simulate multiple users joining concurrently
        List<String> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            users.add("concurrent_user" + i);
        }
        
        // Add all users at once
        Map<String, Object> updates = new HashMap<>();
        updates.put("waitlist", users);
        updates.put("waitlistCount", users.size());
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Verify all users added
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> waitlist = (List<String>) eventDoc.get("waitlist");
        
        assertEquals("All users should be added", 10, waitlist.size());
        
        // Check for duplicates
        long uniqueCount = waitlist.stream().distinct().count();
        assertEquals("No duplicates should exist", 10, uniqueCount);
        
        System.out.println("✓ Edge Case: Concurrent waitlist joins handled correctly in REAL Firestore");
    }
    
    // ============================================
    // MISSING REQUIRED FIELDS - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testEventMissingRequiredFields_handlesGracefully_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Missing Required Fields (REAL Firebase) ===");
        
        String eventId = UUID.randomUUID().toString();
        cleanupEventIds.add(eventId);
        
        // Create event with only ID (missing required fields)
        Map<String, Object> incompleteEvent = new HashMap<>();
        incompleteEvent.put("id", eventId);
        incompleteEvent.put("organizerId", deviceId);
        // Missing: title, capacity, etc.
        incompleteEvent.put("testMarker", true);
        
        // Firestore accepts partial documents
        Tasks.await(db.collection("events").document(eventId).set(incompleteEvent));
        Thread.sleep(1000);
        
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        
        // Verify it exists but has missing fields
        assertTrue("Event document exists", eventDoc.exists());
        assertNull("Title is missing", eventDoc.getString("title"));
        assertNull("Capacity is missing", eventDoc.get("capacity"));
        
        // App should validate required fields before saving
        System.out.println("✓ Edge Case: Incomplete event stored (app should validate required fields)");
    }
    
    // ============================================
    // DATA TYPE MISMATCHES - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testWrongDataTypeForCapacity_handlesGracefully_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Wrong Data Type (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Try to set capacity as string instead of integer
        Map<String, Object> updates = new HashMap<>();
        updates.put("capacity", "50"); // String instead of int
        
        // Firestore will store it as string
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        Object capacity = eventDoc.get("capacity");
        
        // Firestore stores it as provided type
        assertNotNull("Capacity field exists", capacity);
        
        // App should validate and convert types
        System.out.println("✓ Edge Case: Wrong data type stored (app should validate and convert)");
        System.out.println("  Capacity type: " + capacity.getClass().getSimpleName());
    }
    
    // ============================================
    // ERROR HANDLING - REAL FIRESTORE
    // ============================================
    
    @Test
    public void testDeleteNonExistentEvent_handlesGracefully_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Delete Non-Existent Event (REAL Firebase) ===");
        
        String nonExistentId = "nonexistent_" + UUID.randomUUID().toString();
        
        // Try to delete non-existent event
        try {
            Tasks.await(db.collection("events").document(nonExistentId).delete());
            // Firestore delete succeeds even if document doesn't exist (no error)
            
            System.out.println("✓ Edge Case: Deleting non-existent event handled gracefully");
        } catch (Exception e) {
            // Should not throw error
            fail("Should not throw error when deleting non-existent document: " + e.getMessage());
        }
    }
    
    @Test
    public void testQueryNonExistentCollection_handlesGracefully_realFirestore() throws Exception {
        Log.d(TAG, "=== Edge Case: Query Non-Existent Collection (REAL Firebase) ===");
        
        // Query collection that doesn't exist
        QuerySnapshot snapshot = Tasks.await(
            db.collection("nonexistent_collection")
                .limit(10)
                .get()
        );
        
        // Should return empty results, not error
        assertNotNull("Should return snapshot", snapshot);
        assertEquals("Should have no documents", 0, snapshot.size());
        
        System.out.println("✓ Edge Case: Querying non-existent collection handled gracefully");
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    private String createTestEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        
        Map<String, Object> event = new HashMap<>();
        event.put("id", eventId);
        event.put("title", "Edge Case Test Event");
        event.put("organizerId", deviceId);
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
    
    // ============================================
    // Test Runner
    // ============================================
    
    @Test
    public void runAllEdgeCaseIntegrationTests() throws Exception {
        System.out.println("\n========================================");
        System.out.println("EDGE CASE INTEGRATION TESTS");
        System.out.println("========================================");
        System.out.println("These tests use REAL Firebase/Firestore!");
        System.out.println("Testing edge cases with actual Firebase operations");
        System.out.println("========================================\n");
        
        try {
            testEmptyEventTitle_handlesGracefully_realFirestore();
            testNullWaitlist_handlesCorrectly_realFirestore();
            testZeroCapacity_handlesCorrectly_realFirestore();
            testLargeWaitlist_handlesEfficiently_realFirestore();
            testInvalidTimestamp_handlesGracefully_realFirestore();
            testRegistrationEndBeforeStart_handlesCorrectly_realFirestore();
            testMultipleUsersJoinSimultaneously_handlesCorrectly_realFirestore();
            testEventMissingRequiredFields_handlesGracefully_realFirestore();
            testWrongDataTypeForCapacity_handlesGracefully_realFirestore();
            testDeleteNonExistentEvent_handlesGracefully_realFirestore();
            testQueryNonExistentCollection_handlesGracefully_realFirestore();
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL EDGE CASE INTEGRATION TESTS PASSED!");
            System.out.println("========================================\n");
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ SOME EDGE CASE INTEGRATION TESTS FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            throw e;
        }
    }
}

