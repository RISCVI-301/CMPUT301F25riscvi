package com.example.eventease.ui.entrant.profile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.example.eventease.util.ToastUtil;
import java.io.File;
import java.io.IOException;

/**
 * Helper class for handling profile image operations.
 * Manages camera, gallery, and image upload functionality.
 */
public class ProfileImageHelper {
    
    private final Context context;
    private final ActivityResultLauncher<String> pickImage;
    private final ActivityResultLauncher<String> requestCameraPermission;
    private final ActivityResultLauncher<Uri> takePicture;
    private final ImageView profileImage;
    
    private Uri selectedImageUri;
    private File photoFile;
    
    /**
     * Callback interface for when an image is selected.
     */
    public interface ImageSelectedCallback {
        void onImageSelected(Uri imageUri);
    }
    
    /**
     * Creates a new ProfileImageHelper.
     * 
     * @param fragment the fragment for registering activity results
     * @param profileImage the ImageView to display the selected image
     * @param imageSelectedCallback callback when image is selected
     */
    public ProfileImageHelper(Fragment fragment, ImageView profileImage, ImageSelectedCallback imageSelectedCallback) {
        this.context = fragment.getContext();
        this.profileImage = profileImage;
        
        this.pickImage = fragment.registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    // Show circle crop dialog instead of directly setting image
                    if (context != null) {
                        CircleCropHelper.showCircleCropDialog(context, uri, 
                            croppedBitmap -> {
                                // Save cropped bitmap to a file and update URI
                                try {
                                    java.io.File croppedFile = new java.io.File(
                                        context.getCacheDir(), 
                                        "cropped_profile_" + System.currentTimeMillis() + ".jpg"
                                    );
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(croppedFile);
                                    croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                                    fos.flush();
                                    fos.close();
                                    
                                    selectedImageUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        context.getPackageName() + ".fileprovider",
                                        croppedFile
                                    );
                                    
                                    // Update profile image with cropped version
                                    profileImage.setImageBitmap(croppedBitmap);
                                    if (imageSelectedCallback != null) {
                                        imageSelectedCallback.onImageSelected(selectedImageUri);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    ToastUtil.showShort(context, "Failed to save cropped image");
                                }
                            }
                        );
                    }
                }
            }
        );
        
        this.requestCameraPermission = fragment.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openCameraInternal();
                } else {
                    ToastUtil.showShort(context, "Camera permission is required to take photos");
                }
            }
        );
        
        this.takePicture = fragment.registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && selectedImageUri != null && context != null) {
                    // Show circle crop dialog for camera image too
                    CircleCropHelper.showCircleCropDialog(context, selectedImageUri, 
                        croppedBitmap -> {
                            // Save cropped bitmap to a file and update URI
                            try {
                                java.io.File croppedFile = new java.io.File(
                                    context.getCacheDir(), 
                                    "cropped_profile_" + System.currentTimeMillis() + ".jpg"
                                );
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(croppedFile);
                                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                                fos.flush();
                                fos.close();
                                
                                selectedImageUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.getPackageName() + ".fileprovider",
                                    croppedFile
                                );
                                
                                // Update profile image with cropped version
                                profileImage.setImageBitmap(croppedBitmap);
                                if (imageSelectedCallback != null) {
                                    imageSelectedCallback.onImageSelected(selectedImageUri);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                ToastUtil.showShort(context, "Failed to save cropped image");
                            }
                        }
                    );
                }
            }
        );
    }
    
    /**
     * Gets the currently selected image URI.
     * 
     * @return the selected image URI, or null if none selected
     */
    public Uri getSelectedImageUri() {
        return selectedImageUri;
    }
    
    /**
     * Sets the selected image URI.
     * 
     * @param uri the image URI
     */
    public void setSelectedImageUri(Uri uri) {
        this.selectedImageUri = uri;
    }
    
    /**
     * Opens the camera to take a photo.
     * Checks for camera permission first.
     */
    public void openCamera() {
        if (context == null) return;
        
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            // Permission already granted, open camera
            openCameraInternal();
        }
    }
    
    /**
     * Opens the gallery to pick an image.
     */
    public void openGallery() {
        pickImage.launch("image/*");
    }
    
    /**
     * Opens the camera internally (assumes permission is granted).
     */
    private void openCameraInternal() {
        if (context == null) return;
        
        try {
            // Create a File object for the photo
            photoFile = File.createTempFile(
                "profile_photo_" + System.currentTimeMillis(),
                ".jpg",
                context.getCacheDir()
            );
            
            // Create a content URI for the file using FileProvider
            selectedImageUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                photoFile
            );
            
            // Launch the camera intent
            takePicture.launch(selectedImageUri);
        } catch (IOException e) {
            e.printStackTrace();
            ToastUtil.showShort(context, "Failed to create image file");
        } catch (Exception e) {
            e.printStackTrace();
            ToastUtil.showShort(context, "Failed to open camera: " + e.getMessage());
        }
    }
}

