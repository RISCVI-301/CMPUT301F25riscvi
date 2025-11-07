package com.EventEase.model;

import java.util.Date;

/**
 * Represents a waitlist entry for an event.
 * Tracks when a user joined the waitlist for a specific event.
 */
public class WaitlistEntry {
    private String eventId;
    private String uid;
    private Date joinedAt;

    public WaitlistEntry() { }

    /**
     * Creates a new WaitlistEntry with the specified information.
     *
     * @param eventId the ID of the event
     * @param uid the user ID who joined the waitlist
     * @param joinedAt the date when the user joined the waitlist
     */
    public WaitlistEntry(String eventId, String uid, Date joinedAt) {
        this.eventId = eventId;
        this.uid = uid;
        this.joinedAt = joinedAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public Date getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Date joinedAt) { this.joinedAt = joinedAt; }
}
