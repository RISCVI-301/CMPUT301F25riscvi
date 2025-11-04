package com.EventEase.auth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.EventEase.auth.AuthManager;
import com.bumptech.glide.Glide;
import com.example.eventease.App;
import com.example.eventease.R;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UploadProfilePictureFragment extends Fragment {

    private ImageView profileIconImage;
    private ProgressBar progressUpload;
    private Uri selectedImageUri;
    private AuthManager auth;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        // Show selected image in the circle
                        Glide.with(this)
                                .load(selectedImageUri)
                                .centerCrop()
                                .into(profileIconImage);
                    }
                }
            }
    );

    public UploadProfilePictureFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_upload_profile_picture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = App.graph().auth;
        
        profileIconImage = view.findViewById(R.id.profileIconImage);
        progressUpload = view.findViewById(R.id.progressUpload);
        View btnUploadPicture = view.findViewById(R.id.btnUploadPicture);
        View btnSkipUpload = view.findViewById(R.id.btnSkipUpload);

        // Make the profile icon clickable to select image
        profileIconImage.setOnClickListener(v -> openImagePicker());

        btnUploadPicture.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                uploadProfilePicture();
            } else {
                openImagePicker();
            }
        });

        btnSkipUpload.setOnClickListener(v -> navigateToLocationPermission());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadProfilePicture() {
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getUid();
        if (uid == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        progressUpload.setVisibility(View.VISIBLE);

        try {
            // Upload to Firebase Storage with timestamp to ensure unique filename
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference();
            String filename = uid + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference profilePicRef = storageRef.child("profile_pictures").child(filename);

            android.util.Log.d("UploadPicture", "Starting upload to: profile_pictures/" + filename);
            android.util.Log.d("UploadPicture", "Storage bucket: " + storage.getApp().getOptions().getStorageBucket());

            profilePicRef.putFile(selectedImageUri)
                    .addOnProgressListener(snapshot -> {
                        double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                        android.util.Log.d("UploadPicture", "Upload progress: " + progress + "%");
                    })
                    .addOnSuccessListener(taskSnapshot -> {
                        android.util.Log.d("UploadPicture", "Upload successful!");
                        // Get download URL
                        profilePicRef.getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    android.util.Log.d("UploadPicture", "Got download URL: " + uri.toString());
                                    // Save URL to Firestore
                                    saveProfilePictureUrl(uid, uri.toString());
                                })
                                .addOnFailureListener(e -> {
                                    progressUpload.setVisibility(View.GONE);
                                    android.util.Log.e("UploadPicture", "Failed to get download URL", e);
                                    Toast.makeText(requireContext(), "Failed to get download URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        progressUpload.setVisibility(View.GONE);
                        android.util.Log.e("UploadPicture", "Upload failed", e);
                        Toast.makeText(requireContext(), "Upload failed: " + e.getMessage() + 
                                "\n\nPlease ensure Firebase Storage is enabled in your Firebase Console.", Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            progressUpload.setVisibility(View.GONE);
            android.util.Log.e("UploadPicture", "Exception during upload", e);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveProfilePictureUrl(String uid, String photoUrl) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("photoUrl", photoUrl);
        updates.put("updatedAt", System.currentTimeMillis());

        // Use set with merge to create document if it doesn't exist
        db.collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    progressUpload.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Profile picture uploaded!", Toast.LENGTH_SHORT).show();
                    navigateToLocationPermission();
                })
                .addOnFailureListener(e -> {
                    progressUpload.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Failed to save profile picture: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToLocationPermission() {
        try {
            if (isAdded() && getView() != null) {
                NavHostFragment.findNavController(this).navigate(R.id.action_upload_to_locationPermission);
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Navigation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}

