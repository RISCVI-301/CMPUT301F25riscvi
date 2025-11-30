package com.example.eventease.admin.profile.data;

import java.util.List;

/**
 * Represents a user profile in the admin system.
 *
 * <p>This class serves as a data model (Data Transfer Object pattern) for user profile information
 * used by the admin interface. It extends the basic profile information with role management
 * capabilities, allowing administrators to view and manage user roles within the system.</p>
 *
 * <p>This class follows the JavaBean pattern with private fields and public getter/setter methods.
 * It is used exclusively by the admin interface for user management and role assignment operations,
 * providing a comprehensive view of user information including their assigned roles.</p>
 *
 * <p><b>Role in Application:</b> Used by the admin interface to display and manage user profiles
 * with role information. This class enables administrators to view user details, assign roles
 * (such as organizer, entrant, or admin), and manage user accounts in the system.</p>
 *
 * <p><b>Outstanding Issues:</b> None currently.</p>
 */
public class UserProfile {
    private String uid;
    private String email;
    private String name;
    private String phoneNumber;
    private List<String> roles;
    private long createdAt;

    /**
     * Default constructor for Firestore deserialization.
     */
    public UserProfile() {
    }

    /**
     * Creates a new UserProfile with the specified information.
     *
     * @param uid the unique user identifier
     * @param email the user's email address
     * @param name the user's name
     * @param phoneNumber the user's phone number
     * @param roles the list of roles assigned to the user
     * @param createdAt the timestamp when the profile was created
     */
    public UserProfile(String uid, String email, String name, String phoneNumber, List<String> roles, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    /**
     * Gets the unique user identifier.
     *
     * @return the user ID, or null if not set
     */
    public String getUid() {
        return uid;
    }

    /**
     * Sets the unique user identifier.
     *
     * @param uid the user ID to set
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * Gets the user's email address.
     *
     * @return the email address, or null if not set
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address.
     *
     * @param email the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gets the user's name.
     *
     * @return the user's name, or null if not set
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's name.
     *
     * @param name the user's name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the user's phone number.
     *
     * @return the phone number, or null if not set
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the user's phone number.
     *
     * @param phoneNumber the phone number to set
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * Gets the list of roles assigned to the user.
     *
     * @return the list of roles, or null if not set
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Sets the list of roles assigned to the user.
     *
     * @param roles the list of roles to set
     */
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    /**
     * Gets the timestamp when the profile was created.
     *
     * @return the creation timestamp in milliseconds, or 0 if not set
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when the profile was created.
     *
     * @param createdAt the creation timestamp in milliseconds
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}

