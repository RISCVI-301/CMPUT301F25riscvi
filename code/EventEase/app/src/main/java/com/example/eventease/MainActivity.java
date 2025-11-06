package com.example.eventease;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.util.AuthHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private boolean navigated = false;

    private void goToMyEvents() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(
                MainActivity.this,
                com.example.eventease.ui.organizer.OrganizerMyEventActivity.class
        ));
        finish();
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try { FirebaseApp.initializeApp(this); } catch (Exception ignored) {}
        try { FirebaseFirestore.setLoggingEnabled(true); } catch (Throwable ignored) {}

        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.e(TAG, "Firebase not initialized – check google-services.json under app/ module");
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::goToMyEvents, 3000);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        
        if (currentUser != null) {
            createOrUpdateUserDocument(currentUser.getUid());
        } else {
            auth.signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            Log.d(TAG, "Signed in anonymously with UID: " + user.getUid());
                            createOrUpdateUserDocument(user.getUid());
                        } else {
                            Log.e(TAG, "Sign in succeeded but user is null");
                            goToMyEvents();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Anonymous sign-in failed", e);
                        Toast.makeText(this, "Sign-in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        goToMyEvents();
                    });
        }
    }
    
    private void createOrUpdateUserDocument(String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String organizerId = "organizer_test_1";
        
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("fullName", "Organizer");
        userDoc.put("role", "organizer");
        userDoc.put("createdAt", System.currentTimeMillis());
        userDoc.put("organizerId", organizerId);
        
        db.collection("users").document(userId)
                .set(userDoc, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Created/updated user document: " + userId))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create/update user document", e);
                    Toast.makeText(this, "Failed to set user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        
        Map<String, Object> organizerDoc = new HashMap<>();
        organizerDoc.put("fullName", "Sanika Verma");
        organizerDoc.put("role", "organizer");
        organizerDoc.put("createdAt", System.currentTimeMillis());
        organizerDoc.put("authUserId", userId);
        
        db.collection("users").document(organizerId)
                .set(organizerDoc, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Created/updated organizer document: " + organizerId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to create/update organizer document", e))
                .addOnCompleteListener(task -> goToMyEvents());
    }
}