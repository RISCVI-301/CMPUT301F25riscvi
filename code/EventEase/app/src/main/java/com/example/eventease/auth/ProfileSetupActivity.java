package com.example.eventease.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.MainActivity;
import com.example.eventease.R;

/**
 * Activity for first-time profile setup.
 * Shown when a device doesn't have a user profile yet.
 */
public class ProfileSetupActivity extends AppCompatActivity {
    private static final String TAG = "ProfileSetupActivity";
    
    private EditText etName;
    private EditText etEmail;
    private EditText etPhone;
    private Button btnContinue;
    private TextView tvDeviceInfo;
    
    private DeviceAuthManager authManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide action bar for cleaner profile setup UI
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_profile_setup);
        
        authManager = new DeviceAuthManager(this);
        
        // Initialize views
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        btnContinue = findViewById(R.id.btnContinue);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        
        // Show device ID for debugging
        String deviceId = authManager.getDeviceId();
        tvDeviceInfo.setText("Device ID: " + deviceId);
        Log.d(TAG, "Device ID: " + deviceId);
        
        // Set up continue button
        btnContinue.setOnClickListener(v -> createProfile());
    }
    
    private void createProfile() {
        // Get input values
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        
        // Validate name
        if (name.isEmpty()) {
            etName.setError("Please enter your name");
            etName.requestFocus();
            return;
        }
        
        // Validate email (required)
        if (email.isEmpty()) {
            etEmail.setError("Please enter your email");
            etEmail.requestFocus();
            return;
        }
        
        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }
        
        // Everyone starts as entrant; organizers can be promoted in Firestore
        String role = "entrant";
        
        Log.d(TAG, "Creating profile: name=" + name + ", role=" + role);
        
        // Disable button during creation
        btnContinue.setEnabled(false);
        btnContinue.setText("Creating Profile...");
        
        // Create profile
        authManager.createProfile(name, role, email, phone)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile created successfully");
                    Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
                    
                    // Navigate to main app
                    navigateToMainApp();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create profile", e);
                    Toast.makeText(this, "Failed to create profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    
                    // Re-enable button
                    btnContinue.setEnabled(true);
                    btnContinue.setText("Continue");
                });
    }
    
    private void navigateToMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back - must complete profile setup
        Toast.makeText(this, "Please complete your profile to continue", Toast.LENGTH_SHORT).show();
    }
}

