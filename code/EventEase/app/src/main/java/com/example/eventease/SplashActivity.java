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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
        FirebaseAuth auth = FirebaseAuth.getInstance();
        SharedPreferences prefs = getSharedPreferences("EventEasePrefs", MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        String savedUid = prefs.getString("savedUid", null);
        FirebaseUser currentUser = auth.getCurrentUser();

        // Check if Remember Me is enabled and UID matches
        boolean isLoggedIn = rememberMe && savedUid != null && currentUser != null 
                && savedUid.equals(currentUser.getUid());

        // If user is logged in but Remember Me is off or UID doesn't match, sign them out
        if (currentUser != null && (!rememberMe || savedUid == null || !savedUid.equals(currentUser.getUid()))) {
            auth.signOut();
            if (savedUid != null && !savedUid.equals(currentUser.getUid())) {
                prefs.edit().putBoolean("rememberMe", false).remove("savedUid").apply();
            }
        }

        if (isLoggedIn && currentUser != null) {
            // User is logged in - check if admin
            UserRoleChecker.isAdmin().addOnCompleteListener(task -> {
                Intent intent;
                if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                    // User is admin
                    intent = new Intent(SplashActivity.this, com.example.eventease.admin.AdminMainActivity.class);
                } else {
                    // User is not admin - go to entrant MainActivity
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                }
                startActivity(intent);
                finish();
            });
        } else {
            // User not logged in - go to MainActivity which will show login screen
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }
}

