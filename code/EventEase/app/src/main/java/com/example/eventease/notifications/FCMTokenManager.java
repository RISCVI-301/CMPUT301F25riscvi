package com.example.eventease.notifications;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

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
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    
                    String token = task.getResult();
                    Log.d(TAG, "FCM Registration Token: " + token);
                    saveTokenToFirestore(token);
                });
    }
    
    public void saveTokenToFirestore(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No user logged in, skipping token save");
            return;
        }
        
        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("users").document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "FCM token saved to Firestore for user: " + uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save FCM token to Firestore", e);
                });
    }
}

