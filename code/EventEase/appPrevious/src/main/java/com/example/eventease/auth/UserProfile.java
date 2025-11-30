package com.example.eventease.auth;

/**
 * Represents a user profile in the authentication system.
 *
 * <p>This class serves as a data model (Data Transfer Object pattern) for basic user profile
 * information stored in Firestore during the authentication process. It contains minimal user
 * information required for device-based authentication and initial profile setup.</p>
 *
 * <p>This class uses public fields for Firestore compatibility and follows a simple data structure
 * pattern. It is distinct from the {@link com.example.eventease.model.Profile} class, which contains
 * more detailed user information used throughout the application.</p>
 *
 * <p><b>Role in Application:</b> Used during the authentication and profile setup workflow to
 * store initial user information in Firestore. This class is primarily used by the
 * {@link DeviceAuthManager} and profile setup activities to create and manage user accounts
 * in the Firebase backend.</p>
 *
 * <p><b>Outstanding Issues:</b> None currently.</p>
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

    /**
     * Default constructor for Firestore deserialization.
     */
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