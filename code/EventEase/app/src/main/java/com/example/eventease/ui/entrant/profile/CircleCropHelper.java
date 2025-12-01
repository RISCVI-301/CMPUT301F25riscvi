package com.example.eventease.ui.entrant.profile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;

/**
 * Helper class for circle cropping profile pictures.
 * Provides Instagram-like circle crop functionality with zoom and pan.
 */
public class CircleCropHelper {
    private static final String TAG = "CircleCropHelper";
    
    /**
     * Shows a circle crop dialog for the given image URI.
     * 
     * @param context the context
     * @param imageUri the URI of the image to crop
     * @param callback callback when crop is complete
     */
    public static void showCircleCropDialog(Context context, Uri imageUri, 
                                           CircleCropCallback callback) {
        if (context == null || imageUri == null) {
            Log.e(TAG, "Context or imageUri is null");
            return;
        }
        
        Dialog cropDialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        // Check if context is an Activity for DialogBlurHelper
        if (context instanceof android.app.Activity) {
            DialogBlurHelper.setupBlurredDialog(cropDialog, (android.app.Activity) context, R.layout.dialog_crop_profile_circle);
        } else {
            // Fallback: set up dialog without blur
            cropDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            cropDialog.setContentView(R.layout.dialog_crop_profile_circle);
            if (cropDialog.getWindow() != null) {
                cropDialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT, 
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                );
                cropDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                );
            }
        }
        
        View blurBackground = cropDialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> cropDialog.dismiss());
        }
        
        ImageView cropImageView = cropDialog.findViewById(R.id.profilePreviewCrop);
        MaterialButton btnDone = cropDialog.findViewById(R.id.btnSaveCrop);
        MaterialButton btnCancel = cropDialog.findViewById(R.id.btnCancelCrop);
        
        if (cropImageView == null) {
            Log.e(TAG, "Crop ImageView not found");
            cropDialog.dismiss();
            return;
        }
        
        // Load image into crop view
        Glide.with(context)
                .asBitmap()
                .load(imageUri)
                .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, 
                                                @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        setupCircleCropView(cropImageView, resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                });
        
        // Setup buttons
        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                Matrix cropMatrix = (Matrix) cropImageView.getTag();
                if (cropMatrix != null && imageUri != null) {
                    // Reload bitmap to extract cropped circle
                    Glide.with(context)
                            .asBitmap()
                            .load(imageUri)
                            .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(@NonNull Bitmap originalBitmap,
                                                            @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                                    Bitmap croppedCircle = extractCircleBitmap(
                                            originalBitmap, cropMatrix, 
                                            cropImageView.getWidth(), cropImageView.getHeight());
                                    if (croppedCircle != null && callback != null) {
                                        callback.onCropComplete(croppedCircle);
                                    }
                                    cropDialog.dismiss();
                                }
                                
                                @Override
                                public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                            });
                } else {
                    cropDialog.dismiss();
                }
            });
        }
        
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> cropDialog.dismiss());
        }
        
        // Setup touch listeners for pan and zoom
        setupPanAndZoom(cropImageView);
        
        cropDialog.show();
        
        // Apply animations
        android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(
                context, R.anim.entrant_dialog_fade_in);
        android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(
                context, R.anim.entrant_dialog_zoom_in);
        
        androidx.cardview.widget.CardView cardView = cropDialog.findViewById(R.id.dialogCardView);
        if (blurBackground != null) {
            blurBackground.startAnimation(fadeIn);
        }
        if (cardView != null) {
            cardView.startAnimation(zoomIn);
        }
    }
    
    /**
     * Sets up the crop view with initial image, ensuring it fills the circle
     */
    private static void setupCircleCropView(ImageView cropView, Bitmap bitmap) {
        if (cropView == null || bitmap == null) return;
        
        cropView.setImageBitmap(bitmap);
        cropView.setScaleType(ImageView.ScaleType.MATRIX);
        
        Matrix matrix = new Matrix();
        int viewSize = Math.min(cropView.getWidth(), cropView.getHeight());
        
        if (viewSize == 0) {
            cropView.post(() -> setupCircleCropView(cropView, bitmap));
            return;
        }
        
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        
        // Scale to fill the circle (use the larger dimension to ensure no gaps)
        float scale = Math.max(viewSize / bitmapWidth, viewSize / bitmapHeight);
        
        // Center the image
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float dx = (viewSize - scaledWidth) / 2f;
        float dy = (viewSize - scaledHeight) / 2f;
        
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        
        cropView.setImageMatrix(matrix);
        cropView.setTag(matrix);
    }
    
    /**
     * Extracts a circular bitmap from the original based on the crop view's matrix
     */
    private static Bitmap extractCircleBitmap(Bitmap originalBitmap, Matrix cropMatrix,
                                             int viewWidth, int viewHeight) {
        if (originalBitmap == null || cropMatrix == null) return null;
        
        try {
            int size = Math.min(viewWidth, viewHeight);
            int radius = size / 2;
            int outputSize = size;
            
            // Create inverse matrix to map from view coordinates to bitmap coordinates
            Matrix inverseMatrix = new Matrix();
            if (!cropMatrix.invert(inverseMatrix)) {
                Log.e(TAG, "Failed to invert matrix, using fallback");
                return createCircularBitmap(originalBitmap, outputSize);
            }
            
            // Get center of the circle in view coordinates
            float centerX = viewWidth / 2f;
            float centerY = viewHeight / 2f;
            
            // Map center to bitmap coordinates
            float[] centerPoint = {centerX, centerY};
            inverseMatrix.mapPoints(centerPoint);
            
            // Calculate the radius in bitmap coordinates
            // Map a point on the circle edge
            float[] edgePoint = {centerX + radius, centerY};
            inverseMatrix.mapPoints(edgePoint);
            float bitmapRadius = (float) Math.sqrt(
                Math.pow(edgePoint[0] - centerPoint[0], 2) + 
                Math.pow(edgePoint[1] - centerPoint[1], 2)
            );
            
            // Clamp to bitmap bounds
            float srcLeft = Math.max(0, centerPoint[0] - bitmapRadius);
            float srcTop = Math.max(0, centerPoint[1] - bitmapRadius);
            float srcRight = Math.min(originalBitmap.getWidth(), centerPoint[0] + bitmapRadius);
            float srcBottom = Math.min(originalBitmap.getHeight(), centerPoint[1] + bitmapRadius);
            
            // Ensure valid rectangle
            if (srcRight <= srcLeft || srcBottom <= srcTop) {
                Log.e(TAG, "Invalid source rectangle, using fallback");
                return createCircularBitmap(originalBitmap, outputSize);
            }
            
            // Create circular bitmap
            Bitmap circleBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(circleBitmap);
            
            // Draw the cropped region
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            android.graphics.Rect srcRect = new android.graphics.Rect(
                (int) srcLeft, 
                (int) srcTop, 
                (int) srcRight, 
                (int) srcBottom
            );
            RectF dstRect = new RectF(0, 0, outputSize, outputSize);
            
            canvas.drawBitmap(originalBitmap, srcRect, dstRect, paint);
            
            // Apply circular mask
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawCircle(outputSize / 2f, outputSize / 2f, radius, maskPaint);
            
            return circleBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract circle bitmap", e);
            return createCircularBitmap(originalBitmap, Math.min(viewWidth, viewHeight));
        }
    }
    
    /**
     * Creates a circular bitmap from the original (fallback method)
     */
    private static Bitmap createCircularBitmap(Bitmap original, int size) {
        if (original == null) return null;
        
        Bitmap circleBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(circleBitmap);
        
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF dstRect = new RectF(0, 0, size, size);
        
        // Scale and center the bitmap
        float scale = Math.max((float) size / original.getWidth(), (float) size / original.getHeight());
        float scaledWidth = original.getWidth() * scale;
        float scaledHeight = original.getHeight() * scale;
        float dx = (size - scaledWidth) / 2f;
        float dy = (size - scaledHeight) / 2f;
        
        android.graphics.Rect srcRect = new android.graphics.Rect(
            (int) Math.max(0, -dx / scale), 
            (int) Math.max(0, -dy / scale), 
            (int) Math.min(original.getWidth(), (size - dx) / scale), 
            (int) Math.min(original.getHeight(), (size - dy) / scale)
        );
        
        canvas.drawBitmap(original, srcRect, dstRect, paint);
        
        // Apply circular mask
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, maskPaint);
        
        return circleBitmap;
    }
    
    /**
     * Sets up pan and zoom gestures for the crop view
     */
    private static void setupPanAndZoom(ImageView cropView) {
        if (cropView == null) return;
        
        cropView.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;
            private float startDistance;
            private Matrix savedMatrix = new Matrix();
            private static final int NONE = 0;
            private static final int DRAG = 1;
            private static final int ZOOM = 2;
            private int mode = NONE;
            
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                ImageView view = (ImageView) v;
                Matrix matrix = (Matrix) view.getTag();
                if (matrix == null) {
                    matrix = new Matrix(view.getImageMatrix());
                    view.setTag(matrix);
                }
                
                switch (event.getAction() & android.view.MotionEvent.ACTION_MASK) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        savedMatrix.set(matrix);
                        lastX = event.getX();
                        lastY = event.getY();
                        mode = DRAG;
                        break;
                        
                    case android.view.MotionEvent.ACTION_POINTER_DOWN:
                        startDistance = getDistance(event);
                        if (startDistance > 10f) {
                            savedMatrix.set(matrix);
                            mode = ZOOM;
                        }
                        break;
                        
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                        
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                        } else if (mode == ZOOM) {
                            float newDistance = getDistance(event);
                            if (newDistance > 10f) {
                                matrix.set(savedMatrix);
                                float scale = newDistance / startDistance;
                                float centerX = (event.getX(0) + event.getX(1)) / 2f;
                                float centerY = (event.getY(0) + event.getY(1)) / 2f;
                                matrix.postScale(scale, scale, centerX, centerY);
                            }
                        }
                        break;
                }
                
                view.setImageMatrix(matrix);
                view.setTag(matrix);
                return true;
            }
            
            private float getDistance(android.view.MotionEvent event) {
                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);
                return (float) Math.sqrt(dx * dx + dy * dy);
            }
        });
    }
    
    /**
     * Callback interface for when circle crop is complete
     */
    public interface CircleCropCallback {
        void onCropComplete(Bitmap croppedCircleBitmap);
    }
}

