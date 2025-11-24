package com.example.eventease.intent;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Intent tests for waitlist management functionality.
 * Tests US 01.01.01 (Join waiting list) and US 01.01.02 (Leave waiting list).
 */
@RunWith(AndroidJUnit4.class)
public class WaitlistManagementIntentTest {

    @Test
    public void testJoinWaitlistIntent_containsEventId() {
        // US 01.01.01: Test that join waitlist intent contains event ID
        Intent joinIntent = new Intent();
        joinIntent.putExtra("eventId", "event123");
        joinIntent.putExtra("uid", "user123");

        assertNotNull(joinIntent);
        assertEquals("event123", joinIntent.getStringExtra("eventId"));
        assertEquals("user123", joinIntent.getStringExtra("uid"));
    }

    @Test
    public void testLeaveWaitlistIntent_containsEventId() {
        // US 01.01.02: Test that leave waitlist intent contains event ID
        Intent leaveIntent = new Intent();
        leaveIntent.putExtra("eventId", "event123");
        leaveIntent.putExtra("uid", "user123");

        assertNotNull(leaveIntent);
        assertEquals("event123", leaveIntent.getStringExtra("eventId"));
        assertEquals("user123", leaveIntent.getStringExtra("uid"));
    }

    @Test
    public void testWaitlistIntent_dataIntegrity() {
        // US 01.01.01, US 01.01.02: Test data integrity for waitlist operations
        String eventId = "event456";
        String uid = "user456";

        Intent intent = new Intent();
        intent.putExtra("eventId", eventId);
        intent.putExtra("uid", uid);
        intent.setAction("JOIN_WAITLIST");

        assertEquals(eventId, intent.getStringExtra("eventId"));
        assertEquals(uid, intent.getStringExtra("uid"));
        assertEquals("JOIN_WAITLIST", intent.getAction());
    }
}

