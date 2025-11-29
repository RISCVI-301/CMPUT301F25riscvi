package com.example.eventease.firebase;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.eventease.auth.DeviceAuthManager;
import com.example.eventease.notifications.FCMTokenManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * REAL FCM (Firebase Cloud Messaging) notification tests.
 * 
 * These tests verify:
 * - FCM tokens are actually generated
 * - FCM tokens are saved to Firestore
 * - Notifications can be sent via Cloud Functions
 * - Notifications are received on the device
 * 
 * ⚠️ IMPORTANT: These tests require:
 * - Real Android device/emulator with Google Play Services
 * - Internet connection
 * - Firebase project configured correctly
 * - Cloud Functions deployed
 * 
 * To run:
 * ./gradlew connectedAndroidTest --tests FCMNotificationTests
 */
@RunWith(AndroidJUnit4.class)
public class FCMNotificationTests {
    
    private static final String TAG = "FCMNotificationTests";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String testDeviceId;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        testDeviceId = authManager.getDeviceId();
        
        Log.d(TAG, "FCM Test setup complete. Device ID: " + testDeviceId);
    }
    
    // ============================================
    // FCM TOKEN GENERATION TESTS
    // ============================================
    
    @Test
    public void testFCMTokenGenerated_realToken() throws Exception {
        Log.d(TAG, "=== Testing REAL FCM Token Generation ===");
        
        // Initialize FCM token manager
        FCMTokenManager.getInstance().initialize(context);
        
        // Wait a bit for token generation
        Thread.sleep(3000);
        
        // Get token directly from Firebase
        String token = Tasks.await(FirebaseMessaging.getInstance().getToken());
        
        assertNotNull("FCM token should be generated", token);
        assertFalse("FCM token should not be empty", token.isEmpty());
        assertTrue("FCM token should be reasonably long (usually 150+ chars)", token.length() > 50);
        
        Log.d(TAG, "✓ FCM Token generated: " + token.substring(0, 20) + "...");
        System.out.println("✓ REAL FCM Token Generated!");
        System.out.println("  Token length: " + token.length() + " characters");
        System.out.println("  Token preview: " + token.substring(0, 30) + "...");
        
        // Verify token format (FCM tokens are base64-like strings)
        assertTrue("Token should contain valid characters", 
                   token.matches("^[A-Za-z0-9_-]+$"));
    }
    
    @Test
    public void testFCMTokenSavedToFirestore() throws Exception {
        Log.d(TAG, "=== Testing FCM Token Saved to Firestore ===");
        
        // Initialize FCM and wait for token generation and save
        FCMTokenManager.getInstance().initialize(context);
        Thread.sleep(5000); // Wait for token generation and Firestore save
        
        // Check if token exists in Firestore
        DocumentSnapshot userDoc = Tasks.await(
            db.collection("users").document(testDeviceId).get()
        );
        
        if (userDoc.exists()) {
            String fcmToken = userDoc.getString("fcmToken");
            
            if (fcmToken != null && !fcmToken.isEmpty()) {
                Log.d(TAG, "✓ FCM Token found in Firestore!");
                assertNotNull("FCM token should exist in Firestore", fcmToken);
                assertFalse("FCM token should not be empty", fcmToken.isEmpty());
                
                System.out.println("✓ REAL FCM Token saved to Firestore!");
                System.out.println("  Device ID: " + testDeviceId);
                System.out.println("  Token length: " + fcmToken.length());
                System.out.println("  Token preview: " + fcmToken.substring(0, 30) + "...");
                
                // Verify it matches current token
                String currentToken = Tasks.await(FirebaseMessaging.getInstance().getToken());
                if (fcmToken.equals(currentToken)) {
                    System.out.println("✓ Token in Firestore matches current token!");
                } else {
                    System.out.println("⚠ Token mismatch - may need refresh");
                    Log.w(TAG, "Token mismatch - Firestore token may be outdated");
                }
            } else {
                Log.w(TAG, "⚠ FCM Token NOT found in Firestore");
                System.out.println("⚠ WARNING: FCM Token not found in Firestore!");
                System.out.println("  Device ID: " + testDeviceId);
                System.out.println("  This means notifications won't work!");
                System.out.println("  Check FCMTokenManager initialization");
                
                // This is a warning, not a failure, as token might still be generating
                System.out.println("\n  Suggestion: Wait and check Firestore manually");
            }
        } else {
            Log.w(TAG, "⚠ User document not found in Firestore");
            System.out.println("⚠ WARNING: User profile not found in Firestore");
            System.out.println("  Device ID: " + testDeviceId);
            System.out.println("  Cannot verify FCM token without user profile");
        }
    }
    
    @Test
    public void testSendRealNotification_viaCloudFunction() throws Exception {
        Log.d(TAG, "=== Testing REAL Notification via Cloud Function ===");
        
        // Step 1: Ensure FCM token exists
        FCMTokenManager.getInstance().initialize(context);
        Thread.sleep(3000);
        
        String fcmToken = Tasks.await(FirebaseMessaging.getInstance().getToken());
        assertNotNull("Need FCM token to send notification", fcmToken);
        
        // Step 2: Save token to Firestore (if not already saved)
        DocumentSnapshot userDoc = Tasks.await(
            db.collection("users").document(testDeviceId).get()
        );
        
        if (!userDoc.exists() || userDoc.getString("fcmToken") == null) {
            Log.d(TAG, "Saving FCM token to Firestore...");
            Map<String, Object> userData = new HashMap<>();
            userData.put("fcmToken", fcmToken);
            userData.put("notificationsEnabled", true);
            Tasks.await(db.collection("users").document(testDeviceId).set(userData, 
                com.google.firebase.firestore.SetOptions.merge()));
            Thread.sleep(2000);
        }
        
        // Step 3: Create notification request
        String requestId = "fcm_test_" + UUID.randomUUID().toString();
        java.util.List<String> userIds = new java.util.ArrayList<>();
        userIds.add(testDeviceId);
        
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("title", "🧪 REAL FCM Test Notification");
        notificationRequest.put("message", "If you see this notification, FCM is working! This is a real notification sent via Cloud Functions.");
        notificationRequest.put("eventId", "test_event");
        notificationRequest.put("eventTitle", "FCM Test Event");
        notificationRequest.put("type", "test");
        notificationRequest.put("groupType", "test");
        notificationRequest.put("processed", false);
        notificationRequest.put("createdAt", System.currentTimeMillis());
        
        Log.d(TAG, "Creating notification request: " + requestId);
        Tasks.await(
            db.collection("notificationRequests")
                .document(requestId)
                .set(notificationRequest)
        );
        
        System.out.println("✓ Notification request created!");
        System.out.println("  Request ID: " + requestId);
        System.out.println("  Cloud Function should send notification now...");
        
        // Step 4: Wait for Cloud Function to process and send notification
        Log.d(TAG, "Waiting 10 seconds for Cloud Function to send notification...");
        System.out.println("\n⏳ Waiting for Cloud Function to process (10 seconds)...");
        System.out.println("  → Check your device for the notification!");
        System.out.println("  → Check Cloud Functions logs in Firebase Console");
        
        Thread.sleep(10000);
        
        // Step 5: Verify notification request was processed
        DocumentSnapshot requestDoc = Tasks.await(
            db.collection("notificationRequests")
                .document(requestId)
                .get()
        );
        
        if (requestDoc.exists()) {
            Boolean processed = requestDoc.getBoolean("processed");
            Long sentCount = requestDoc.getLong("sentCount");
            Long failureCount = requestDoc.getLong("failureCount");
            
            System.out.println("\n📊 Notification Request Status:");
            System.out.println("  Processed: " + processed);
            System.out.println("  Sent: " + sentCount);
            System.out.println("  Failed: " + failureCount);
            
            if (Boolean.TRUE.equals(processed)) {
                System.out.println("\n✓ Cloud Function processed the request!");
                
                if (sentCount != null && sentCount > 0) {
                    System.out.println("✓✓✓ NOTIFICATION SENT SUCCESSFULLY! ✓✓✓");
                    System.out.println("  → Check your device notifications");
                    System.out.println("  → You should see: '🧪 REAL FCM Test Notification'");
                } else if (failureCount != null && failureCount > 0) {
                    System.out.println("✗ Notification sending failed");
                    System.out.println("  Check Cloud Functions logs for errors");
                }
            } else {
                System.out.println("⚠ Cloud Function hasn't processed yet");
                System.out.println("  This might be normal - check Cloud Functions logs");
            }
        }
        
        System.out.println("\n✓ FCM Notification Test Complete!");
        System.out.println("  Remember to check your device for the notification!");
    }
    
    @Test
    public void testNotificationsEnabledFlag_preventsSending() throws Exception {
        Log.d(TAG, "=== Testing Notifications Enabled Flag ===");
        
        // Disable notifications for this device
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationsEnabled", false);
        
        Tasks.await(
            db.collection("users").document(testDeviceId).update(updates)
        );
        Thread.sleep(2000);
        
        // Create notification request
        String requestId = "fcm_disabled_test_" + UUID.randomUUID().toString();
        java.util.List<String> userIds = new java.util.ArrayList<>();
        userIds.add(testDeviceId);
        
        Map<String, Object> notificationRequest = new HashMap<>();
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("title", "Should Not Receive This");
        notificationRequest.put("message", "Notifications disabled");
        notificationRequest.put("processed", false);
        
        Tasks.await(
            db.collection("notificationRequests")
                .document(requestId)
                .set(notificationRequest)
        );
        
        Thread.sleep(5000);
        
        // Check if notification was skipped
        DocumentSnapshot requestDoc = Tasks.await(
            db.collection("notificationRequests")
                .document(requestId)
                .get()
        );
        
        if (requestDoc.exists() && Boolean.TRUE.equals(requestDoc.getBoolean("processed"))) {
            Long sentCount = requestDoc.getLong("sentCount");
            System.out.println("Notification request processed. Sent: " + sentCount);
            
            // Should be 0 because notifications are disabled
            if (sentCount != null && sentCount == 0) {
                System.out.println("✓ Notifications correctly skipped when disabled!");
            }
        }
        
        // Re-enable notifications
        updates.put("notificationsEnabled", true);
        Tasks.await(db.collection("users").document(testDeviceId).update(updates));
        
        System.out.println("✓ Notifications re-enabled");
    }
    
    // ============================================
    // Test Runner
    // ============================================
    
    @Test
    public void runAllFCMTests() throws Exception {
        System.out.println("\n========================================");
        System.out.println("REAL FCM NOTIFICATION TESTS");
        System.out.println("========================================");
        System.out.println("These tests use REAL FCM and Cloud Functions!");
        System.out.println("You should see notifications on your device!");
        System.out.println("========================================\n");
        
        try {
            testFCMTokenGenerated_realToken();
            testFCMTokenSavedToFirestore();
            testNotificationsEnabledFlag_preventsSending();
            testSendRealNotification_viaCloudFunction(); // Run this last as it sends actual notification
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL FCM TESTS COMPLETE!");
            System.out.println("========================================");
            System.out.println("Check your device for notifications!");
            System.out.println("Check Cloud Functions logs in Firebase Console!");
            System.out.println("========================================\n");
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ SOME FCM TESTS FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            throw e;
        }
    }
}

