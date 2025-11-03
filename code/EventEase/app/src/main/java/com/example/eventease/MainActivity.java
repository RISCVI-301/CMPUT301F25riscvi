package com.example.eventease;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
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

        // Firebase init + logging (safe-guarded)
        try { FirebaseApp.initializeApp(this); } catch (Exception ignored) {}
        try { FirebaseFirestore.setLoggingEnabled(true); } catch (Throwable ignored) {}

        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.e(TAG, "Firebase not initialized – check google-services.json under app/ module");
        }

        // Fallback navigation in case Firestore is slow/offline
        new Handler(Looper.getMainLooper()).postDelayed(this::goToMyEvents, 1500);

        // Dev-only: upsert a fixed organizer user so My Events has an owner
        final String CURRENT_USER_ID = "organizer_test_1";
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("fullName", "Sanika Verma");
        userDoc.put("role", "organizer");
        userDoc.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(CURRENT_USER_ID)
                .set(userDoc, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Upserted users/organizer_test_1 successfully"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upsert failed", e);
                    Toast.makeText(this, "Failed to set user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                })
                .addOnCompleteListener(task -> {
                    // Navigate regardless of success/failure
                    goToMyEvents();
                });
    }
}