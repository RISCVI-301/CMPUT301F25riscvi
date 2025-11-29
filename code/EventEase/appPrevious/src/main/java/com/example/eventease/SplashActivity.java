package com.example.eventease;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.example.eventease.auth.UserRoleChecker;

/**
 * Splash screen activity that displays the app logo briefly before navigating to the appropriate screen.
 * Checks authentication state and navigates to:
 * - MainActivity (entrant view) if user is logged in with Remember Me enabled
 * - Login screen if user is not logged in
 * - AdminMainActivity if user is an admin
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide action bar immediately
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_splash);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Navigate immediately without delay for both logged in and not logged in users
        checkAuthAndNavigate();
    }

    private void checkAuthAndNavigate() {
        // Device auth - check if profile exists
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(this);
        
        authManager.hasProfile().addOnCompleteListener(task -> {
            if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                // Profile exists - check if admin
                UserRoleChecker.isAdmin(this).addOnCompleteListener(adminTask -> {
                    Intent intent;
                    if (adminTask.isSuccessful() && Boolean.TRUE.equals(adminTask.getResult())) {
                        // User is admin
                        intent = new Intent(SplashActivity.this, com.example.eventease.admin.AdminMainActivity.class);
                    } else {
                        // User is entrant/organizer - go to MainActivity
                        intent = new Intent(SplashActivity.this, MainActivity.class);
                    }
                    startActivity(intent);
                    finish();
                });
            } else {
                // No profile - redirect directly to ProfileSetupActivity
                Intent intent = new Intent(SplashActivity.this, com.example.eventease.auth.ProfileSetupActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}

