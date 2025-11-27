package com.example.eventease.model;

import java.util.Date;

/**
 * Represents an admitted entry for an event.
 * Tracks when a user was admitted and when they accepted the invitation.
 */
public class AdmittedEntry {
    private String eventId;
    private String uid;
    private Date admittedAt;
    private Date acceptedAt;

    /**
     * Default constructor for Firestore deserialization.
     */
    public AdmittedEntry() { }

    /**
     * Creates a new AdmittedEntry with the specified information.
     *
     * @param eventId the ID of the event
     * @param uid the user ID who was admitted
     * @param admittedAt the date when the user was admitted
     * @param acceptedAt the date when the user accepted the invitation
     */
    public AdmittedEntry(String eventId, String uid, Date admittedAt, Date acceptedAt) {
        this.eventId = eventId;
        this.uid = uid;
        this.admittedAt = admittedAt;
        this.acceptedAt = acceptedAt;
    }

    /**
     * Gets the ID of the event this admitted entry is for.
     *
     * @return the event ID, or null if not set
     */
    public String getEventId() { return eventId; }

    /**
     * Sets the ID of the event this admitted entry is for.
     *
     * @param eventId the event ID to set
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * Gets the user ID who was admitted to the event.
     *
     * @return the user ID, or null if not set
     */
    public String getUid() { return uid; }

    /**
     * Sets the user ID who was admitted to the event.
     *
     * @param uid the user ID to set
     */
    public void setUid(String uid) { this.uid = uid; }

    /**
     * Gets the date when the user was admitted to the event.
     *
     * @return the admission date, or null if not set
     */
    public Date getAdmittedAt() { return admittedAt; }

    /**
     * Sets the date when the user was admitted to the event.
     *
     * @param admittedAt the admission date to set
     */
    public void setAdmittedAt(Date admittedAt) { this.admittedAt = admittedAt; }

    /**
     * Gets the date when the user accepted the invitation.
     *
     * @return the acceptance date, or null if not set
     */
    public Date getAcceptedAt() { return acceptedAt; }

    /**
     * Sets the date when the user accepted the invitation.
     *
     * @param acceptedAt the acceptance date to set
     */
    public void setAcceptedAt(Date acceptedAt) { this.acceptedAt = acceptedAt; }
}

