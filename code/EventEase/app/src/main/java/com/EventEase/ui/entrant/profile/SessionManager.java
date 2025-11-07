package com.EventEase.ui.entrant.profile;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Helper class for managing user session and Remember Me functionality.
 * Ensures users stay logged in after email/password changes.
 */
public class SessionManager {
    
    private static final String PREFS_NAME = "EventEasePrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_SAVED_UID = "savedUid";
    
    /**
     * Ensures Remember Me is set to keep user logged in.
     * Uses UID for persistence (works even if email/password changes).
     * 
     * @param context the context for SharedPreferences
     */
    public static void ensureRememberMeSet(Context context) {
        if (context == null) return;
        
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedUid = prefs.getString(KEY_SAVED_UID, null);
        boolean rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false);
        
        // If Remember Me is not set or UID doesn't match, set it with current UID
        if (!rememberMe || savedUid == null || !savedUid.equals(currentUser.getUid())) {
            prefs.edit()
                .putBoolean(KEY_REMEMBER_ME, true)
                .putString(KEY_SAVED_UID, currentUser.getUid())
                .apply();
        }
    }
    
    /**
     * Ensures user stays logged in after email change.
     * Reloads user to ensure auth state is fresh.
     * 
     * @param auth the FirebaseAuth instance
     * @param context the context for session management
     */
    public static void ensureUserLoggedIn(FirebaseAuth auth, Context context) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;
        
        // Reload user to ensure auth state is fresh after email change
        currentUser.reload().addOnSuccessListener(unused -> {
            // User is still authenticated, ensure Remember Me is set
            ensureRememberMeSet(context);
        }).addOnFailureListener(e -> {
            // If reload fails, user might have been logged out
            // Try to keep them logged in by checking if we can get the user
            if (auth.getCurrentUser() == null) {
                // User was logged out, but we can't re-authenticate here
                // This should not happen after email change, but handle gracefully
                android.util.Log.w("SessionManager", "User appears to be logged out after email change");
            } else {
                // User still exists, ensure Remember Me is set
                ensureRememberMeSet(context);
            }
        });
    }
}

