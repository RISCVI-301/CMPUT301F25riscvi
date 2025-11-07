package com.EventEase.auth;

/**
 * Represents a user profile in the authentication system.
 * Contains basic user information stored in Firestore.
 */
public class UserProfile {
    /** Unique user identifier. */
    public String uid;
    /** User's email address. */
    public String email;
    /** User's name. */
    public String name;
    /** Profile creation timestamp. */
    public long createdAt;


    public UserProfile() { /* Firestore needs empty ctor */ }

    /**
     * Creates a new UserProfile with the specified information.
     *
     * @param uid the unique user identifier
     * @param email the user's email address
     * @param name the user's name
     * @param createdAt the timestamp when the profile was created
     */
    public UserProfile(String uid, String email, String name, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
    }
}