package com.example.eventease.auth;

import androidx.annotation.NonNull;

/**
 * Interface for authentication management.
 * Provides methods to check authentication status and get the current user ID.
 * This interface abstracts authentication operations, allowing for different implementations.
 */
public interface AuthManager {
    /**
     * Checks if a user is currently authenticated.
     *
     * @return true if a user is signed in, false otherwise
     */
    boolean isAuthenticated();

    /**
     * Gets the unique identifier of the currently authenticated user.
     *
     * @return the Firebase user ID (UID)
     * @throws IllegalStateException if no user is currently signed in
     */
    @NonNull String getUid();
}
