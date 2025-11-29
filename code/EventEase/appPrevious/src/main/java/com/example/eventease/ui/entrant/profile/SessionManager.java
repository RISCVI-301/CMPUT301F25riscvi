package com.example.eventease.ui.entrant.profile;

import android.content.Context;

/**
 * Helper class for managing user session and Remember Me functionality.
 * NOTE: With device ID authentication, users are always "logged in" on their device.
 * This class is kept for backward compatibility but methods are now no-ops.
 */
public class SessionManager {
    
    /**
     * Ensures Remember Me is set to keep user logged in.
     * NOTE: With device auth, users are always "logged in" - this is a no-op.
     * 
     * @param context the context for SharedPreferences
     */
    public static void ensureRememberMeSet(Context context) {
        // Device auth - users are always "logged in" on their device
        // No action needed
    }
    
    /**
     * Ensures user stays logged in after email change.
     * NOTE: With device auth, no email changes needed - this is a no-op.
     * 
     * @param auth (ignored - kept for compatibility)
     * @param context the context for session management
     */
    public static void ensureUserLoggedIn(Object auth, Context context) {
        // Device auth - users are always "logged in" on their device
        // No action needed
    }
}

