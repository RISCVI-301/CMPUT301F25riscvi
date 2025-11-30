package com.EventEase.auth;

import androidx.annotation.NonNull;

/**
 * Interface for authentication management.
 * Provides methods to check authentication status and get the current user ID.
 */
public interface AuthManager {
    boolean isAuthenticated();

    /** Returns the current Firebase UID.
     * @throws IllegalStateException if no user is signed in. */
    @NonNull String getUid();
}
