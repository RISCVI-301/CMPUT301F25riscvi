package com.example.eventease.model;

import java.util.Date;

/**
 * Represents a waitlist entry for an event.
 * Tracks when a user joined the waitlist for a specific event.
 */
public class WaitlistEntry {
    private String eventId;
    private String uid;
    private Date joinedAt;

    /**
     * Default constructor for Firestore deserialization.
     */
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

    /**
     * Gets the ID of the event this waitlist entry is for.
     *
     * @return the event ID, or null if not set
     */
    public String getEventId() { return eventId; }

    /**
     * Sets the ID of the event this waitlist entry is for.
     *
     * @param eventId the event ID to set
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * Gets the user ID who joined the waitlist.
     *
     * @return the user ID, or null if not set
     */
    public String getUid() { return uid; }

    /**
     * Sets the user ID who joined the waitlist.
     *
     * @param uid the user ID to set
     */
    public void setUid(String uid) { this.uid = uid; }

    /**
     * Gets the date when the user joined the waitlist.
     *
     * @return the join date, or null if not set
     */
    public Date getJoinedAt() { return joinedAt; }

    /**
     * Sets the date when the user joined the waitlist.
     *
     * @param joinedAt the join date to set
     */
    public void setJoinedAt(Date joinedAt) { this.joinedAt = joinedAt; }
}
