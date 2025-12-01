package com.example.eventease.ui.organizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.example.eventease.ui.entrant.profile.ProfileUpdateHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Activity for editing organizer profile information.
 * Allows updating name, phone number, and profile picture.
 */
public class OrganizerEditProfileActivity extends AppCompatActivity {
    private static final String TAG = "OrganizerEditProfile";
    
    private EditText nameField;
    private EditText phoneField;
    private EditText emailField;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    
    private ProfileUpdateHelper updateHelper;
    private Uri selectedImageUri;
    private ActivityResultLauncher<String> pickImage;
    private ActivityResultLauncher<String> requestCameraPermission;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_edit_profile);
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        nameField = findViewById(R.id.editNameField);
        phoneField = findViewById(R.id.editPhoneField);
        emailField = findViewById(R.id.editEmailField);
        profileImage = findViewById(R.id.profileImageEdit);
        
        // Initialize helper
        updateHelper = new ProfileUpdateHelper(this);
        
        // Set up activity result launchers for image picking
        setupImagePickers();
        
        // Load current user data
        loadUserData();
        
        // Set up click listeners
        ImageButton backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                android.util.Log.d(TAG, "Back button clicked");
                finish();
            });
            // Ensure the button is clickable
            backButton.setClickable(true);
            backButton.setFocusable(true);
        } else {
            android.util.Log.e(TAG, "Back button not found in layout");
        }
        
        ImageView editProfilePicture = findViewById(R.id.editProfilePicture);
        if (editProfilePicture != null) {
            editProfilePicture.setOnClickListener(v -> {
                // Show dialog to choose image source
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Select Image Source")
                    .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                        if (which == 0) {
                            // Camera selected
                            requestCameraPermission.launch(android.Manifest.permission.CAMERA);
                        } else {
                            // Gallery selected
                            pickImage.launch("image/*");
                        }
                    })
                    .show();
            });
        }
        
        View saveButton = findViewById(R.id.saveButton);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveChanges());
        }
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
    
    private void setupImagePickers() {
        // Gallery picker
        pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this)
                        .load(uri)
                        .circleCrop()
                        .into(profileImage);
                }
            }
        );
        
        // Camera permission
        requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    // Permission granted - for now, use gallery
                    // Full camera implementation can be added later if needed
                    pickImage.launch("image/*");
                } else {
                    Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }
    
    private void loadUserData() {
        String userId = com.example.eventease.auth.AuthHelper.getUid(this);
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        DocumentReference userRef = db.collection("users").document(userId);
        
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // Set the hints with current data
                String currentName = documentSnapshot.getString("name");
                String currentPhone = documentSnapshot.getString("phoneNumber");
                String currentEmail = documentSnapshot.getString("email");
                
                if (currentName != null) {
                    nameField.setHint(currentName);
                }
                if (currentPhone != null) {
                    phoneField.setHint(currentPhone);
                }
                if (currentEmail != null && emailField != null) {
                    emailField.setHint(currentEmail);
                }
                
                // Load profile image
                String photoUrl = documentSnapshot.getString("photoUrl");
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.entrant_icon)
                        .error(R.drawable.entrant_icon)
                        .circleCrop()
                        .into(profileImage);
                }
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void saveChanges() {
        String newName = nameField.getText().toString().trim();
        String newPhone = phoneField.getText().toString().trim();
        String newEmail = emailField != null ? emailField.getText().toString().trim() : "";
        
        // Use ProfileUpdateHelper to save changes (same as entrant view)
        updateHelper.saveChanges(newName, newPhone, newEmail, selectedImageUri, new ProfileUpdateHelper.UpdateCallback() {
            @Override
            public void onUpdateSuccess() {
                Toast.makeText(OrganizerEditProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
            
            @Override
            public void onUpdateFailure(String error) {
                Toast.makeText(OrganizerEditProfileActivity.this, "Failed to update profile: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}

