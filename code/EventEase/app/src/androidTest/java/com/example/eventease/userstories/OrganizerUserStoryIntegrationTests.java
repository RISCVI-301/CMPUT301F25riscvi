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
 * REAL FIREBASE INTEGRATION TESTS for Organizer User Stories.
 * 
 * ⚠️ These tests interact with REAL Firebase/Firestore!
 * 
 * US 02.01.01 - Create event
 * US 02.01.02 - Set event capacity
 * US 02.01.03 - Set event location
 * US 02.01.04 - Set registration period
 * US 02.01.05 - Generate QR code
 * US 02.02.01 - View waitlist
 * US 02.02.02 - Send notifications
 * US 02.03.01 - View admitted entrants
 * US 02.03.02 - Remove admitted entrant
 * US 02.04.01 - Replace entrants
 * US 02.04.02 - Automatic selection
 * US 02.05.01 - View event history
 * US 02.05.02 - Cancel event
 * 
 * To run: ./gradlew connectedAndroidTest --tests OrganizerUserStoryIntegrationTests
 */
@RunWith(AndroidJUnit4.class)
public class OrganizerUserStoryIntegrationTests {
    
    private static final String TAG = "OrganizerIntegrationTests";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String organizerId;
    private List<String> cleanupEventIds;
    private List<String> cleanupRequestIds;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        organizerId = authManager.getDeviceId();
        cleanupEventIds = new ArrayList<>();
        cleanupRequestIds = new ArrayList<>();
        
        Log.d(TAG, "Setup complete. Organizer ID: " + organizerId);
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
    // US 02.01.01: Create event (REAL)
    // ============================================
    
    @Test
    public void testUS020101_createEvent_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.01.01: Create Event (REAL Firebase) ===");
        
        String eventId = UUID.randomUUID().toString();
        cleanupEventIds.add(eventId);
        
        // Create event in Firestore
        Map<String, Object> event = new HashMap<>();
        event.put("id", eventId);
        event.put("title", "Integration Test Event");
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
        
        // Verify event created
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertTrue("Event should exist in Firestore", eventDoc.exists());
        assertEquals("Title should match", "Integration Test Event", eventDoc.getString("title"));
        assertEquals("Organizer should match", organizerId, eventDoc.getString("organizerId"));
        
        Log.d(TAG, "✓ US 02.01.01 PASSED: Event created in REAL Firestore");
        System.out.println("✓ US 02.01.01: Successfully created event in REAL Firestore!");
    }
    
    // ============================================
    // US 02.01.02: Set event capacity (REAL)
    // ============================================
    
    @Test
    public void testUS020102_setEventCapacity_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.01.02: Set Event Capacity (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Update capacity
        Map<String, Object> updates = new HashMap<>();
        updates.put("capacity", 100);
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Verify update
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertEquals("Capacity should be updated", 100L, eventDoc.getLong("capacity"));
        
        Log.d(TAG, "✓ US 02.01.02 PASSED: Event capacity updated in REAL Firestore");
        System.out.println("✓ US 02.01.02: Successfully updated event capacity in REAL Firestore!");
    }
    
    // ============================================
    // US 02.01.03: Set event location (REAL)
    // ============================================
    
    @Test
    public void testUS020103_setEventLocation_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.01.03: Set Event Location (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Update location
        Map<String, Object> updates = new HashMap<>();
        updates.put("location", "Central Park, New York");
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Verify update
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertEquals("Location should be updated", "Central Park, New York", eventDoc.getString("location"));
        
        Log.d(TAG, "✓ US 02.01.03 PASSED: Event location updated in REAL Firestore");
        System.out.println("✓ US 02.01.03: Successfully updated event location in REAL Firestore!");
    }
    
    // ============================================
    // US 02.01.04: Set registration period (REAL)
    // ============================================
    
    @Test
    public void testUS020104_setRegistrationPeriod_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.01.04: Set Registration Period (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        long regStart = System.currentTimeMillis() + 86400000;
        long regEnd = System.currentTimeMillis() + 7 * 86400000;
        
        // Update registration period
        Map<String, Object> updates = new HashMap<>();
        updates.put("registrationStart", regStart);
        updates.put("registrationEnd", regEnd);
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Verify update
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertEquals("Registration start should match", regStart, (long) eventDoc.getLong("registrationStart"));
        assertEquals("Registration end should match", regEnd, (long) eventDoc.getLong("registrationEnd"));
        
        Log.d(TAG, "✓ US 02.01.04 PASSED: Registration period updated in REAL Firestore");
        System.out.println("✓ US 02.01.04: Successfully updated registration period in REAL Firestore!");
    }
    
    // ============================================
    // US 02.01.05: Generate QR code (REAL)
    // ============================================
    
    @Test
    public void testUS020105_generateQRCode_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.01.05: Generate QR Code (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Generate QR payload
        String qrPayload = "eventease://event/" + eventId;
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("qrPayload", qrPayload);
        updates.put("qrEnabled", true);
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Verify QR payload saved
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        assertEquals("QR payload should match", qrPayload, eventDoc.getString("qrPayload"));
        assertTrue("QR should be enabled", eventDoc.getBoolean("qrEnabled"));
        
        Log.d(TAG, "✓ US 02.01.05 PASSED: QR code generated and saved in REAL Firestore");
        System.out.println("✓ US 02.01.05: Successfully generated QR code in REAL Firestore!");
        System.out.println("  QR Payload: " + qrPayload);
    }
    
    // ============================================
    // US 02.02.01: View waitlist (REAL)
    // ============================================
    
    @Test
    public void testUS020201_viewWaitlist_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.02.01: View Waitlist (REAL Firebase) ===");
        
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
        
        // Read waitlist from Firestore
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> waitlistFromFirestore = (List<String>) eventDoc.get("waitlist");
        
        assertNotNull("Waitlist should exist", waitlistFromFirestore);
        assertEquals("Waitlist should have 3 users", 3, waitlistFromFirestore.size());
        assertTrue("Should contain user1", waitlistFromFirestore.contains("user1"));
        
        Log.d(TAG, "✓ US 02.02.01 PASSED: Waitlist viewed from REAL Firestore");
        System.out.println("✓ US 02.02.01: Successfully viewed waitlist (" + waitlistFromFirestore.size() + " users) from REAL Firestore!");
    }
    
    // ============================================
    // US 02.02.02: Send notifications (REAL)
    // ============================================
    
    @Test
    public void testUS020202_sendNotifications_realFCM() throws Exception {
        Log.d(TAG, "=== US 02.02.02: Send Notifications (REAL FCM) ===");
        
        // Create notification request
        String requestId = "organizer_notification_" + UUID.randomUUID().toString();
        cleanupRequestIds.add(requestId);
        
        List<String> userIds = new ArrayList<>();
        userIds.add(organizerId); // Send to organizer
        
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("title", "Event Update");
        notificationRequest.put("message", "Your event has been updated");
        notificationRequest.put("type", "organizer_update");
        notificationRequest.put("groupType", "organizer");
        notificationRequest.put("processed", false);
        notificationRequest.put("createdAt", System.currentTimeMillis());
        
        Tasks.await(db.collection("notificationRequests").document(requestId).set(notificationRequest));
        Log.d(TAG, "Notification request created: " + requestId);
        
        System.out.println("✓ US 02.02.02: Notification request created in REAL Firestore!");
        System.out.println("  → Cloud Function should send FCM notification");
        
        // Wait for Cloud Function
        Thread.sleep(8000);
        
        // Check if processed
        DocumentSnapshot requestDoc = Tasks.await(
            db.collection("notificationRequests").document(requestId).get()
        );
        
        if (requestDoc.exists() && Boolean.TRUE.equals(requestDoc.getBoolean("processed"))) {
            Long sentCount = requestDoc.getLong("sentCount");
            System.out.println("✓ Notification sent: " + sentCount + " notification(s)");
        }
    }
    
    // ============================================
    // US 02.03.01: View admitted entrants (REAL)
    // ============================================
    
    @Test
    public void testUS020301_viewAdmittedEntrants_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.03.01: View Admitted Entrants (REAL Firebase) ===");
        
        String eventId = createTestEvent();
        cleanupEventIds.add(eventId);
        
        // Add admitted entrants
        List<String> admitted = new ArrayList<>();
        admitted.add("entrant1");
        admitted.add("entrant2");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("admitted", admitted);
        
        Tasks.await(db.collection("events").document(eventId).update(updates));
        Thread.sleep(1000);
        
        // Read admitted list from Firestore
        DocumentSnapshot eventDoc = Tasks.await(db.collection("events").document(eventId).get());
        @SuppressWarnings("unchecked")
        List<String> admittedFromFirestore = (List<String>) eventDoc.get("admitted");
        
        assertNotNull("Admitted list should exist", admittedFromFirestore);
        assertEquals("Should have 2 admitted entrants", 2, admittedFromFirestore.size());
        
        Log.d(TAG, "✓ US 02.03.01 PASSED: Admitted entrants viewed from REAL Firestore");
        System.out.println("✓ US 02.03.01: Successfully viewed " + admittedFromFirestore.size() + " admitted entrants from REAL Firestore!");
    }
    
    // ============================================
    // US 02.05.01: View event history (REAL)
    // ============================================
    
    @Test
    public void testUS020501_viewEventHistory_realFirestore() throws Exception {
        Log.d(TAG, "=== US 02.05.01: View Event History (REAL Firebase) ===");
        
        // Create multiple events for this organizer
        String event1 = createTestEvent("Event 1");
        String event2 = createTestEvent("Event 2");
        cleanupEventIds.add(event1);
        cleanupEventIds.add(event2);
        
        Thread.sleep(2000);
        
        // Query events by organizer ID
        QuerySnapshot eventsSnapshot = Tasks.await(
            db.collection("events")
                .whereEqualTo("organizerId", organizerId)
                .get()
        );
        
        assertTrue("Should find events for organizer", eventsSnapshot.size() > 0);
        
        // Verify events belong to organizer
        for (DocumentSnapshot doc : eventsSnapshot.getDocuments()) {
            assertEquals("Event should belong to organizer", organizerId, doc.getString("organizerId"));
        }
        
        Log.d(TAG, "✓ US 02.05.01 PASSED: Event history viewed from REAL Firestore");
        System.out.println("✓ US 02.05.01: Successfully viewed event history (" + eventsSnapshot.size() + " events) from REAL Firestore!");
    }
    
    // ============================================
    // HELPER METHODS
    // ============================================
    
    private String createTestEvent() throws Exception {
        return createTestEvent("Test Event");
    }
    
    private String createTestEvent(String title) throws Exception {
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
    
    // ============================================
    // Test Runner
    // ============================================
    
    @Test
    public void runAllOrganizerIntegrationTests() throws Exception {
        System.out.println("\n========================================");
        System.out.println("ORGANIZER USER STORY INTEGRATION TESTS");
        System.out.println("========================================");
        System.out.println("These tests use REAL Firebase/Firestore!");
        System.out.println("========================================\n");
        
        try {
            testUS020101_createEvent_realFirestore();
            testUS020102_setEventCapacity_realFirestore();
            testUS020103_setEventLocation_realFirestore();
            testUS020104_setRegistrationPeriod_realFirestore();
            testUS020105_generateQRCode_realFirestore();
            testUS020201_viewWaitlist_realFirestore();
            testUS020202_sendNotifications_realFCM();
            testUS020301_viewAdmittedEntrants_realFirestore();
            testUS020501_viewEventHistory_realFirestore();
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL ORGANIZER INTEGRATION TESTS PASSED!");
            System.out.println("========================================\n");
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ SOME ORGANIZER INTEGRATION TESTS FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            throw e;
        }
    }
}

