package com.EventEase.model;

import java.util.Date;

public class Invitation {
    public enum Status { PENDING, ACCEPTED, DECLINED }

    private String id;
    private String eventId;
    private String uid;
    private Status status;
    private Date issuedAt;
    private Date expiresAt;

    public Invitation() { }

    public Invitation(String id, String eventId, String uid, Status status, Date issuedAt, Date expiresAt) {
        this.id = id;
        this.eventId = eventId;
        this.uid = uid;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Date getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Date issuedAt) { this.issuedAt = issuedAt; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
}
