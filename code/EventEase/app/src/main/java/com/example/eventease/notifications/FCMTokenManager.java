package com.example.eventease.notifications;

import android.content.Context;
import android.util.Log;

import com.example.eventease.auth.DeviceAuthManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FCMTokenManager {
    private static final String TAG = "FCMTokenManager";
    private static FCMTokenManager instance;
    private Context appContext;
    
    private FCMTokenManager() {}
    
    public static FCMTokenManager getInstance() {
        if (instance == null) {
            instance = new FCMTokenManager();
        }
        return instance;
    }
    
    public void initialize() {
        initialize(null);
    }
    
    public void initialize(Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
        Log.d(TAG, "=== Initializing FCM token ===");
        
        // Get initial token and set up token refresh listener
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        Log.e(TAG, "❌ Fetching FCM registration token failed");
                        if (exception != null) {
                            Log.e(TAG, "  Error type: " + exception.getClass().getSimpleName());
                            Log.e(TAG, "  Error message: " + exception.getMessage());
                            if (exception.getCause() != null) {
                                Log.e(TAG, "  Cause: " + exception.getCause().getMessage());
                            }
                        }
                        return;
                    }
                    
                    String token = task.getResult();
                    if (token == null || token.isEmpty()) {
                        Log.e(TAG, "❌ FCM token is null or empty");
                        return;
                    }
                    
                    // Validate token format (FCM tokens are typically 152+ characters)
                    if (token.length() < 50) {
                        Log.w(TAG, "⚠️ Warning: FCM token seems unusually short (length: " + token.length() + ")");
                    }
                    
                    Log.d(TAG, "✓ FCM Registration Token received");
                    Log.d(TAG, "  Token length: " + token.length());
                    Log.d(TAG, "  Token preview: " + (token.length() > 20 ? token.substring(0, 20) + "..." : token));
                    saveTokenToFirestore(token);
                    
                    // Verify token was saved after a short delay
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        verifyTokenInFirestore();
                    }, 2000);
                });
        
        // Set up token refresh listener (handles automatic token rotation)
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
            if (tokenTask.isSuccessful()) {
                String newToken = tokenTask.getResult();
                if (newToken != null && !newToken.isEmpty()) {
                    Log.d(TAG, "🔄 FCM token refreshed automatically, updating Firestore...");
                    saveTokenToFirestore(newToken);
                }
            } else {
                Log.w(TAG, "Token refresh failed: " + (tokenTask.getException() != null ? tokenTask.getException().getMessage() : "Unknown error"));
            }
                });
    }
    
    public void saveTokenToFirestore(String token) {
        if (appContext == null) {
            Log.w(TAG, "⚠️ No context available, skipping token save");
            return;
        }
        
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "❌ Cannot save null or empty token");
            return;
        }
        
        DeviceAuthManager authManager = new DeviceAuthManager(appContext);
        String uid = authManager.getUid();
        
        if (uid == null || uid.isEmpty()) {
            Log.w(TAG, "⚠️ No device ID found, skipping token save");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Log.d(TAG, "=== Saving FCM token to Firestore ===");
        Log.d(TAG, "User ID: " + uid);
        Log.d(TAG, "Token length: " + token.length());
        Log.d(TAG, "Token preview: " + (token.length() > 20 ? token.substring(0, 20) + "..." : token));
        
        // Also set notificationsEnabled to true by default if not set
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("fcmToken", token);
        updateData.put("fcmTokenUpdatedAt", System.currentTimeMillis());
        
        db.collection("users").document(uid)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ FCM token saved to Firestore successfully");
                    Log.d(TAG, "  Document path: users/" + uid);
                    Log.d(TAG, "  Token saved at: " + new java.util.Date(System.currentTimeMillis()));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save FCM token to Firestore for user: " + uid);
                    Log.e(TAG, "  Error type: " + e.getClass().getSimpleName());
                    Log.e(TAG, "  Error message: " + e.getMessage());
                    if (e.getCause() != null) {
                        Log.e(TAG, "  Cause: " + e.getCause().getMessage());
                    }
                    
                    // Try to create the document if it doesn't exist
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("No document to update") || 
                                             errorMsg.contains("NOT_FOUND"))) {
                        Log.d(TAG, "  → Document doesn't exist, creating it...");
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("fcmToken", token);
                        userData.put("fcmTokenUpdatedAt", System.currentTimeMillis());
                        userData.put("notificationsEnabled", true); // Default to enabled
                        
                        db.collection("users").document(uid)
                                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✓ Created user document with FCM token");
                                })
                                .addOnFailureListener(e2 -> {
                                    Log.e(TAG, "❌ Failed to create user document", e2);
                                    Log.e(TAG, "  Error: " + e2.getMessage());
                                });
                    } else {
                        // Retry after a delay for other errors
                        Log.d(TAG, "  → Will retry saving token in 5 seconds...");
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            Log.d(TAG, "Retrying FCM token save...");
                            saveTokenToFirestore(token);
                        }, 5000);
                    }
                });
    }
    
    /**
     * Verifies that the FCM token is saved in Firestore for the current device.
     * Useful for debugging notification issues.
     */
    public void verifyTokenInFirestore() {
        if (appContext == null) {
            Log.w(TAG, "No context available, cannot verify token");
            return;
        }
        
        DeviceAuthManager authManager = new DeviceAuthManager(appContext);
        String uid = authManager.getUid();
        
        if (uid == null || uid.isEmpty()) {
            Log.w(TAG, "No device ID found, cannot verify token");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String savedToken = doc.getString("fcmToken");
                        
                        // Check notificationsEnabled field - handle both boolean and string types
                        Boolean notificationsEnabled = null;
                        Object notificationsEnabledObj = doc.get("notificationsEnabled");
                        if (notificationsEnabledObj instanceof Boolean) {
                            notificationsEnabled = (Boolean) notificationsEnabledObj;
                        } else if (notificationsEnabledObj instanceof String) {
                            notificationsEnabled = Boolean.parseBoolean((String) notificationsEnabledObj);
                        } else if (notificationsEnabledObj != null) {
                            // Try to convert other types
                            notificationsEnabled = Boolean.valueOf(notificationsEnabledObj.toString());
                        }
                        
                        // Default to true if not set (backwards compatibility)
                        boolean isEnabled = notificationsEnabled != null ? notificationsEnabled : true;
                        
                        Log.d(TAG, "=== FCM Token Verification ===");
                        Log.d(TAG, "User ID: " + uid);
                        Log.d(TAG, "Token exists: " + (savedToken != null && !savedToken.isEmpty()));
                        if (savedToken != null) {
                            Log.d(TAG, "Token length: " + savedToken.length());
                            Log.d(TAG, "Token preview: " + (savedToken.length() > 20 ? savedToken.substring(0, 20) + "..." : savedToken));
                            
                            // Validate token format
                            if (savedToken.length() < 50) {
                                Log.w(TAG, "⚠️ WARNING: Token seems unusually short (may be invalid)");
                            }
                        } else {
                            Log.w(TAG, "⚠️ WARNING: No FCM token found in Firestore!");
                            Log.w(TAG, "  → User will not receive push notifications");
                            Log.w(TAG, "  → Attempting to re-initialize token...");
                            // Try to re-initialize
                            initialize(appContext);
                        }
                        
                        // Check for token error markers (set by Cloud Functions when token is invalid)
                        String tokenError = doc.getString("fcmTokenError");
                        if (tokenError != null) {
                            Log.w(TAG, "⚠️ WARNING: Token has error marker: " + tokenError);
                            Log.w(TAG, "  → Cloud Functions detected invalid token");
                            Log.w(TAG, "  → Attempting to refresh token...");
                            initialize(appContext);
                        }
                        
                        Log.d(TAG, "Notifications enabled (Firestore): " + isEnabled);
                        if (notificationsEnabledObj != null) {
                            Log.d(TAG, "  Raw value type: " + notificationsEnabledObj.getClass().getSimpleName());
                            Log.d(TAG, "  Raw value: " + notificationsEnabledObj);
                        } else {
                            Log.d(TAG, "  Field not found, defaulting to true");
                        }
                        
                        // Check last update time
                        Long tokenUpdatedAt = doc.getLong("fcmTokenUpdatedAt");
                        if (tokenUpdatedAt != null) {
                            long ageMs = System.currentTimeMillis() - tokenUpdatedAt;
                            long ageDays = ageMs / (24 * 60 * 60 * 1000);
                            Log.d(TAG, "Token last updated: " + new java.util.Date(tokenUpdatedAt));
                            Log.d(TAG, "Token age: " + ageDays + " days");
                            if (ageDays > 30) {
                                Log.w(TAG, "⚠️ WARNING: Token is " + ageDays + " days old, may need refresh");
                            }
                        }
                        
                        Log.d(TAG, "==============================");
                    } else {
                        Log.w(TAG, "User document does not exist in Firestore: users/" + uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to verify token in Firestore", e);
                });
    }
}

