package com.example.eventease.admin.event.data;

public class Event implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final int capacity;
    private final long createdAt, registrationEnd, registrationStart;
    private final String description, id, organizerId, posterUrl, qrPayload, title;
    private final boolean geolocation, qrEnabled;

    private final String guidelines;
    private final int waitlist_count;

    public Event(int capacity, long createdAt, String description, boolean geolocation,
                 String id, String organizerId, String posterUrl, boolean qrEnabled,
                 String qrPayload, long registrationEnd, long registrationStart, String title, String guidelines, int waitlistCount) {
        this.capacity = capacity;
        this.createdAt = createdAt;
        this.description = description;
        this.geolocation = geolocation;
        this.id = id;
        this.organizerId = organizerId;
        this.posterUrl = posterUrl;
        this.qrEnabled = qrEnabled;
        this.qrPayload = qrPayload;
        this.registrationEnd = registrationEnd;
        this.registrationStart = registrationStart;
        this.title = title;
        this.guidelines = guidelines;
        waitlist_count = waitlistCount;
    }

    public int getCapacity() { return capacity; }
    public long getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
    public boolean isGeolocation() { return geolocation; }
    public String getId() { return id; }
    public String getOrganizerId() { return organizerId; }
    public String getPosterUrl() { return posterUrl; }
    public boolean isQrEnabled() { return qrEnabled; }
    public String getQrPayload() { return qrPayload; }
    public long getRegistrationEnd() { return registrationEnd; }
    public long getRegistrationStart() { return registrationStart; }
    public String getTitle() { return title; }
}
