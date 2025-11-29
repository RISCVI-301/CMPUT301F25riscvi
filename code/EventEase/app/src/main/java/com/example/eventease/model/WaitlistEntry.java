package com.example.eventease.model;

import java.util.Date;

/**
 * Represents a waitlist entry for an event.
 *
 * <p>This class serves as a data model (Data Transfer Object pattern) for tracking users who have
 * joined the waitlist for an event. It records when a user joined the waitlist, which is used
 * to maintain the order of waitlist entries and determine priority when spots become available.</p>
 *
 * <p>This class follows the JavaBean pattern with private fields and public getter/setter methods.
 * It is used in conjunction with the {@link Event} class, which maintains a list of waitlisted
 * user IDs, to provide detailed waitlist management functionality.</p>
 *
 * <p><b>Role in Application:</b> Used by the event management system to track waitlist entries
 * and maintain the order in which users joined. This ordering is critical for fair admission
 * processing when event capacity becomes available or when organizers manually admit users from
 * the waitlist.</p>
 *
 * <p><b>Outstanding Issues:</b> None currently.</p>
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
