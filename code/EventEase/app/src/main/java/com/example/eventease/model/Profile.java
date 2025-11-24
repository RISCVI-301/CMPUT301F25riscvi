package com.example.eventease.model;

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

    /**
     * Default constructor for Firestore deserialization.
     */
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

    /**
     * Gets the unique user identifier.
     *
     * @return the user ID, or null if not set
     */
    public String getUid() { return uid; }

    /**
     * Sets the unique user identifier.
     *
     * @param uid the user ID to set
     */
    public void setUid(String uid) { this.uid = uid; }

    /**
     * Gets the user's display name.
     *
     * @return the display name, or null if not set
     */
    public String getDisplayName() { return displayName; }

    /**
     * Sets the user's display name.
     *
     * @param displayName the display name to set
     */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Gets the user's email address.
     *
     * @return the email address, or null if not set
     */
    public String getEmail() { return email; }

    /**
     * Sets the user's email address.
     *
     * @param email the email address to set
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * Gets the user's phone number.
     *
     * @return the phone number, or null if not set
     */
    public String getPhoneNumber() { return phoneNumber; }

    /**
     * Sets the user's phone number.
     *
     * @param phoneNumber the phone number to set
     */
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    /**
     * Gets the URL of the user's profile photo.
     *
     * @return the photo URL, or null if not set
     */
    public String getPhotoUrl() { return photoUrl; }

    /**
     * Sets the URL of the user's profile photo.
     *
     * @param photoUrl the photo URL to set
     */
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
