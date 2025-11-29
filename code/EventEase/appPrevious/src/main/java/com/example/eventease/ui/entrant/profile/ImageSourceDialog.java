package com.example.eventease.ui.entrant.profile;

import android.app.Activity;
import android.app.Dialog;

import androidx.appcompat.widget.AppCompatButton;
import com.example.eventease.R;

/**
 * Dialog for selecting image source (camera or gallery).
 */
public class ImageSourceDialog {
    
    private final Dialog dialog;
    private final ProfileImageHelper imageHelper;
    
    /**
     * Creates and shows the image source selection dialog.
     * 
     * @param activity the activity
     * @param imageHelper the ProfileImageHelper for handling image operations
     */
    public ImageSourceDialog(Activity activity, ProfileImageHelper imageHelper) {
        this.imageHelper = imageHelper;
        
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        DialogBlurHelper.setupBlurredDialog(dialog, activity, R.layout.entrant_dialog_image_source);
        
        AppCompatButton cameraButton = dialog.findViewById(R.id.btnCamera);
        AppCompatButton galleryButton = dialog.findViewById(R.id.btnGallery);

        if (cameraButton != null) {
            cameraButton.setOnClickListener(v -> {
                dialog.dismiss();
                imageHelper.openCamera();
            });
        }

        if (galleryButton != null) {
            galleryButton.setOnClickListener(v -> {
                dialog.dismiss();
                imageHelper.openGallery();
            });
        }

        dialog.show();
        DialogBlurHelper.applyDialogAnimations(dialog, activity);
    }
}

