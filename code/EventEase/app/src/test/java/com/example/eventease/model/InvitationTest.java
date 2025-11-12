package com.example.eventease.model;

import com.example.eventease.testdata.TestDataHelper;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.Date;

/**
 * Unit tests for Invitation model class.
 * Tests US 01.05.02 (Accept invitation) and US 01.05.03 (Decline invitation).
 */
public class InvitationTest {
    private Invitation invitation;
    private String invitationId;
    private String eventId;
    private String uid;
    private Date issuedAt;
    private Date expiresAt;

    @Before
    public void setUp() {
        invitationId = "inv123";
        eventId = "event123";
        uid = "user123";
        issuedAt = new Date();
        expiresAt = new Date(issuedAt.getTime() + 7 * 24 * 60 * 60 * 1000L); // 7 days later
        invitation = new Invitation(invitationId, eventId, uid, 
                                   Invitation.Status.PENDING, issuedAt, expiresAt);
    }

    @Test
    public void testInvitationCreation() {
        // Test invitation creation
        assertNotNull(invitation);
        assertEquals(invitationId, invitation.getId());
        assertEquals(eventId, invitation.getEventId());
        assertEquals(uid, invitation.getUid());
        assertEquals(Invitation.Status.PENDING, invitation.getStatus());
        assertEquals(issuedAt, invitation.getIssuedAt());
        assertEquals(expiresAt, invitation.getExpiresAt());
    }

    @Test
    public void testAcceptInvitation() {
        // US 01.05.02: Accept invitation
        invitation.setStatus(Invitation.Status.ACCEPTED);
        assertEquals(Invitation.Status.ACCEPTED, invitation.getStatus());
    }

    @Test
    public void testDeclineInvitation() {
        // US 01.05.03: Decline invitation
        invitation.setStatus(Invitation.Status.DECLINED);
        assertEquals(Invitation.Status.DECLINED, invitation.getStatus());
    }

    @Test
    public void testInvitationStatuses() {
        // Test all invitation statuses
        assertEquals(Invitation.Status.PENDING, Invitation.Status.PENDING);
        assertEquals(Invitation.Status.ACCEPTED, Invitation.Status.ACCEPTED);
        assertEquals(Invitation.Status.DECLINED, Invitation.Status.DECLINED);
    }

    @Test
    public void testDefaultConstructor() {
        // Test default constructor for Firestore
        Invitation emptyInvitation = new Invitation();
        assertNotNull(emptyInvitation);
        assertNull(emptyInvitation.getId());
        assertNull(emptyInvitation.getEventId());
        assertNull(emptyInvitation.getUid());
        assertNull(emptyInvitation.getStatus());
    }

    @Test
    public void testSettersAndGetters() {
        // Test all setters and getters
        invitation.setId("newInvId");
        invitation.setEventId("newEventId");
        invitation.setUid("newUid");
        invitation.setStatus(Invitation.Status.ACCEPTED);
        Date newIssuedAt = new Date();
        Date newExpiresAt = new Date(newIssuedAt.getTime() + 1000000L);
        invitation.setIssuedAt(newIssuedAt);
        invitation.setExpiresAt(newExpiresAt);

        assertEquals("newInvId", invitation.getId());
        assertEquals("newEventId", invitation.getEventId());
        assertEquals("newUid", invitation.getUid());
        assertEquals(Invitation.Status.ACCEPTED, invitation.getStatus());
        assertEquals(newIssuedAt, invitation.getIssuedAt());
        assertEquals(newExpiresAt, invitation.getExpiresAt());
    }

    @Test
    public void testInvitationExpiry() {
        // Test that invitation has expiry date
        assertNotNull(invitation.getExpiresAt());
        assertTrue(invitation.getExpiresAt().after(invitation.getIssuedAt()));
    }

    @Test
    public void testRealisticInvitation_usingTestDataHelper() {
        // Test with realistic invitation data
        Invitation realisticInvitation = TestDataHelper.createTestInvitation(
            "inv456", "event456", "user456"
        );
        
        assertNotNull(realisticInvitation);
        assertEquals("inv456", realisticInvitation.getId());
        assertEquals("event456", realisticInvitation.getEventId());
        assertEquals("user456", realisticInvitation.getUid());
        assertEquals(Invitation.Status.PENDING, realisticInvitation.getStatus());
        assertNotNull(realisticInvitation.getIssuedAt());
        assertNotNull(realisticInvitation.getExpiresAt());
        assertTrue(realisticInvitation.getExpiresAt().after(realisticInvitation.getIssuedAt()));
    }
}

