package com.example.eventease.admin.logs.data;

public class Notification {
    private String notificationTitle, notificationMessage, eventTitle, organizerID;
    private int createdAt;
    private String OrganizerName;

    public Notification(int createdAt, String notificationTitle, String notificationMessage, String eventTitle, String organizerID ){
        this.createdAt = createdAt;
        this.notificationTitle = notificationTitle;
        this.notificationMessage = notificationMessage;
        this.eventTitle = eventTitle;
        this.organizerID = organizerID;
    }

}
