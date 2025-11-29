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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * REAL Firebase integration tests for notification preferences.
 * 
 * Tests:
 * - Saving notification preferences to Firestore
 * - Loading notification preferences from Firestore
 * - Default values when preferences not set
 * - Independent control of invited vs not invited preferences
 * 
 * ⚠️ These tests interact with REAL Firebase/Firestore!
 * 
 * To run: ./gradlew connectedAndroidTest --tests NotificationPreferenceIntegrationTest
 */
@RunWith(AndroidJUnit4.class)
public class NotificationPreferenceIntegrationTest {
    
    private static final String TAG = "NotificationPreferenceIntegrationTest";
    private Context context;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private String testDeviceId;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(context);
        testDeviceId = "test_" + UUID.randomUUID().toString().substring(0, 8);
        
        Log.d(TAG, "Setting up test with device ID: " + testDeviceId);
    }
    
    @After
    public void tearDown() {
        // Clean up test user document
        if (testDeviceId != null) {
            db.collection("users").document(testDeviceId).delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleaned up test user document"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clean up test user document", e));
        }
    }

    @Test
    public void testSaveNotificationPreferences_BothEnabled() throws Exception {
        Log.d(TAG, "=== Test: Save both preferences as enabled ===");
        
        // Create user document
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Test User");
        userData.put("notificationPreferenceInvited", true);
        userData.put("notificationPreferenceNotInvited", true);
        
        Tasks.await(db.collection("users").document(testDeviceId).set(userData));
        Thread.sleep(1000);
        
        // Verify saved preferences
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(testDeviceId).get());
        assertTrue("User document should exist", userDoc.exists());
        
        Boolean invitedPref = userDoc.getBoolean("notificationPreferenceInvited");
        Boolean notInvitedPref = userDoc.getBoolean("notificationPreferenceNotInvited");
        
        assertTrue("Invited preference should be enabled", invitedPref);
        assertTrue("Not invited preference should be enabled", notInvitedPref);
        
        Log.d(TAG, "✓ Test passed: Both preferences saved and loaded correctly");
    }

    @Test
    public void testSaveNotificationPreferences_InvitedOnly() throws Exception {
        Log.d(TAG, "=== Test: Save only invited notifications enabled ===");
        
        // Create user document with only invited enabled
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Test User");
        userData.put("notificationPreferenceInvited", true);
        userData.put("notificationPreferenceNotInvited", false);
        
        Tasks.await(db.collection("users").document(testDeviceId).set(userData));
        Thread.sleep(1000);
        
        // Verify saved preferences
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(testDeviceId).get());
        Boolean invitedPref = userDoc.getBoolean("notificationPreferenceInvited");
        Boolean notInvitedPref = userDoc.getBoolean("notificationPreferenceNotInvited");
        
        assertTrue("Invited preference should be enabled", invitedPref);
        assertFalse("Not invited preference should be disabled", notInvitedPref);
        
        Log.d(TAG, "✓ Test passed: Invited-only preference saved correctly");
    }

    @Test
    public void testSaveNotificationPreferences_NotInvitedOnly() throws Exception {
        Log.d(TAG, "=== Test: Save only not invited notifications enabled ===");
        
        // Create user document with only not invited enabled
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Test User");
        userData.put("notificationPreferenceInvited", false);
        userData.put("notificationPreferenceNotInvited", true);
        
        Tasks.await(db.collection("users").document(testDeviceId).set(userData));
        Thread.sleep(1000);
        
        // Verify saved preferences
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(testDeviceId).get());
        Boolean invitedPref = userDoc.getBoolean("notificationPreferenceInvited");
        Boolean notInvitedPref = userDoc.getBoolean("notificationPreferenceNotInvited");
        
        assertFalse("Invited preference should be disabled", invitedPref);
        assertTrue("Not invited preference should be enabled", notInvitedPref);
        
        Log.d(TAG, "✓ Test passed: Not-invited-only preference saved correctly");
    }

    @Test
    public void testUpdateNotificationPreferences() throws Exception {
        Log.d(TAG, "=== Test: Update notification preferences ===");
        
        // Initially both enabled
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Test User");
        userData.put("notificationPreferenceInvited", true);
        userData.put("notificationPreferenceNotInvited", true);
        
        Tasks.await(db.collection("users").document(testDeviceId).set(userData));
        Thread.sleep(1000);
        
        // Update to disable invited notifications
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationPreferenceInvited", false);
        
        Tasks.await(db.collection("users").document(testDeviceId).update(updates));
        Thread.sleep(1000);
        
        // Verify updated preferences
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(testDeviceId).get());
        Boolean invitedPref = userDoc.getBoolean("notificationPreferenceInvited");
        Boolean notInvitedPref = userDoc.getBoolean("notificationPreferenceNotInvited");
        
        assertFalse("Invited preference should be updated to disabled", invitedPref);
        assertTrue("Not invited preference should remain enabled", notInvitedPref);
        
        Log.d(TAG, "✓ Test passed: Preferences updated correctly");
    }

    @Test
    public void testLoadNotificationPreferences_DefaultsToEnabled() throws Exception {
        Log.d(TAG, "=== Test: Default values when preferences not set ===");
        
        // Create user document without preferences
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Test User");
        
        Tasks.await(db.collection("users").document(testDeviceId).set(userData));
        Thread.sleep(1000);
        
        // Load preferences
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(testDeviceId).get());
        Boolean invitedPref = userDoc.getBoolean("notificationPreferenceInvited");
        Boolean notInvitedPref = userDoc.getBoolean("notificationPreferenceNotInvited");
        
        // Should default to true if not set
        boolean invited = invitedPref != null ? invitedPref : true;
        boolean notInvited = notInvitedPref != null ? notInvitedPref : true;
        
        assertTrue("Should default to enabled when not set", invited);
        assertTrue("Should default to enabled when not set", notInvited);
        
        Log.d(TAG, "✓ Test passed: Defaults to enabled when preferences not set");
    }

    @Test
    public void testNotificationPreferences_IndependentControl() throws Exception {
        Log.d(TAG, "=== Test: Independent control of preferences ===");
        
        // Set initial preferences
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "Test User");
        userData.put("notificationPreferenceInvited", true);
        userData.put("notificationPreferenceNotInvited", false);
        
        Tasks.await(db.collection("users").document(testDeviceId).set(userData));
        Thread.sleep(1000);
        
        // Update only not invited preference
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationPreferenceNotInvited", true);
        
        Tasks.await(db.collection("users").document(testDeviceId).update(updates));
        Thread.sleep(1000);
        
        // Verify both preferences are independent
        DocumentSnapshot userDoc = Tasks.await(db.collection("users").document(testDeviceId).get());
        Boolean invitedPref = userDoc.getBoolean("notificationPreferenceInvited");
        Boolean notInvitedPref = userDoc.getBoolean("notificationPreferenceNotInvited");
        
        assertTrue("Invited preference should remain unchanged", invitedPref);
        assertTrue("Not invited preference should be updated", notInvitedPref);
        
        Log.d(TAG, "✓ Test passed: Preferences can be controlled independently");
    }
}

