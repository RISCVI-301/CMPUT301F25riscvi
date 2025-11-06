package com.EventEase.model;

import java.util.Date;

public class WaitlistEntry {
    private String eventId;
    private String uid;
    private Date joinedAt;

    public WaitlistEntry() { }

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
