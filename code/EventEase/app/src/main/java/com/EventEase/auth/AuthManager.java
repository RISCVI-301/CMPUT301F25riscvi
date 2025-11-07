package com.EventEase.auth;

import androidx.annotation.NonNull;

public interface AuthManager {
    boolean isAuthenticated();

    /** Returns the current Firebase UID.
     * @throws IllegalStateException if no user is signed in. */
    @NonNull String getUid();
}
