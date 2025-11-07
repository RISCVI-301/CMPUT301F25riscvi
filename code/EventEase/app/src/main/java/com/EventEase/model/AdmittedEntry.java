package com.EventEase.model;

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

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public Date getAdmittedAt() { return admittedAt; }
    public void setAdmittedAt(Date admittedAt) { this.admittedAt = admittedAt; }

    public Date getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Date acceptedAt) { this.acceptedAt = acceptedAt; }
}

