package com.example.eventease.admin.event.data;

public final class Event {
    private final int capacity;
    private final long createdAt, registrationEnd, registrationStart;
    private final String description, id, organizerId, posterUrl, qrPayload, title;
    private final boolean geolocation, qrEnabled;

    public Event(int capacity, long createdAt, String description, boolean geolocation,
                 String id, String organizerId, String posterUrl, boolean qrEnabled,
                 String qrPayload, long registrationEnd, long registrationStart, String title) {
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
    }

   
}
