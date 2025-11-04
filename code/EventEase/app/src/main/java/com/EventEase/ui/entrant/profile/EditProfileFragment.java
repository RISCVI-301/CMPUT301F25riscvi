package com.EventEase.ui.entrant.profile;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.appcompat.widget.AppCompatButton;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.example.eventease.R;
import java.util.HashMap;
import java.util.Map;

public class EditProfileFragment extends Fragment {
    private EditText nameField;
    private EditText emailField;
    private EditText phoneField;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private Uri selectedImageUri;
    
    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                profileImage.setImageURI(uri);
            }
        }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_edit_profile, container, false);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        
        // Initialize views
        nameField = root.findViewById(R.id.editNameField);
        emailField = root.findViewById(R.id.editEmailField);
        phoneField = root.findViewById(R.id.editPhoneField);
        profileImage = root.findViewById(R.id.profileImageEdit);
        
        // Load current user data
        loadUserData();
        
        // Set up click listeners
        root.findViewById(R.id.backButton).setOnClickListener(v -> 
            Navigation.findNavController(root).navigateUp());
        
        root.findViewById(R.id.editProfilePicture).setOnClickListener(v -> 
            pickImage.launch("image/*"));
        
        root.findViewById(R.id.saveButton).setOnClickListener(v -> saveChanges());
        root.findViewById(R.id.changePasswordButton).setOnClickListener(v -> showChangePasswordDialog());
        
        return root;
    }
    
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            DocumentReference userRef = db.collection("users").document(uid);
            
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Set the hints with current data
                    nameField.setHint(documentSnapshot.getString("name"));
                    emailField.setHint(documentSnapshot.getString("email"));
                    phoneField.setHint(documentSnapshot.getString("phoneNumber"));
                    
                    // Load profile image
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
                        Glide.with(getContext())
                            .load(photoUrl)
                            .placeholder(R.drawable.icon)
                            .error(R.drawable.icon)
                            .into(profileImage);
                    }
                }
            });
        }
    }
    
    private void saveChanges() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            DocumentReference userRef = db.collection("users").document(uid);
            
            Map<String, Object> updates = new HashMap<>();
            
            // Update name if changed
            String newName = nameField.getText().toString().trim();
            if (!newName.isEmpty()) {
                updates.put("name", newName);
            }
            
            // Update phone if changed
            String newPhone = phoneField.getText().toString().trim();
            if (!newPhone.isEmpty()) {
                updates.put("phoneNumber", newPhone);
            }
            
            // Update photo if selected
            if (selectedImageUri != null) {
                StorageReference storageRef = storage.getReference()
                    .child("profile_pictures")
                    .child(uid + "_" + System.currentTimeMillis() + ".jpg");
                
                storageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            updates.put("photoUrl", uri.toString());
                            updateFirestore(userRef, updates);
                        });
                    })
                    .addOnFailureListener(e -> 
                        Toast.makeText(getContext(), "Failed to upload image", Toast.LENGTH_SHORT).show());
            } else {
                updateFirestore(userRef, updates);
            }
        }
    }
    
    private void updateFirestore(DocumentReference userRef, Map<String, Object> updates) {
        if (!updates.isEmpty()) {
            updates.put("updatedAt", System.currentTimeMillis());
            userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).navigateUp();
                })
                .addOnFailureListener(e -> 
                    Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show());
        } else {
            Navigation.findNavController(requireView()).navigateUp();
        }
    }

    private void showChangePasswordDialog() {
        if (getContext() == null) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(getContext(), "Unable to change password right now", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_change_password);
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextInputLayout currentPasswordLayout = dialog.findViewById(R.id.currentPasswordLayout);
        TextInputLayout newPasswordLayout = dialog.findViewById(R.id.newPasswordLayout);
        TextInputLayout confirmPasswordLayout = dialog.findViewById(R.id.confirmNewPasswordLayout);

        TextInputEditText currentPasswordInput = dialog.findViewById(R.id.currentPasswordInput);
        TextInputEditText newPasswordInput = dialog.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmPasswordInput = dialog.findViewById(R.id.confirmNewPasswordInput);

        AppCompatButton cancelButton = dialog.findViewById(R.id.btnCancel);
        AppCompatButton saveButton = dialog.findViewById(R.id.btnSaveChanges);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                if (currentPasswordLayout != null) currentPasswordLayout.setError(null);
                if (newPasswordLayout != null) newPasswordLayout.setError(null);
                if (confirmPasswordLayout != null) confirmPasswordLayout.setError(null);

                String currentPassword = currentPasswordInput != null && currentPasswordInput.getText() != null
                    ? currentPasswordInput.getText().toString().trim()
                    : "";
                String newPassword = newPasswordInput != null && newPasswordInput.getText() != null
                    ? newPasswordInput.getText().toString().trim()
                    : "";
                String confirmPassword = confirmPasswordInput != null && confirmPasswordInput.getText() != null
                    ? confirmPasswordInput.getText().toString().trim()
                    : "";

                boolean hasError = false;

                if (currentPassword.isEmpty()) {
                    if (currentPasswordLayout != null) currentPasswordLayout.setError("Current password required");
                    hasError = true;
                }

                if (newPassword.isEmpty()) {
                    if (newPasswordLayout != null) newPasswordLayout.setError("New password required");
                    hasError = true;
                } else if (newPassword.length() < 6) {
                    if (newPasswordLayout != null) newPasswordLayout.setError("Password must be at least 6 characters");
                    hasError = true;
                } else if (newPassword.equals(currentPassword)) {
                    if (newPasswordLayout != null) newPasswordLayout.setError("Use a different password");
                    hasError = true;
                }

                if (!newPassword.equals(confirmPassword)) {
                    if (confirmPasswordLayout != null) confirmPasswordLayout.setError("Passwords do not match");
                    hasError = true;
                }

                if (hasError) {
                    return;
                }

                saveButton.setEnabled(false);

                AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);

                currentUser.reauthenticate(credential)
                    .addOnSuccessListener(unused -> currentUser.updatePassword(newPassword)
                        .addOnSuccessListener(updateUnused -> {
                            Toast.makeText(getContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> {
                            if (newPasswordLayout != null) newPasswordLayout.setError("Failed to update password");
                            saveButton.setEnabled(true);
                        }))
                    .addOnFailureListener(e -> {
                        if (currentPasswordLayout != null) currentPasswordLayout.setError("Current password is incorrect");
                        saveButton.setEnabled(true);
                    });
            });
        }

        dialog.show();
    }
}
