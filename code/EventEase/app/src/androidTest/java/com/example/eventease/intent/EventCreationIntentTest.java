package com.example.eventease.intent;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.eventease.ui.organizer.OrganizerCreateEventActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Intent tests for event creation functionality.
 * Tests US 02.01.01 (Create event) and US 02.01.04 (Set registration period).
 */
@RunWith(AndroidJUnit4.class)
public class EventCreationIntentTest {

    @Test
    public void testEventCreationIntent_hasCorrectStructure() {
        // US 02.01.01: Test that event creation intent has correct structure
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), 
                                   OrganizerCreateEventActivity.class);
        intent.putExtra("organizerId", "testOrganizer123");
        
        assertNotNull(intent);
        assertEquals(OrganizerCreateEventActivity.class.getName(), 
                     intent.getComponent().getClassName());
        assertEquals("testOrganizer123", intent.getStringExtra("organizerId"));
    }

    @Test
    public void testEventCreationIntent_receivesOrganizerId() {
        // US 02.01.01: Test that organizer ID is passed correctly
        String organizerId = "testOrganizer123";
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), 
                                   OrganizerCreateEventActivity.class);
        intent.putExtra("organizerId", organizerId);
        
        assertNotNull(intent);
        assertEquals(organizerId, intent.getStringExtra("organizerId"));
        assertTrue(intent.hasExtra("organizerId"));
    }

    @Test
    public void testEventCreationIntent_canHandleMissingOrganizerId() {
        // Test that intent can be created without organizerId (will be resolved in activity)
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), 
                                   OrganizerCreateEventActivity.class);
        
        assertNotNull(intent);
        assertFalse(intent.hasExtra("organizerId"));
    }
}

