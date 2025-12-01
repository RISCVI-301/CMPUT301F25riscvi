package com.example.eventease.auth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.eventease.MainActivity;
import com.example.eventease.R;
import com.example.eventease.ui.entrant.profile.DialogBlurHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity for first-time profile setup.
 * Shown when a device doesn't have a user profile yet.
 * Includes optional profile picture upload and permission requests.
 */
public class ProfileSetupActivity extends AppCompatActivity {
    private static final String TAG = "ProfileSetupActivity";
    
    private EditText etName;
    private EditText etEmail;
    private EditText etPhone;
    private Button btnContinue;
    private TextView tvDeviceInfo;
    private ImageView ivProfilePicture;
    private Button btnUploadPicture;
    private ProgressBar progressUpload;
    private View profilePictureSection;
    
    private DeviceAuthManager authManager;
    private Uri selectedImageUri;
    
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivProfilePicture.setImageURI(uri);
                    ivProfilePicture.setVisibility(View.VISIBLE);
                    btnUploadPicture.setText("Change Picture");
                }
            }
    );
    
    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                }
            }
    );
    
    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && selectedImageUri != null) {
                    ivProfilePicture.setImageURI(selectedImageUri);
                    ivProfilePicture.setVisibility(View.VISIBLE);
                    btnUploadPicture.setText("Change Picture");
                }
            }
    );
    
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
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        btnUploadPicture = findViewById(R.id.btnUploadPicture);
        progressUpload = findViewById(R.id.progressUpload);
        profilePictureSection = findViewById(R.id.profilePictureSection);
        
        // Show device ID for debugging
        String deviceId = authManager.getDeviceId();
        tvDeviceInfo.setText("Device ID: " + deviceId);
        Log.d(TAG, "Device ID: " + deviceId);
        
        // Set up profile picture upload button
        if (btnUploadPicture != null) {
            btnUploadPicture.setOnClickListener(v -> showImageSourceDialog());
        }
        
        // Make profile picture clickable
        if (ivProfilePicture != null) {
            ivProfilePicture.setOnClickListener(v -> showImageSourceDialog());
        }
        
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
        
        // Create profile first
        authManager.createProfile(name, role, email, phone)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile created successfully");
                    
                    // If user selected a profile picture, upload it
                    if (selectedImageUri != null) {
                        uploadProfilePicture(name);
                    } else {
                        // No picture selected, proceed to permissions
                    Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
                        navigateToPermissions();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create profile", e);
                    Toast.makeText(this, "Failed to create profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    
                    // Re-enable button
                    btnContinue.setEnabled(true);
                    btnContinue.setText("Continue");
                });
    }
    
    private void showImageSourceDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        DialogBlurHelper.setupBlurredDialog(dialog, this, R.layout.entrant_dialog_image_source);

        AppCompatButton cameraButton = dialog.findViewById(R.id.btnCamera);
        AppCompatButton galleryButton = dialog.findViewById(R.id.btnGallery);

        if (cameraButton != null) {
            cameraButton.setOnClickListener(v -> {
                dialog.dismiss();
                openCamera();
            });
        }

        if (galleryButton != null) {
            galleryButton.setOnClickListener(v -> {
                dialog.dismiss();
                pickImageLauncher.launch("image/*");
            });
        }

        dialog.show();
        DialogBlurHelper.applyDialogAnimations(dialog, this);
    }
    
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            openCameraInternal();
        }
    }
    
    private void openCameraInternal() {
        try {
            java.io.File photoFile = java.io.File.createTempFile(
                "profile_photo_" + System.currentTimeMillis(),
                ".jpg",
                getCacheDir()
            );
            
            selectedImageUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile
            );
            
            takePictureLauncher.launch(selectedImageUri);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void uploadProfilePicture(String userName) {
        if (selectedImageUri == null) {
            navigateToPermissions();
            return;
        }
        
        if (progressUpload != null) {
            progressUpload.setVisibility(View.VISIBLE);
        }
        
        String uid = authManager.getUid();
        if (uid == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            navigateToPermissions();
            return;
        }
        
        try {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference();
            String filename = uid + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference profilePicRef = storageRef.child("profile_pictures").child(filename);
            
            Log.d(TAG, "Uploading profile picture to: profile_pictures/" + filename);
            Log.d(TAG, "Storage bucket: " + storage.getApp().getOptions().getStorageBucket());
            Log.d(TAG, "UID: " + uid + " (should start with 'device_')");
            
            profilePicRef.putFile(selectedImageUri)
                    .addOnProgressListener(snapshot -> {
                        double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                        Log.d(TAG, "Upload progress: " + progress + "%");
                    })
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "Upload successful!");
                        profilePicRef.getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    Log.d(TAG, "Profile picture uploaded: " + uri.toString());
                                    saveProfilePictureUrl(uid, uri.toString(), userName);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to get download URL", e);
                                    if (progressUpload != null) {
                                        progressUpload.setVisibility(View.GONE);
                                    }
                                    String errorMsg = "Failed to get download URL: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                                    Log.e(TAG, "Full error details", e);
                                    navigateToPermissions();
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Upload failed", e);
                        if (progressUpload != null) {
                            progressUpload.setVisibility(View.GONE);
                        }
                        String errorMsg = "Upload failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                        Toast.makeText(this, errorMsg + "\n\nPlease ensure Firebase Storage is enabled and rules are deployed.", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Full error details", e);
                        navigateToPermissions();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception during upload", e);
            if (progressUpload != null) {
                progressUpload.setVisibility(View.GONE);
            }
            Toast.makeText(this, "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
            navigateToPermissions();
        }
    }
    
    private void saveProfilePictureUrl(String uid, String photoUrl, String userName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("photoUrl", photoUrl);
        updates.put("updatedAt", System.currentTimeMillis());
        
        // Use set with merge instead of update to handle case where document might not exist yet
        db.collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (progressUpload != null) {
                        progressUpload.setVisibility(View.GONE);
                    }
                    Log.d(TAG, "Profile picture URL saved successfully");
                    Toast.makeText(this, "Welcome, " + userName + "!", Toast.LENGTH_SHORT).show();
                    navigateToPermissions();
                })
                .addOnFailureListener(e -> {
                    if (progressUpload != null) {
                        progressUpload.setVisibility(View.GONE);
                    }
                    Log.e(TAG, "Failed to save profile picture URL", e);
                    String errorMsg = "Failed to save profile picture: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Full error details", e);
                    // Still navigate even if save fails
                    navigateToPermissions();
                });
    }
    
    private void navigateToPermissions() {
        // Navigate to permission request activity
        Intent intent = new Intent(this, PermissionRequestActivity.class);
        startActivity(intent);
        finish();
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

