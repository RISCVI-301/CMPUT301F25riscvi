package com.example.eventease.model;

import java.util.Date;

/**
 * Represents an invitation sent to an entrant for an event.
 * Tracks invitation status and timing information.
 */
public class Invitation {
    /**
     * Invitation status values.
     * <ul>
     *   <li>PENDING - Invitation has been sent but not yet responded to</li>
     *   <li>ACCEPTED - Invitation has been accepted by the user</li>
     *   <li>DECLINED - Invitation has been declined by the user</li>
     * </ul>
     */
    public enum Status { PENDING, ACCEPTED, DECLINED }

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
