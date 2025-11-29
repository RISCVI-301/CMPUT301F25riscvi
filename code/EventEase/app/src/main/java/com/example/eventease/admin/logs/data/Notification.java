package com.example.eventease.admin.logs.data;

public class Notification {
    private String notificationTitle, notificationMessage, eventTitle, organizerID;
    private long createdAt;
    private String organizerName;

    public Notification(long createdAt, String notificationTitle, String notificationMessage, String eventTitle, String organizerID ){
        this.createdAt = createdAt;
        this.notificationTitle = notificationTitle;
        this.notificationMessage = notificationMessage;
        this.eventTitle = eventTitle;
        this.organizerID = organizerID;
    }

    public void setOrganizerName(String name){
        this.organizerName = name;
    }

    public String getNotificationTitle() {
        return this.notificationTitle;
    }

    public String getNotificationMessage() {
        return this.notificationMessage;
    }

    public String getEventTitle() {
        return this.eventTitle;
    }

    public String getOrganizerID() {
        return this.organizerID;
    }

    public String getCreatedAt() {
        long millis = (long) this.createdAt;
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(millis));
    }
}