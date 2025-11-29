package com.example.eventease.auth;

import android.content.Context;

/**
 * Simple helper to get current user ID.
 * Provides a centralized way to get the authenticated user ID (device ID).
 */
public class AuthHelper {
    /**
     * Gets the current user ID (device ID).
     * 
     * @param context Android context
     * @return Device ID (user ID)
     */
    public static String getUid(Context context) {
        DeviceAuthManager authManager = new DeviceAuthManager(context);
        return authManager.getUid();
    }
    
    /**
     * Gets the DeviceAuthManager instance.
     * 
     * @param context Android context
     * @return DeviceAuthManager
     */
    public static DeviceAuthManager getAuthManager(Context context) {
        return new DeviceAuthManager(context);
    }
}

