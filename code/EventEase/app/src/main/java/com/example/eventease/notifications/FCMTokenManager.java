package com.example.eventease.notifications;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FCMTokenManager {
    private static final String TAG = "FCMTokenManager";
    private static FCMTokenManager instance;
    
    private FCMTokenManager() {}
    
    public static FCMTokenManager getInstance() {
        if (instance == null) {
            instance = new FCMTokenManager();
        }
        return instance;
    }
    
    public void initialize() {
        Log.d(TAG, "Initializing FCM token...");
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        if (task.getException() != null) {
                            Log.w(TAG, "  Error: " + task.getException().getMessage());
                        }
                        return;
                    }
                    
                    String token = task.getResult();
                    if (token == null || token.isEmpty()) {
                        Log.w(TAG, "FCM token is null or empty");
                        return;
                    }
                    
                    Log.d(TAG, "FCM Registration Token received (length: " + token.length() + ")");
                    saveTokenToFirestore(token);
                    
                    // Verify token was saved after a short delay
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        verifyTokenInFirestore();
                    }, 2000);
                });
    }
    
    public void saveTokenToFirestore(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in, skipping token save");
            return;
        }
        
        String uid = user.getUid();
        String email = user.getEmail();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Log.d(TAG, "Saving FCM token for user: " + uid + " (" + (email != null ? email : "no email") + ")");
        Log.d(TAG, "Token length: " + (token != null ? token.length() : 0));
        
        db.collection("users").document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "FCM token saved to Firestore for user: " + uid);
                    Log.d(TAG, "  Document path: users/" + uid);
                    Log.d(TAG, "  Token: " + (token != null && token.length() > 20 ? token.substring(0, 20) + "..." : token));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save FCM token to Firestore for user: " + uid, e);
                    Log.e(TAG, "  Error: " + e.getMessage());
                    // Try to create the document if it doesn't exist
                    if (e.getMessage() != null && e.getMessage().contains("No document to update")) {
                        Log.d(TAG, "  Document doesn't exist, creating it...");
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("fcmToken", token);
                        db.collection("users").document(uid)
                                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Created user document with FCM token");
                                })
                                .addOnFailureListener(e2 -> {
                                    Log.e(TAG, "Failed to create user document", e2);
                                });
                    }
                });
    }
    
    /**
     * Verifies that the FCM token is saved in Firestore for the current user.
     * Useful for debugging notification issues.
     */
    public void verifyTokenInFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in, cannot verify token");
            return;
        }
        
        String uid = user.getUid();
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
                        }
                        Log.d(TAG, "Notifications enabled (Firestore): " + isEnabled);
                        if (notificationsEnabledObj != null) {
                            Log.d(TAG, "  Raw value type: " + notificationsEnabledObj.getClass().getSimpleName());
                            Log.d(TAG, "  Raw value: " + notificationsEnabledObj);
                        } else {
                            Log.d(TAG, "  Field not found, defaulting to true");
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

