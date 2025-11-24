package com.example.eventease.auth;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import java.io.File;
import java.io.IOException;

import com.bumptech.glide.Glide;
import com.example.eventease.App;
import com.example.eventease.R;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment for uploading user profile picture during signup.
 * Supports image selection from gallery or camera capture, uploads to Firebase Storage.
 */
public class UploadProfilePictureFragment extends Fragment {

    private ImageView profileIconImage;
    private ProgressBar progressUpload;
    private Uri selectedImageUri;
    private AuthManager auth;
    private File photoFile;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    // Change scaleType to centerCrop when image is selected
                    profileIconImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    // Show selected image in the circle with centerCrop and circular clipping
                    Glide.with(this)
                            .load(selectedImageUri)
                            .centerCrop()
                            .circleCrop()
                            .into(profileIconImage);
                }
            }
    );
    
    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openCameraInternal();
                } else {
                    Toast.makeText(getContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                }
            }
    );
    
    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && selectedImageUri != null) {
                    // Change scaleType to centerCrop when image is captured
                    profileIconImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    // Show captured image in the circle with centerCrop and circular clipping
                    Glide.with(this)
                            .load(selectedImageUri)
                            .centerCrop()
                            .circleCrop()
                            .into(profileIconImage);
                }
            }
    );

    public UploadProfilePictureFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_upload_profile_picture, container, false);
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
        profileIconImage.setOnClickListener(v -> showImageSourceDialog());

        btnUploadPicture.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                uploadProfilePicture();
            } else {
                showImageSourceDialog();
            }
        });

        btnSkipUpload.setOnClickListener(v -> navigateToLocationPermission());
    }

    private void showImageSourceDialog() {
        if (getContext() == null) return;
        
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_image_source);
        dialog.setCanceledOnTouchOutside(false);

        // Set window properties for full screen blur
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Capture screenshot and blur it for the background
        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }
        
        // Make the background clickable to dismiss
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }

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
        
        // Apply animations after dialog is shown
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }
    
    private void openCamera() {
        if (getContext() == null) return;
        
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            // Permission already granted, open camera
            openCameraInternal();
        }
    }
    
    private void openCameraInternal() {
        if (getContext() == null) return;
        
        try {
            // Create a File object for the photo
            photoFile = File.createTempFile(
                "profile_photo_" + System.currentTimeMillis(),
                ".jpg",
                getContext().getCacheDir()
            );
            
            // Create a content URI for the file using FileProvider
            selectedImageUri = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                photoFile
            );
            
            // Launch the camera intent
            takePictureLauncher.launch(selectedImageUri);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to create image file", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
    
    private Bitmap captureScreenshot() {
        try {
            if (getActivity() == null || getActivity().getWindow() == null) return null;
            View rootView = getActivity().getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null || requireContext() == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(requireContext());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            
            rs.destroy();
            
            // Scale back up
            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }
}

