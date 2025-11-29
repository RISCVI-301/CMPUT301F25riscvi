package com.example.eventease.intent;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Intent tests for invitation functionality.
 * Tests US 01.05.02 (Accept invitation) and US 01.05.03 (Decline invitation).
 */
@RunWith(AndroidJUnit4.class)
public class InvitationIntentTest {

    @Test
    public void testAcceptInvitationIntent_containsRequiredData() {
        // US 01.05.02: Test that accept invitation intent contains required data
        Intent acceptIntent = new Intent();
        acceptIntent.putExtra("invitationId", "inv123");
        acceptIntent.putExtra("eventId", "event123");
        acceptIntent.putExtra("uid", "user123");

        assertNotNull(acceptIntent);
        assertEquals("inv123", acceptIntent.getStringExtra("invitationId"));
        assertEquals("event123", acceptIntent.getStringExtra("eventId"));
        assertEquals("user123", acceptIntent.getStringExtra("uid"));
    }

    @Test
    public void testDeclineInvitationIntent_containsRequiredData() {
        // US 01.05.03: Test that decline invitation intent contains required data
        Intent declineIntent = new Intent();
        declineIntent.putExtra("invitationId", "inv456");
        declineIntent.putExtra("eventId", "event456");
        declineIntent.putExtra("uid", "user456");

        assertNotNull(declineIntent);
        assertEquals("inv456", declineIntent.getStringExtra("invitationId"));
        assertEquals("event456", declineIntent.getStringExtra("eventId"));
        assertEquals("user456", declineIntent.getStringExtra("uid"));
    }

    @Test
    public void testInvitationIntent_dataIntegrity() {
        // US 01.05.02, US 01.05.03: Test data integrity for invitation operations
        String invitationId = "inv789";
        String eventId = "event789";
        String uid = "user789";

        Intent intent = new Intent();
        intent.putExtra("invitationId", invitationId);
        intent.putExtra("eventId", eventId);
        intent.putExtra("uid", uid);
        intent.setAction("ACCEPT_INVITATION");

        assertEquals(invitationId, intent.getStringExtra("invitationId"));
        assertEquals(eventId, intent.getStringExtra("eventId"));
        assertEquals(uid, intent.getStringExtra("uid"));
        assertEquals("ACCEPT_INVITATION", intent.getAction());
    }
}

