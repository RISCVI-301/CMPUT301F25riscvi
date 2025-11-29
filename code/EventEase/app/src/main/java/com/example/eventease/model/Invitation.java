package com.example.eventease.model;

import java.util.Date;

/**
 * Represents an invitation sent to an entrant for an event.
 *
 * <p>This class serves as a data model (Data Transfer Object pattern) for event invitations in the
 * EventEase application. It tracks the lifecycle of invitations sent to users who are on the waitlist
 * for an event, including their current status (pending, accepted, or declined) and timing information
 * for expiration handling.</p>
 *
 * <p>This class follows the JavaBean pattern with private fields and public getter/setter methods,
 * and includes a status enumeration to represent the invitation state. The class is used by the
 * invitation management system to track user responses and manage invitation deadlines.</p>
 *
 * <p><b>Role in Application:</b> Central to the waitlist-to-admission workflow. When an organizer
 * admits users from the waitlist, invitations are created and sent. This class tracks the invitation
 * status and expiration, enabling automatic processing of expired invitations and user response
 * handling.</p>
 *
 * <p><b>Outstanding Issues:</b> None currently.</p>
 */
public class Invitation {
    /**
     * Invitation status values.
     */
    public enum Status {
        /** Invitation has been sent but not yet responded to. */
        PENDING,
        /** Invitation has been accepted by the user. */
        ACCEPTED,
        /** Invitation has been declined by the user. */
        DECLINED
    }

    private String id;
    private String eventId;
    private String uid;
    private Status status;
    private Date issuedAt;
    private Date expiresAt;

    /**
     * Default constructor for Firestore deserialization.
     */
    public Invitation() { }

    /**
     * Creates a new Invitation with the specified information.
     *
     * @param id the unique invitation identifier
     * @param eventId the ID of the event this invitation is for
     * @param uid the user ID of the invitee
     * @param status the current status of the invitation
     * @param issuedAt the date when the invitation was issued
     * @param expiresAt the date when the invitation expires
     */
    public Invitation(String id, String eventId, String uid, Status status, Date issuedAt, Date expiresAt) {
        this.id = id;
        this.eventId = eventId;
        this.uid = uid;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the unique invitation identifier.
     *
     * @return the invitation ID, or null if not set
     */
    public String getId() { return id; }

    /**
     * Sets the unique invitation identifier.
     *
     * @param id the invitation ID to set
     */
    public void setId(String id) { this.id = id; }

    /**
     * Gets the ID of the event this invitation is for.
     *
     * @return the event ID, or null if not set
     */
    public String getEventId() { return eventId; }

    /**
     * Sets the ID of the event this invitation is for.
     *
     * @param eventId the event ID to set
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * Gets the user ID of the invitee.
     *
     * @return the user ID, or null if not set
     */
    public String getUid() { return uid; }

    /**
     * Sets the user ID of the invitee.
     *
     * @param uid the user ID to set
     */
    public void setUid(String uid) { this.uid = uid; }

    /**
     * Gets the current status of the invitation.
     *
     * @return the invitation status, or null if not set
     */
    public Status getStatus() { return status; }

    /**
     * Sets the current status of the invitation.
     *
     * @param status the invitation status to set
     */
    public void setStatus(Status status) { this.status = status; }

    /**
     * Gets the date when the invitation was issued.
     *
     * @return the issue date, or null if not set
     */
    public Date getIssuedAt() { return issuedAt; }

    /**
     * Sets the date when the invitation was issued.
     *
     * @param issuedAt the issue date to set
     */
    public void setIssuedAt(Date issuedAt) { this.issuedAt = issuedAt; }

    /**
     * Gets the date when the invitation expires.
     *
     * @return the expiration date, or null if not set
     */
    public Date getExpiresAt() { return expiresAt; }

    /**
     * Sets the date when the invitation expires.
     *
     * @param expiresAt the expiration date to set
     */
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
}
