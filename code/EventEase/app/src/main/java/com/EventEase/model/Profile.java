package com.EventEase.model;

/**
 * Represents a user profile in the EventEase system.
 * Contains user information including display name, email, phone number, and photo URL.
 */
public class Profile {
    private String uid;
    private String displayName;
    private String email;
    private String phoneNumber;
    private String photoUrl;

    public Profile() { }

    /**
     * Creates a new Profile with the specified information.
     *
     * @param uid the unique user identifier
     * @param displayName the user's display name
     * @param email the user's email address
     * @param photoUrl the URL of the user's profile photo
     */
    public Profile(String uid, String displayName, String email, String photoUrl) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.photoUrl = photoUrl;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
