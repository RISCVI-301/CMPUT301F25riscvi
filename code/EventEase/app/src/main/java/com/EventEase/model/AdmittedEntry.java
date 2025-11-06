package com.EventEase.model;

import java.util.Date;

/**
 * Represents an admitted/accepted event entry for a user
 */
public class AdmittedEntry {
    private String eventId;
    private String uid;
    private Date admittedAt;
    private Date acceptedAt;

    public AdmittedEntry() { }

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

