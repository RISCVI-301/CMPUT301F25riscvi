package com.example.eventease.ui.entrant.profile;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.WindowManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.graphics.Color;
import com.example.eventease.R;

/**
 * Helper class for creating blurred dialog backgrounds.
 * Handles screenshot capture and blur effects for dialogs.
 */
public class DialogBlurHelper {
    
    /**
     * Sets up a full-screen dialog with blurred background.
     * 
     * @param dialog the dialog to configure
     * @param activity the activity to capture screenshot from
     * @param dialogLayoutId the layout resource ID for the dialog
     */
    public static void setupBlurredDialog(Dialog dialog, Activity activity, int dialogLayoutId) {
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogLayoutId);
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
        Bitmap screenshot = captureScreenshot(activity);
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f, activity);
            if (blurredBitmap != null) {
                View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(activity.getResources(), blurredBitmap));
                }
            }
        }
        
        // Make the background clickable to dismiss
        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }
    }
    
    /**
     * Applies animations to dialog elements.
     * 
     * @param dialog the dialog to animate
     * @param context the context for loading animations
     */
    public static void applyDialogAnimations(Dialog dialog, Context context) {
        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }
    
    /**
     * Captures a screenshot of the current activity.
     * 
     * @param activity the activity to capture
     * @return the screenshot bitmap, or null if capture failed
     */
    private static Bitmap captureScreenshot(Activity activity) {
        try {
            if (activity == null || activity.getWindow() == null) return null;
            android.view.View rootView = activity.getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Blurs a bitmap using RenderScript.
     * 
     * @param bitmap the bitmap to blur
     * @param radius the blur radius
     * @param context the context for RenderScript
     * @return the blurred bitmap, or the original if blur failed
     */
    private static Bitmap blurBitmap(Bitmap bitmap, float radius, Context context) {
        if (bitmap == null || context == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(context);
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

