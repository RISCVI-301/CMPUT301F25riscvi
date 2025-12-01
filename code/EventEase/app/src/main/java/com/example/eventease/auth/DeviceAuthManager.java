package com.example.eventease.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages device-based authentication without passwords.
 * Each device is uniquely identified by its Android ID.
 */
public class DeviceAuthManager {
    private static final String TAG = "DeviceAuthManager";
    private static final String PREFS_NAME = "device_auth";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_HAS_PROFILE = "has_profile";
    
    private final Context context;
    private final SharedPreferences prefs;
    private final FirebaseFirestore db;
    
    public DeviceAuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Gets the unique device identifier.
     * Uses Android ID prefixed with "device_" to avoid conflicts.
     * 
     * @return Device ID (e.g., "device_abc123xyz")
     */
    public String getDeviceId() {
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        
        if (deviceId == null) {
            // Generate device ID from Android ID
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            
            // Prefix to distinguish from Firebase Auth UIDs
            deviceId = "device_" + androidId;
            
            // Cache it
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
            
            Log.d(TAG, "Generated new device ID: " + deviceId);
        }
        
        return deviceId;
    }
    
    /**
     * Gets the current user ID (same as device ID).
     * Convenience method to match Firebase Auth pattern.
     * 
     * @return User ID (device ID)
     */
    public String getUid() {
        return getDeviceId();
    }
    
    /**
     * Checks if a user profile exists for this device.
     * 
     * @return Task that completes with true if profile exists, false otherwise
     */
    public Task<Boolean> hasProfile() {
        String deviceId = getDeviceId();
        
        return db.collection("users")
                .document(deviceId)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Error checking profile", task.getException());
                        return false;
                    }
                    
                    DocumentSnapshot doc = task.getResult();
                    boolean exists = doc != null && doc.exists();
                    
                    // Cache the result
                    prefs.edit().putBoolean(KEY_HAS_PROFILE, exists).apply();
                    
                    if (exists) {
                        String name = doc.getString("name");
                        if (name != null) {
                            prefs.edit().putString(KEY_USER_NAME, name).apply();
                        }
                    }
                    
                    Log.d(TAG, "Profile exists: " + exists);
                    return exists;
                });
    }
    
    /**
     * Gets cached profile status (faster than querying Firestore).
     * 
     * @return true if profile exists (cached)
     */
    public boolean hasCachedProfile() {
        return prefs.getBoolean(KEY_HAS_PROFILE, false);
    }
    
    /**
     * Gets cached user name.
     * 
     * @return User name or null if not cached
     */
    public String getCachedUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }
    
    /**
     * Creates a new user profile for this device.
     * 
     * @param name User's display name
     * @param role User's role ("entrant" or "organizer")
     * @return Task that completes when profile is created
     */
    public Task<Void> createProfile(String name, String role) {
        return createProfile(name, role, null, null);
    }
    
    /**
     * Creates a new user profile for this device with optional fields.
     * 
     * @param name User's display name
     * @param role User's role ("entrant" or "organizer")
     * @param email Required email
     * @param phoneNumber Optional phone number
     * @return Task that completes when profile is created
     */
    public Task<Void> createProfile(String name, String role, String email, String phoneNumber) {
        String deviceId = getDeviceId();
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("deviceId", deviceId);
        userData.put("name", name);
        userData.put("fullName", name);
        userData.put("displayName", name);
        userData.put("roles", Arrays.asList(role));
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("lastSeenAt", System.currentTimeMillis());
        
        // Email is required
        if (email != null && !email.trim().isEmpty()) {
            userData.put("email", email.trim());
        } else {
            throw new IllegalArgumentException("Email is required");
        }
        
        // Phone number is optional
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            userData.put("phoneNumber", phoneNumber.trim());
        }
        
        Log.d(TAG, "Creating profile for device: " + deviceId + ", name: " + name + ", role: " + role);
        
        return db.collection("users")
                .document(deviceId)
                .set(userData)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        // Cache success
                        prefs.edit()
                            .putBoolean(KEY_HAS_PROFILE, true)
                            .putString(KEY_USER_NAME, name)
                            .apply();
                        
                        Log.d(TAG, "Profile created successfully");
                        return Tasks.forResult(null);
                    } else {
                        Log.e(TAG, "Failed to create profile", task.getException());
                        return Tasks.forException(task.getException());
                    }
                });
    }
    
    /**
     * Updates the last seen timestamp for this device.
     * Should be called on app start.
     */
    public void updateLastSeen() {
        String deviceId = getDeviceId();
        
        db.collection("users")
                .document(deviceId)
                .update("lastSeenAt", System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Last seen updated"))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to update last seen", e));
    }
    
    /**
     * Checks if the current device has a specific role.
     * 
     * @param role Role to check (e.g., "admin", "organizer", "entrant")
     * @return Task that completes with true if user has the role
     */
    public Task<Boolean> hasRole(String role) {
        String deviceId = getDeviceId();
        
        return db.collection("users")
                .document(deviceId)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return false;
                    }
                    
                    DocumentSnapshot doc = task.getResult();
                    if (!doc.exists()) {
                        return false;
                    }
                    
                    Object rolesObj = doc.get("roles");
                    if (rolesObj instanceof List<?>) {
                        List<?> roles = (List<?>) rolesObj;
                        for (Object r : roles) {
                            if (r != null && r.toString().equalsIgnoreCase(role)) {
                                return true;
                            }
                        }
                    }
                    
                    // Also check single "role" field for compatibility
                    String singleRole = doc.getString("role");
                    if (singleRole != null && singleRole.equalsIgnoreCase(role)) {
                        return true;
                    }
                    
                    return false;
                });
    }
    
    /**
     * Checks if the current device is an admin.
     * 
     * @return Task that completes with true if user is admin
     */
    public Task<Boolean> isAdmin() {
        return hasRole("admin");
    }
    
    /**
     * Checks if the current device is an organizer.
     * 
     * @return Task that completes with true if user is organizer
     */
    public Task<Boolean> isOrganizer() {
        return hasRole("organizer");
    }
    
    /**
     * Clears all cached data (for testing/debugging).
     */
    public void clearCache() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cache cleared");
    }
}

