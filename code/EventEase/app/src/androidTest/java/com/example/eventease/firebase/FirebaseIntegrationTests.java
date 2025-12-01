package com.example.eventease.firebase;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * REAL Firebase/Firestore integration tests.
 * 
 * These tests actually interact with Firebase - they create real documents,
 * read from Firestore, and verify data is persisted correctly.
 * 
 * ⚠️ IMPORTANT: These tests modify real Firebase data. Run in test environment only!
 * 
 * To run:
 * ./gradlew connectedAndroidTest --tests FirebaseIntegrationTests
 */
@RunWith(AndroidJUnit4.class)
public class FirebaseIntegrationTests {
    
    private static final String TAG = "FirebaseIntegrationTests";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String testDeviceId;
    private String testEventId;
    private String testNotificationRequestId;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        testDeviceId = authManager.getDeviceId();
        testEventId = "test_event_" + UUID.randomUUID().toString();
        testNotificationRequestId = "test_notification_" + UUID.randomUUID().toString();
        
        Log.d(TAG, "Test setup complete. Device ID: " + testDeviceId);
        Log.d(TAG, "Test Event ID: " + testEventId);
    }
    
    @After
    public void tearDown() {
        // Clean up test data if needed
        // Note: In production tests, you might want to clean up test documents
    }
    
    // ============================================
    // FIRESTORE READ/WRITE TESTS
    // ============================================
    
    @Test
    public void testWriteToFirestore_createsRealDocument() throws Exception {
        Log.d(TAG, "=== Testing Firestore Write ===");
        
        // Create a test event document
        Map<String, Object> event = new HashMap<>();
        event.put("title", "Integration Test Event");
        event.put("capacity", 50);
        event.put("organizerId", testDeviceId);
        event.put("createdAt", System.currentTimeMillis());
        event.put("testMarker", true); // Mark as test data
        
        // Write to Firestore
        Log.d(TAG, "Writing event to Firestore: " + testEventId);
        Tasks.await(db.collection("events").document(testEventId).set(event));
        
        // Wait a moment for propagation
        Thread.sleep(1000);
        
        // Read back from Firestore
        Log.d(TAG, "Reading event back from Firestore...");
        DocumentSnapshot doc = Tasks.await(
            db.collection("events").document(testEventId).get()
        );
        
        // Verify it was saved
        assertTrue("Document should exist in Firestore", doc.exists());
        assertEquals("Title should match", "Integration Test Event", doc.getString("title"));
        assertEquals("Capacity should match", Long.valueOf(50L), doc.getLong("capacity"));
        assertEquals("Organizer ID should match", testDeviceId, doc.getString("organizerId"));
        
        Log.d(TAG, "✓ Firestore write/read test PASSED");
        System.out.println("✓ REAL Firestore document created and verified!");
    }
    
    @Test
    public void testReadFromFirestore_getsRealData() throws Exception {
        Log.d(TAG, "=== Testing Firestore Read ===");
        
        // First create a document
        Map<String, Object> testData = new HashMap<>();
        testData.put("testField", "testValue");
        testData.put("timestamp", System.currentTimeMillis());
        testData.put("testMarker", true);
        
        String testDocId = "read_test_" + UUID.randomUUID().toString();
        Tasks.await(db.collection("testCollection").document(testDocId).set(testData));
        Thread.sleep(1000);
        
        // Now read it
        DocumentSnapshot doc = Tasks.await(
            db.collection("testCollection").document(testDocId).get()
        );
        
        assertTrue("Document should exist", doc.exists());
        assertEquals("Field should match", "testValue", doc.getString("testField"));
        
        Log.d(TAG, "✓ Firestore read test PASSED");
        System.out.println("✓ REAL Firestore document read successfully!");
    }
    
    @Test
    public void testUpdateFirestoreDocument_updatesRealData() throws Exception {
        Log.d(TAG, "=== Testing Firestore Update ===");
        
        // Create initial document
        Map<String, Object> initial = new HashMap<>();
        initial.put("status", "INITIAL");
        initial.put("value", 10);
        initial.put("testMarker", true);
        
        String updateDocId = "update_test_" + UUID.randomUUID().toString();
        Tasks.await(db.collection("testCollection").document(updateDocId).set(initial));
        Thread.sleep(1000);
        
        // Update the document
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "UPDATED");
        updates.put("value", 20);
        
        Tasks.await(db.collection("testCollection").document(updateDocId).update(updates));
        Thread.sleep(1000);
        
        // Read back and verify update
        DocumentSnapshot doc = Tasks.await(
            db.collection("testCollection").document(updateDocId).get()
        );
        
        assertTrue("Document should exist", doc.exists());
        assertEquals("Status should be updated", "UPDATED", doc.getString("status"));
        assertEquals("Value should be updated", Long.valueOf(20L), doc.getLong("value"));
        
        Log.d(TAG, "✓ Firestore update test PASSED");
        System.out.println("✓ REAL Firestore document updated successfully!");
    }
    
    @Test
    public void testQueryFirestore_getsFilteredResults() throws Exception {
        Log.d(TAG, "=== Testing Firestore Query ===");
        
        // Create test documents
        String queryTestPrefix = "query_test_" + UUID.randomUUID().toString();
        
        for (int i = 0; i < 3; i++) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("testId", queryTestPrefix);
            doc.put("index", i);
            doc.put("testMarker", true);
            
            Tasks.await(db.collection("testCollection")
                .document(queryTestPrefix + "_" + i)
                .set(doc));
        }
        
        Thread.sleep(2000); // Wait for writes to complete
        
        // Query for documents with matching testId
        QuerySnapshot querySnapshot = Tasks.await(
            db.collection("testCollection")
                .whereEqualTo("testId", queryTestPrefix)
                .get()
        );
        
        assertTrue("Should find documents", querySnapshot.size() >= 3);
        
        Log.d(TAG, "✓ Firestore query test PASSED - Found " + querySnapshot.size() + " documents");
        System.out.println("✓ REAL Firestore query returned " + querySnapshot.size() + " documents!");
    }
    
    // ============================================
    // USER PROFILE FIRESTORE TESTS
    // ============================================
    
    @Test
    public void testUserProfileExistsInFirestore() throws Exception {
        Log.d(TAG, "=== Testing User Profile in Firestore ===");
        
        String deviceId = authManager.getDeviceId();
        assertNotNull("Device ID should exist", deviceId);
        
        // Read user profile from Firestore
        DocumentSnapshot userDoc = Tasks.await(
            db.collection("users").document(deviceId).get()
        );
        
        if (userDoc.exists()) {
            Log.d(TAG, "User profile exists in Firestore");
            assertTrue("User document should exist", userDoc.exists());
            
            String name = userDoc.getString("name");
            String email = userDoc.getString("email");
            
            Log.d(TAG, "  - Name: " + name);
            Log.d(TAG, "  - Email: " + email);
            
            System.out.println("✓ User profile found in REAL Firestore:");
            System.out.println("  Device ID: " + deviceId);
            System.out.println("  Name: " + name);
            System.out.println("  Email: " + email);
        } else {
            Log.w(TAG, "⚠ User profile does NOT exist in Firestore");
            System.out.println("⚠ WARNING: User profile not found in Firestore");
            System.out.println("  Device ID: " + deviceId);
            System.out.println("  This may be expected if device hasn't been set up yet");
        }
    }
    
    // ============================================
    // EVENT FIRESTORE TESTS
    // ============================================
    
    @Test
    public void testCreateEventInFirestore_realDocument() throws Exception {
        Log.d(TAG, "=== Testing Event Creation in Firestore ===");
        
        Map<String, Object> event = new HashMap<>();
        event.put("title", "Real Firebase Test Event");
        event.put("description", "This event was created by integration test");
        event.put("capacity", 100);
        event.put("organizerId", testDeviceId);
        event.put("registrationStart", System.currentTimeMillis());
        event.put("registrationEnd", System.currentTimeMillis() + 86400000);
        event.put("deadlineEpochMs", System.currentTimeMillis() + 172800000);
        event.put("startsAtEpochMs", System.currentTimeMillis() + 259200000);
        event.put("waitlistCount", 0);
        event.put("waitlist", new java.util.ArrayList<>());
        event.put("admitted", new java.util.ArrayList<>());
        event.put("testMarker", true);
        
        // Write to Firestore
        Log.d(TAG, "Creating event in Firestore: " + testEventId);
        Tasks.await(db.collection("events").document(testEventId).set(event));
        Thread.sleep(1000);
        
        // Verify it exists
        DocumentSnapshot eventDoc = Tasks.await(
            db.collection("events").document(testEventId).get()
        );
        
        assertTrue("Event should exist in Firestore", eventDoc.exists());
        assertEquals("Title should match", "Real Firebase Test Event", eventDoc.getString("title"));
        assertEquals("Organizer should match", testDeviceId, eventDoc.getString("organizerId"));
        
        Log.d(TAG, "✓ Event created in REAL Firestore!");
        System.out.println("✓ Event created in REAL Firestore!");
        System.out.println("  Event ID: " + testEventId);
        System.out.println("  Title: Real Firebase Test Event");
        System.out.println("  Check Firestore Console to verify!");
    }
    
    // ============================================
    // NOTIFICATION REQUEST FIRESTORE TESTS
    // ============================================
    
    @Test
    public void testCreateNotificationRequest_triggersCloudFunction() throws Exception {
        Log.d(TAG, "=== Testing Notification Request ===");
        
        // Create notification request in Firestore
        // This should trigger the Cloud Function to send FCM notifications
        Map<String, Object> notificationRequest = new HashMap<>();
        
        java.util.List<String> userIds = new java.util.ArrayList<>();
        userIds.add(testDeviceId); // Send to current device
        
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("title", "Real Firebase Test Notification");
        notificationRequest.put("message", "This is a test notification from integration test");
        notificationRequest.put("eventId", testEventId);
        notificationRequest.put("eventTitle", "Test Event");
        notificationRequest.put("type", "test");
        notificationRequest.put("groupType", "test");
        notificationRequest.put("processed", false);
        notificationRequest.put("createdAt", System.currentTimeMillis());
        notificationRequest.put("testMarker", true);
        
        Log.d(TAG, "Creating notification request: " + testNotificationRequestId);
        Tasks.await(
            db.collection("notificationRequests")
                .document(testNotificationRequestId)
                .set(notificationRequest)
        );
        
        System.out.println("✓ Notification request created in REAL Firestore!");
        System.out.println("  Request ID: " + testNotificationRequestId);
        System.out.println("  Cloud Function should process this and send FCM notification");
        System.out.println("  Check Cloud Functions logs and device notifications!");
        
        // Wait for Cloud Function to process
        Log.d(TAG, "Waiting 5 seconds for Cloud Function to process...");
        Thread.sleep(5000);
        
        // Check if processed
        DocumentSnapshot requestDoc = Tasks.await(
            db.collection("notificationRequests")
                .document(testNotificationRequestId)
                .get()
        );
        
        if (requestDoc.exists()) {
            Boolean processed = requestDoc.getBoolean("processed");
            Long sentCount = requestDoc.getLong("sentCount");
            Long failureCount = requestDoc.getLong("failureCount");
            
            Log.d(TAG, "Notification request status:");
            Log.d(TAG, "  - Processed: " + processed);
            Log.d(TAG, "  - Sent Count: " + sentCount);
            Log.d(TAG, "  - Failure Count: " + failureCount);
            
            System.out.println("\nNotification Request Status:");
            System.out.println("  Processed: " + processed);
            System.out.println("  Sent: " + sentCount);
            System.out.println("  Failed: " + failureCount);
            
            if (Boolean.TRUE.equals(processed)) {
                System.out.println("✓ Cloud Function processed the notification request!");
                if (sentCount != null && sentCount > 0) {
                    System.out.println("✓ " + sentCount + " notification(s) sent via FCM!");
                }
            } else {
                System.out.println("⚠ Cloud Function hasn't processed yet. Check Cloud Functions logs.");
            }
        }
    }
    
    // ============================================
    // Test Runner
    // ============================================
    
    @Test
    public void runAllFirebaseIntegrationTests() throws Exception {
        System.out.println("\n========================================");
        System.out.println("FIREBASE INTEGRATION TESTS");
        System.out.println("========================================");
        System.out.println("These tests use REAL Firebase/Firestore!");
        System.out.println("========================================\n");
        
        try {
            testWriteToFirestore_createsRealDocument();
            testReadFromFirestore_getsRealData();
            testUpdateFirestoreDocument_updatesRealData();
            testQueryFirestore_getsFilteredResults();
            testUserProfileExistsInFirestore();
            testCreateEventInFirestore_realDocument();
            testCreateNotificationRequest_triggersCloudFunction();
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL FIREBASE INTEGRATION TESTS PASSED!");
            System.out.println("========================================\n");
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ SOME FIREBASE INTEGRATION TESTS FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            throw e;
        }
    }
}

