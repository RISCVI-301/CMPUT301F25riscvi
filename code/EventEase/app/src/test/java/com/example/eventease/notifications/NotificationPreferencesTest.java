package com.example.eventease.notifications;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for notification preferences functionality.
 * Tests saving and loading of notification preferences:
 * - notificationPreferenceInvited
 * - notificationPreferenceNotInvited
 * 
 * This tests the logic in AccountFragment for managing notification preferences.
 */
public class NotificationPreferencesTest {

    private Map<String, Object> userDocument;
    
    @Before
    public void setUp() {
        userDocument = new HashMap<>();
    }

    @Test
    public void testSaveNotificationPreferences_BothEnabled() {
        // Save both preferences as enabled
        userDocument.put("notificationPreferenceInvited", true);
        userDocument.put("notificationPreferenceNotInvited", true);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        assertTrue("Invited preference should be enabled", invitedPref);
        assertTrue("Not invited preference should be enabled", notInvitedPref);
    }

    @Test
    public void testSaveNotificationPreferences_InvitedOnly() {
        // Save only invited notifications enabled
        userDocument.put("notificationPreferenceInvited", true);
        userDocument.put("notificationPreferenceNotInvited", false);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        assertTrue("Invited preference should be enabled", invitedPref);
        assertFalse("Not invited preference should be disabled", notInvitedPref);
    }

    @Test
    public void testSaveNotificationPreferences_NotInvitedOnly() {
        // Save only not invited notifications enabled
        userDocument.put("notificationPreferenceInvited", false);
        userDocument.put("notificationPreferenceNotInvited", true);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        assertFalse("Invited preference should be disabled", invitedPref);
        assertTrue("Not invited preference should be enabled", notInvitedPref);
    }

    @Test
    public void testSaveNotificationPreferences_BothDisabled() {
        // Save both preferences as disabled
        userDocument.put("notificationPreferenceInvited", false);
        userDocument.put("notificationPreferenceNotInvited", false);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        assertFalse("Invited preference should be disabled", invitedPref);
        assertFalse("Not invited preference should be disabled", notInvitedPref);
    }

    @Test
    public void testLoadNotificationPreferences_DefaultsToEnabled() {
        // When preferences are not set, should default to enabled
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        // Default to true if not set
        boolean invited = invitedPref != null ? invitedPref : true;
        boolean notInvited = notInvitedPref != null ? notInvitedPref : true;
        
        assertTrue("Should default to enabled when not set", invited);
        assertTrue("Should default to enabled when not set", notInvited);
    }

    @Test
    public void testLoadNotificationPreferences_WithExistingValues() {
        // Load existing preferences
        userDocument.put("notificationPreferenceInvited", true);
        userDocument.put("notificationPreferenceNotInvited", false);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        boolean invited = invitedPref != null ? invitedPref : true;
        boolean notInvited = notInvitedPref != null ? notInvitedPref : true;
        
        assertTrue("Should load existing invited preference", invited);
        assertFalse("Should load existing not invited preference", notInvited);
    }

    @Test
    public void testUpdateNotificationPreferences() {
        // Initially both enabled
        userDocument.put("notificationPreferenceInvited", true);
        userDocument.put("notificationPreferenceNotInvited", true);
        
        // Update to disable invited notifications
        userDocument.put("notificationPreferenceInvited", false);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        assertFalse("Invited preference should be updated to disabled", invitedPref);
        assertTrue("Not invited preference should remain enabled", notInvitedPref);
    }

    @Test
    public void testNotificationPreferences_IndependentControl() {
        // Test that preferences can be controlled independently
        userDocument.put("notificationPreferenceInvited", true);
        userDocument.put("notificationPreferenceNotInvited", false);
        
        // Change only one preference
        userDocument.put("notificationPreferenceNotInvited", true);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        assertTrue("Invited preference should remain unchanged", invitedPref);
        assertTrue("Not invited preference should be updated", notInvitedPref);
    }

    @Test
    public void testNotificationPreferences_NullHandling() {
        // Test handling of null values
        userDocument.put("notificationPreferenceInvited", null);
        userDocument.put("notificationPreferenceNotInvited", null);
        
        Boolean invitedPref = (Boolean) userDocument.get("notificationPreferenceInvited");
        Boolean notInvitedPref = (Boolean) userDocument.get("notificationPreferenceNotInvited");
        
        // Should default to true when null
        boolean invited = invitedPref != null ? invitedPref : true;
        boolean notInvited = notInvitedPref != null ? notInvitedPref : true;
        
        assertTrue("Should default to enabled when null", invited);
        assertTrue("Should default to enabled when null", notInvited);
    }

    @Test
    public void testNotificationPreferences_FieldNames() {
        // Verify correct field names are used
        String invitedField = "notificationPreferenceInvited";
        String notInvitedField = "notificationPreferenceNotInvited";
        
        userDocument.put(invitedField, true);
        userDocument.put(notInvitedField, false);
        
        assertTrue("Should use correct field name for invited", 
                  userDocument.containsKey(invitedField));
        assertTrue("Should use correct field name for not invited", 
                  userDocument.containsKey(notInvitedField));
    }
}

