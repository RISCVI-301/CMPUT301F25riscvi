package com.EventEase.ui.entrant.profile;

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
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
}