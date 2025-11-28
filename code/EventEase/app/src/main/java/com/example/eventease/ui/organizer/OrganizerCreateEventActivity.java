package com.example.eventease.ui.organizer;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Activity for an organizer to create a new event.
 * 
 * <p>This class provides a comprehensive form for organizers to input event details including:
 * <ul>
 *   <li>Event title, description, location, and guidelines</li>
 *   <li>Registration period (start and end times)</li>
 *   <li>Deadline to accept/decline invitations</li>
 *   <li>Event date and event start date</li>
 *   <li>Event deadline (for replacements)</li>
 *   <li>Event capacity</li>
 *   <li>Event poster image</li>
 *   <li>QR code generation options</li>
 *   <li>Geolocation tracking options</li>
 * </ul>
 * 
 * <p>The activity performs validation on user input before saving the event. The creation process involves:
 * <ol>
 *   <li>Uploading the poster image to Firebase Storage (if provided)</li>
 *   <li>Generating a QR code for the event (if enabled)</li>
 *   <li>Creating a new document in the 'events' collection in Firestore</li>
 *   <li>Initializing subcollections for waitlist, admitted entrants, etc.</li>
 * </ol>
 * 
 * <p>After successful creation, the organizer is returned to the event list view.
 */
public class OrganizerCreateEventActivity extends AppCompatActivity {
    private static final String TAG = "CreateEvent";
    private static final int MAX_TAGS = 5;
    // --- UI Elements ---
    private ImageButton btnBack, btnPickPoster;
    private ImageView posterPreview;
    private androidx.cardview.widget.CardView posterPreviewCard;
    private com.google.android.material.button.MaterialButton btnCropPoster;
    private EditText etTitle, etDescription, etGuidelines, etLocation, etCapacity, etSampleSize, etTags;
    private ChipGroup chipGroupTags;
    private TextView tvSampleSize;
    private Button btnStart, btnEnd, btnDeadline, btnEventStart, btnSave;
    private Switch swGeo, swQr;
    private RadioGroup rgEntrants;
    private RadioButton rbAny, rbSpecific;

    // --- Data Holders ---
    private long regStartEpochMs = 0L, regEndEpochMs = 0L, deadlineEpochMs = 0L;
    private long eventStartEpochMs = 0L;
    private Uri posterUri = null;
    private String organizerId;
    private boolean isResolvingOrganizerId;
    private final ArrayList<String> interests = new ArrayList<>();
    
    // Image cropping
    private android.graphics.Matrix imageMatrix;
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;

    /**
     * Handles the result of the image picker intent.
     * When an image is selected from the gallery, its URI is stored and automatically opens crop dialog.
     */
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    posterUri = uri;
                    // Show preview and load image into preview
                    if (posterPreviewCard != null) {
                        posterPreviewCard.setVisibility(View.VISIBLE);
                    }
                    if (btnCropPoster != null) {
                        btnCropPoster.setVisibility(View.VISIBLE);
                    }
                    if (posterPreview != null) {
                        loadImageIntoPreview(uri);
                    }
                    // Also update the button icon to show image was selected
                    Glide.with(this).load(uri).into(btnPickPoster);
                    
                    // Automatically open crop dialog when first selecting an image
                    // Use post to ensure UI is ready
                    posterPreview.post(() -> {
                        if (posterPreview.getWidth() > 0 && posterPreview.getHeight() > 0) {
                            showCropDialog();
                        } else {
                            // Wait a bit more if view not measured yet
                            posterPreview.postDelayed(() -> showCropDialog(), 200);
                        }
                    });
                }
            });
    /**
     * Initializes the activity, sets up UI components, and attaches click listeners.
     * @param savedInstanceState If the activity is being re-initialized, this Bundle contains the most recent data.
     */
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organizer_create_event);

        try { FirebaseApp.initializeApp(this); } catch (Exception ignored) {}
        try { com.google.firebase.firestore.FirebaseFirestore.setLoggingEnabled(true); } catch (Throwable ignored) {}

        btnBack = findViewById(R.id.btnBack);
        btnPickPoster = findViewById(R.id.btnPickPoster);
        posterPreview = findViewById(R.id.posterPreview);
        posterPreviewCard = findViewById(R.id.posterPreviewCard);
        btnCropPoster = findViewById(R.id.btnCropPoster);
        etTitle = findViewById(R.id.etTitle);
        
        // Initialize image matrix for cropping
        imageMatrix = new android.graphics.Matrix();
        if (posterPreview != null) {
            posterPreview.setScaleType(ImageView.ScaleType.MATRIX);
        }
        etDescription = findViewById(R.id.etDescription);
        etGuidelines = findViewById(R.id.etGuidelines);
        etLocation = findViewById(R.id.etLocation);
        etCapacity = findViewById(R.id.etCapacity);
        etSampleSize = findViewById(R.id.etSampleSize);
        tvSampleSize = findViewById(R.id.tvSampleSize);
        etTags = findViewById(R.id.etTags);
        chipGroupTags = findViewById(R.id.chipGroupTags);
        btnStart = findViewById(R.id.btnStart);
        btnEnd = findViewById(R.id.btnEnd);
        btnDeadline = findViewById(R.id.btnDeadline);
        btnEventStart = findViewById(R.id.btnEventStart);
        btnSave = findViewById(R.id.btnSave);
        swGeo = findViewById(R.id.swGeo);
        swQr = findViewById(R.id.swQr);
        rgEntrants = findViewById(R.id.rgEntrants);
        rbAny = findViewById(R.id.rbAny);
        rbSpecific = findViewById(R.id.rbSpecific);
        setupTagInput();

        organizerId = getIntent().getStringExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID);
        if (organizerId == null || organizerId.trim().isEmpty()) {
            resolveOrganizerId(null);
        }

        rgEntrants.setOnCheckedChangeListener((g, checkedId) -> {
            boolean specific = (checkedId == R.id.rbSpecific);
            etCapacity.setEnabled(specific);
            etCapacity.setVisibility(specific ? View.VISIBLE : View.GONE);
            // Sample size is always visible, no need to control its visibility here
            if (!specific) {
                etCapacity.setText("");
            } else {
                etCapacity.requestFocus();
            }
        });

        etCapacity.setOnClickListener(v -> rgEntrants.check(R.id.rbSpecific));
        etCapacity.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                rgEntrants.check(R.id.rbSpecific);
            }
        });

        btnStart.setOnClickListener(v -> pickDateTime(true));
        btnEnd.setOnClickListener(v -> pickDateTime(false));
        btnDeadline.setOnClickListener(v -> pickDeadline());
        btnEventStart.setOnClickListener(v -> pickEventStartDate());
        btnBack.setOnClickListener(v -> finish());
        btnPickPoster.setOnClickListener(v -> pickImage.launch("image/*"));
        
        // Setup crop button
        if (btnCropPoster != null) {
            btnCropPoster.setOnClickListener(v -> showCropDialog());
        }
        btnSave.setOnClickListener(v -> beginSaveEvent());
    }

    private void setupTagInput() {
        if (etTags == null) {
            return;
        }
        etTags.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_NEXT) {
                addTagFromInput();
                return true;
            }
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN) {
                addTagFromInput();
                return true;
            }
            return false;
        });
        etTags.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                addTagFromInput();
                return true;
            }
            return false;
        });
    }

    private void addTagFromInput() {
        if (etTags == null) {
            return;
        }
        String raw = safe(etTags.getText());
        if (raw.isEmpty()) {
            return;
        }
        addTag(raw);
        etTags.setText("");
    }

    private void addTag(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return;
        }
        String normalized = tag.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (interests.size() >= MAX_TAGS) {
            toast("You can add up to " + MAX_TAGS + " tags");
            return;
        }
        for (String existing : interests) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        interests.add(normalized);
        addChipForTag(normalized);
    }

    private void addChipForTag(String tag) {
        if (chipGroupTags == null) {
            return;
        }
        Chip chip = new Chip(this);
        chip.setText(tag);
        chip.setTextColor(ContextCompat.getColor(this, R.color.ee_text_light));
        chip.setChipBackgroundColorResource(R.color.ee_card);
        chip.setCloseIconVisible(true);
        chip.setCloseIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        chip.setCloseIconTintResource(R.color.ee_text_light);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setOnCloseIconClickListener(v -> {
            interests.removeIf(s -> s.equalsIgnoreCase(tag));
            chipGroupTags.removeView(chip);
        });
        chipGroupTags.addView(chip);
    }
    /**
     * Displays a DatePickerDialog followed by a TimePickerDialog to allow the user
     * to select a specific date and time.
     *
     * @param isStart A boolean flag to determine if the selected date/time is for the
     *                registration start or end.
     */
    private void pickDateTime(boolean isStart) {
        final Calendar now = Calendar.getInstance();
        
        // Calculate minimum date - prevent past dates
        long minDateMs = System.currentTimeMillis();
        if (!isStart && regStartEpochMs > 0) {
            // Registration end must be after registration start
            minDateMs = Math.max(minDateMs, regStartEpochMs);
        }
        
        DatePickerDialog dp = new DatePickerDialog(
                this, (view, y, m, d) -> {
            TimePickerDialog tp = new TimePickerDialog(
                    this, (vv, hh, mm) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, d, hh, mm, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                
                // Validate not in the past
                if (chosen.getTimeInMillis() < System.currentTimeMillis()) {
                    toast("Cannot select a past date/time");
                    return;
                }
                
                // Validate registration end is after start
                if (!isStart && regStartEpochMs > 0 && chosen.getTimeInMillis() <= regStartEpochMs) {
                    toast("Registration End must be after Registration Start");
                    return;
                }
                
                long ts = chosen.getTimeInMillis();
                if (isStart) {
                    regStartEpochMs = ts;
                    btnStart.setText(android.text.format.DateFormat
                            .format("MMM d, yyyy  h:mm a", chosen));
                } else {
                    regEndEpochMs = ts;
                    btnEnd.setText(android.text.format.DateFormat
                            .format("MMM d, yyyy  h:mm a", chosen));
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
            tp.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        
        // Prevent selecting past dates
        dp.getDatePicker().setMinDate(minDateMs - 1000);
        dp.show();
    }

    private void pickDeadline() {
        final Calendar now = Calendar.getInstance();
        
        // Minimum date should be registration end if it's set
        long minDateMs = System.currentTimeMillis();
        if (regEndEpochMs > 0) {
            minDateMs = Math.max(minDateMs, regEndEpochMs);
        }
        
        Calendar minDate = Calendar.getInstance();
        minDate.setTimeInMillis(minDateMs);
        
        DatePickerDialog dp = new DatePickerDialog(
                this, (view, y, m, d) -> {
            TimePickerDialog tp = new TimePickerDialog(
                    this, (vv, hh, mm) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, d, hh, mm, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                
                // Validate not in the past
                if (chosen.getTimeInMillis() < System.currentTimeMillis()) {
                    toast("Cannot select a past date/time");
                    return;
                }
                
                // Validate deadline is after registration end
                if (regEndEpochMs > 0 && chosen.getTimeInMillis() <= regEndEpochMs) {
                    toast("Deadline to Accept/Reject must be after Registration End");
                    return;
                }
                
                // Validate deadline is before event start if event start is set
                if (eventStartEpochMs > 0 && chosen.getTimeInMillis() >= eventStartEpochMs) {
                    toast("Deadline to Accept/Reject must be before Event Start Date");
                    return;
                }
                
                deadlineEpochMs = chosen.getTimeInMillis();
                btnDeadline.setText(android.text.format.DateFormat
                        .format("MMM d, yyyy  h:mm a", chosen));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
            tp.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        
        // Prevent selecting past dates and dates before registration end
        dp.getDatePicker().setMinDate(minDateMs - 1000);
        dp.show();
    }

    private void pickEventStartDate() {
        final Calendar now = Calendar.getInstance();
        
        // Minimum date should be after deadline to accept/reject if it's set
        long minDateMs = System.currentTimeMillis();
        if (deadlineEpochMs > 0) {
            minDateMs = Math.max(minDateMs, deadlineEpochMs);
        }
        
        DatePickerDialog dp = new DatePickerDialog(
                this, (view, y, m, d) -> {
            TimePickerDialog tp = new TimePickerDialog(
                    this, (vv, hh, mm) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, d, hh, mm, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                
                // Validate not in the past
                if (chosen.getTimeInMillis() < System.currentTimeMillis()) {
                    toast("Cannot select a past date/time");
                    return;
                }
                
                // Validate event start is after deadline to accept/reject
                if (deadlineEpochMs > 0 && chosen.getTimeInMillis() <= deadlineEpochMs) {
                    toast("Event Start Date must be after Deadline to Accept/Reject");
                    return;
                }
                
                eventStartEpochMs = chosen.getTimeInMillis();
                btnEventStart.setText(android.text.format.DateFormat
                        .format("MMM d, yyyy  h:mm a", chosen));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
            tp.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        
        // Prevent selecting past dates and dates before deadline
        dp.getDatePicker().setMinDate(minDateMs - 1000);
        dp.show();
    }
    /**
     * Starts the process of saving the event. It first ensures the user is authenticated,
     * signing in anonymously if necessary, before proceeding to validation.
     */
    private void beginSaveEvent() {
        // Device auth - no need to sign in, just save
        doValidateAndSave();
    }

    private void resolveOrganizerId(@Nullable Runnable onReady) {
        if (organizerId != null && !organizerId.trim().isEmpty()) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (isResolvingOrganizerId) {
            return;
        }
        
        // Get device ID as organizer ID
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(this);
        String deviceId = authManager.getUid();
        
        if (deviceId == null || deviceId.isEmpty()) {
            toast("Could not get device ID");
            return;
        }
        
        isResolvingOrganizerId = true;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(deviceId)
                .get()
                .addOnSuccessListener(doc -> {
                    // Use device ID as organizer ID
                    organizerId = deviceId;
                    isResolvingOrganizerId = false;
                    
                    if (!doc.exists()) {
                        Log.w(TAG, "User document doesn't exist for device: " + deviceId);
                        toast("Please complete your profile setup first");
                    } else if (onReady != null) {
                        onReady.run();
                    }
                })
                .addOnFailureListener(e -> {
                    isResolvingOrganizerId = false;
                    toast("Failed to load organizer profile: " + e.getMessage());
                });
    }
    /**
     * Validates all the user-entered form data. If validation passes, it proceeds
     * to upload the event poster and save the event details.
     */
    private void doValidateAndSave() {
        addTagFromInput();
        String title = safe(etTitle.getText());
        if (title.isEmpty()) { etTitle.setError("Event name is required"); etTitle.requestFocus(); return; }
        if (title.length() > 80) { etTitle.setError("Max 80 characters"); etTitle.requestFocus(); return; }

        if (regStartEpochMs == 0L) { toast("Please pick Registration Start"); return; }
        if (regEndEpochMs == 0L) { toast("Please pick Registration End"); return; }
        if (regEndEpochMs < regStartEpochMs) { toast("Registration End must be after Registration Start"); return; }
        if (deadlineEpochMs == 0L) { toast("Please pick Deadline to Accept/Reject"); return; }
        if (eventStartEpochMs == 0L) { toast("Please pick Event Start Date"); return; }

        // Validation: Deadline to Accept/Reject must be after Registration End
        if (deadlineEpochMs <= regEndEpochMs) {
            toast("Deadline to Accept/Reject must be after Registration End");
            return;
        }

        // Validation: Deadline to Accept/Reject must be before Event Start Date
        if (deadlineEpochMs >= eventStartEpochMs) {
            toast("Deadline to Accept/Reject must be before Event Start Date");
            return;
        }

        if (posterUri == null) { toast("Please select an event poster"); return; }

        int chosenCapacity = -1;
        if (rbSpecific.isChecked()) {
            String capStr = safe(etCapacity.getText());
            if (capStr.isEmpty()) { etCapacity.setError("Enter waiting list capacity"); etCapacity.requestFocus(); return; }
            try {
                int v = Integer.parseInt(capStr);
                if (v < 1 || v > 500) { etCapacity.setError("1–500 only"); etCapacity.requestFocus(); return; }
                chosenCapacity = v;
            } catch (NumberFormatException e) {
                etCapacity.setError("Enter a number"); etCapacity.requestFocus(); return;
            }
        }
        
        // Sample size is always required, regardless of waitlist capacity setting
        int chosenSampleSize = 0;
        String sampleStr = safe(etSampleSize.getText());
        if (sampleStr.isEmpty()) { 
            etSampleSize.setError("Enter sample size"); 
            etSampleSize.requestFocus(); 
            return; 
        }
        try {
            int v = Integer.parseInt(sampleStr);
            if (v < 1) { 
                etSampleSize.setError("Must be at least 1"); 
                etSampleSize.requestFocus(); 
                return; 
            }
            // If waitlist capacity is specific, sample size cannot exceed it
            if (chosenCapacity > 0 && v > chosenCapacity) { 
                etSampleSize.setError("Sample size cannot exceed waiting list capacity"); 
                etSampleSize.requestFocus(); 
                return; 
            }
            chosenSampleSize = v;
        } catch (NumberFormatException e) {
            etSampleSize.setError("Enter a number"); 
            etSampleSize.requestFocus(); 
            return;
        }

        if (organizerId == null || organizerId.trim().isEmpty()) {
            toast("Fetching organizer profile…");
            resolveOrganizerId(this::doValidateAndSave);
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Saving…");
        doUploadAndSave(title, chosenCapacity, chosenSampleSize);
    }
    /**
     * Uploads the selected event poster to Firebase Storage and then calls
     * {@link #writeEventDoc} to save the event details to Firestore.
     *
     * @param title          The validated title of the event.
     * @param chosenCapacity The validated capacity of the event (-1 for unlimited).
     * @param chosenSampleSize The validated sample size (number of initial invitations).
     */
    private void doUploadAndSave(String title, int chosenCapacity, int chosenSampleSize) {
        final String id = UUID.randomUUID().toString();
        final StorageReference ref = FirebaseStorage.getInstance()
                .getReference("posters/" + id + ".jpg");

        StorageMetadata meta = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        // Apply crop transformation if image was cropped
        if (posterUri != null && imageMatrix != null && !imageMatrix.isIdentity()) {
            // Load bitmap, apply crop, and upload
            Glide.with(this)
                    .asBitmap()
                    .load(posterUri)
                    .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull android.graphics.Bitmap originalBitmap, 
                                                    @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                            // Apply crop transformation
                            android.graphics.Bitmap croppedBitmap = applyCropToBitmap(originalBitmap);
                            if (croppedBitmap != null) {
                                uploadCroppedBitmap(croppedBitmap, ref, meta, id, title, chosenCapacity, chosenSampleSize);
                            } else {
                                // Fallback to original upload
                                ref.putFile(posterUri, meta)
                                        .continueWithTask(task -> {
                                            if (!task.isSuccessful()) throw task.getException();
                                            return ref.getDownloadUrl();
                                        })
                                        .addOnSuccessListener(download -> writeEventDoc(id, title, chosenCapacity, chosenSampleSize, download.toString()))
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Poster upload failed", e);
                                            toast("Upload failed: " + e.getMessage());
                                            btnSave.setEnabled(true);
                                            btnSave.setText("SAVE CHANGES");
                                        });
                            }
                        }
                        
                        @Override
                        public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                    });
        } else {
            // No crop applied, upload original
            ref.putFile(posterUri, meta)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException();
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(download -> writeEventDoc(id, title, chosenCapacity, chosenSampleSize, download.toString()))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Poster upload failed", e);
                        toast("Upload failed: " + e.getMessage());
                        btnSave.setEnabled(true);
                        btnSave.setText("SAVE CHANGES");
                    });
        }
    }
    
    /**
     * Applies the crop transformation to the bitmap based on the current matrix.
     * Extracts the visible portion of the image as shown in the preview.
     */
    private android.graphics.Bitmap applyCropToBitmap(android.graphics.Bitmap originalBitmap) {
        if (originalBitmap == null || imageMatrix == null || posterPreview == null) {
            return null;
        }
        
        try {
            int previewWidth = posterPreview.getWidth();
            int previewHeight = posterPreview.getHeight();
            
            if (previewWidth == 0 || previewHeight == 0) {
                // Use default dimensions if view not measured
                previewWidth = 400;
                previewHeight = 200;
            }
            
            // Get the transformation matrix values
            float[] values = new float[9];
            imageMatrix.getValues(values);
            float scale = values[android.graphics.Matrix.MSCALE_X];
            float transX = values[android.graphics.Matrix.MTRANS_X];
            float transY = values[android.graphics.Matrix.MTRANS_Y];
            
            // Calculate the source rectangle in the original bitmap
            // The preview shows a portion of the original image based on scale and translation
            float srcLeft = -transX / scale;
            float srcTop = -transY / scale;
            float srcWidth = previewWidth / scale;
            float srcHeight = previewHeight / scale;
            
            // Ensure coordinates are within bitmap bounds
            srcLeft = Math.max(0, Math.min(srcLeft, originalBitmap.getWidth()));
            srcTop = Math.max(0, Math.min(srcTop, originalBitmap.getHeight()));
            srcWidth = Math.min(srcWidth, originalBitmap.getWidth() - srcLeft);
            srcHeight = Math.min(srcHeight, originalBitmap.getHeight() - srcTop);
            
            if (srcWidth <= 0 || srcHeight <= 0) {
                // Invalid crop, return original
                return originalBitmap;
            }
            
            // Create cropped bitmap from the source rectangle
            android.graphics.Bitmap croppedBitmap = android.graphics.Bitmap.createBitmap(
                originalBitmap,
                (int)srcLeft,
                (int)srcTop,
                (int)srcWidth,
                (int)srcHeight
            );
            
            // Scale to match preview aspect ratio if needed
            if (croppedBitmap.getWidth() != previewWidth || croppedBitmap.getHeight() != previewHeight) {
                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(
                    croppedBitmap, previewWidth, previewHeight, true);
                if (scaled != croppedBitmap) {
                    croppedBitmap.recycle();
                }
                return scaled;
            }
            
            return croppedBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply crop", e);
            return null;
        }
    }
    
    /**
     * Uploads a cropped bitmap to Firebase Storage
     */
    private void uploadCroppedBitmap(android.graphics.Bitmap bitmap, StorageReference ref, 
                                     StorageMetadata meta, String id, String title, 
                                     int chosenCapacity, int chosenSampleSize) {
        try {
            // Convert bitmap to byte array
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] imageData = baos.toByteArray();
            
            // Upload bytes
            ref.putBytes(imageData, meta)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException();
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(download -> writeEventDoc(id, title, chosenCapacity, chosenSampleSize, download.toString()))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Poster upload failed", e);
                        toast("Upload failed: " + e.getMessage());
                        btnSave.setEnabled(true);
                        btnSave.setText("SAVE CHANGES");
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload cropped bitmap", e);
            toast("Failed to process image: " + e.getMessage());
            btnSave.setEnabled(true);
            btnSave.setText("SAVE CHANGES");
        }
    }
    /**
     * Writes the complete event document to the 'events' collection in Firestore.
     *
     * @param id             The unique ID generated for this event.
     * @param title          The title of the event.
     * @param chosenCapacity The maximum number of attendees (-1 for no limit).
     * @param chosenSampleSize The number of initial invitations to send.
     * @param posterUrl      The public URL of the uploaded poster in Firebase Storage.
     */
    private void writeEventDoc(String id, String title, int chosenCapacity, int chosenSampleSize, String posterUrl) {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            toast("Organizer profile not configured.");
            btnSave.setEnabled(true);
            btnSave.setText("SAVE CHANGES");
            return;
        }
        String description = safe(etDescription.getText());
        String guidelines = safe(etGuidelines.getText());
        String location = safe(etLocation.getText());
        boolean useGeo = swGeo.isChecked();
        boolean generateQr = swQr.isChecked();

        Map<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("title", title);
        doc.put("description", TextUtils.isEmpty(description) ? null : description);
        doc.put("notes", TextUtils.isEmpty(description) ? null : description);
        doc.put("guidelines", TextUtils.isEmpty(guidelines) ? null : guidelines);
        doc.put("location", TextUtils.isEmpty(location) ? null : location);
        doc.put("interests", new ArrayList<>(interests));
        doc.put("registrationStart", regStartEpochMs);
        doc.put("registrationEnd", regEndEpochMs);
        doc.put("deadlineEpochMs", deadlineEpochMs);
        doc.put("eventStart", eventStartEpochMs);
        doc.put("eventStartEpochMs", eventStartEpochMs); // Also save with consistent naming
        doc.put("capacity", chosenCapacity);
        doc.put("sampleSize", chosenSampleSize);
        doc.put("geolocation", useGeo);
        doc.put("qrEnabled", generateQr);
        doc.put("posterUrl", posterUrl);
        doc.put("organizerId", organizerId);
        doc.put("createdAt", System.currentTimeMillis());
        doc.put("createdAtEpochMs", System.currentTimeMillis());
        // Use custom scheme format that works better with QR scanners
        doc.put("qrPayload", generateQr ? ("eventease://event/" + id) : null);
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(id)
                .set(doc)
                .addOnSuccessListener(v -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("SAVE CHANGES");
                    showSuccessDialog(id, title, generateQr ? ("eventease://event/" + id) : null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore write failed", e);
                    toast("Save failed: " + e.getMessage());
                    btnSave.setEnabled(true);
                    btnSave.setText("SAVE CHANGES");
                });
    }

    private void showSuccessDialog(String id, String title, @Nullable String qrPayload) {
        if (qrPayload == null) {
            showEventOptionsDialog(title);
        } else {
            showQrPreparationDialog(title, qrPayload);
        }
    }

    private void showQrPreparationDialog(String title, String qrPayload) {
        Dialog preparingDialog = createCardDialog(R.layout.dialog_event_created);
        TextView subtitle = preparingDialog.findViewById(R.id.tvSubtitle);
        TextView header = preparingDialog.findViewById(R.id.tvTitle);
        if (header != null) {
            header.setText("Event Created Successfully");
        }
        if (subtitle != null) {
            subtitle.setText("Generating QR code…");
        }
        preparingDialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            preparingDialog.dismiss();
            showQrDialog(title, qrPayload);
        }, 1200);
    }

    private void showEventOptionsDialog(String title) {
        Dialog dialog = createCardDialog(R.layout.dialog_event_options);
        TextView titleView = dialog.findViewById(R.id.tvEventTitle);
        TextView subtitleView = dialog.findViewById(R.id.tvEventSubtitle);
        MaterialButton btnViewEvents = dialog.findViewById(R.id.btnViewEvents);

        if (titleView != null) {
            titleView.setText("Event Created Successfully");
        }
        if (subtitleView != null) {
            subtitleView.setText("\"" + title + "\" is ready to share.");
        }

        if (btnViewEvents != null) {
            btnViewEvents.setOnClickListener(v -> {
                dialog.dismiss();
                goToMyEvents();
            });
        }

        dialog.show();
    }

    private void showQrDialog(String title, String qrPayload) {
        Dialog dialog = createCardDialog(R.layout.dialog_qr_preview);
        TextView titleView = dialog.findViewById(R.id.tvEventTitle);
        ImageView imgQr = dialog.findViewById(R.id.imgQr);
        MaterialButton btnShare = dialog.findViewById(R.id.btnShare);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSave);
        MaterialButton btnCopyLink = dialog.findViewById(R.id.btnCopyLink);
        MaterialButton btnViewEvents = dialog.findViewById(R.id.btnViewEvents);

        if (titleView != null) {
            titleView.setText(title);
        }

        Bitmap qrBitmap = generateQrBitmap(qrPayload);
        if (imgQr != null) {
            if (qrBitmap != null) {
                imgQr.setImageBitmap(qrBitmap);
            } else {
                imgQr.setImageResource(R.drawable.ic_event_poster_placeholder);
            }
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                if (qrBitmap != null) {
                    shareQrBitmap(qrBitmap, qrPayload);
                } else {
                    shareQrText(qrPayload);
                }
            });
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (qrBitmap != null) {
                    boolean saved = saveQrToGallery(qrBitmap, title);
                    if (saved) {
                        toast("Saved to gallery");
                    }
                } else {
                    toast("QR not ready yet.");
                }
            });
        }

        if (btnCopyLink != null) {
            btnCopyLink.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Event Link", qrPayload);
                clipboard.setPrimaryClip(clip);
                toast("Link copied to clipboard!");
            });
        }

        if (btnViewEvents != null) {
            btnViewEvents.setOnClickListener(v -> {
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private Dialog createCardDialog(@LayoutRes int layoutRes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(layoutRes);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private Bitmap generateQrBitmap(String payload) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 512, 512);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (WriterException e) {
            Log.e(TAG, "QR code generation failed", e);
            return null;
        }
    }

    private void shareQrBitmap(Bitmap bitmap, String payload) {
        try {
            File cacheDir = new File(getCacheDir(), "qr");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new IOException("Unable to create cache directory");
            }
            File file = new File(cacheDir, "qr_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Scan this QR to view the event: " + payload);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share QR code"));
        } catch (IOException e) {
            Log.e(TAG, "Failed to share QR bitmap", e);
            toast("Unable to share QR. Try again.");
        }
    }

    private void shareQrText(String payload) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(shareIntent, "Share event link"));
    }

    private boolean saveQrToGallery(Bitmap bitmap, String title) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("Saving to gallery requires Android 10 or newer. Use Share instead.");
            return false;
        }

        String fileName = "EventEase_" + System.currentTimeMillis() + ".png";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EventEase");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Unable to create MediaStore entry");
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Unable to open output stream");
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save QR to gallery", e);
            toast("Unable to save QR. Try again.");
            return false;
        }
    }

    private void goToMyEvents() {
        Intent intent = new Intent(this, com.example.eventease.ui.organizer.OrganizerMyEventActivity.class);
        if (organizerId != null && !organizerId.trim().isEmpty()) {
            intent.putExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID, organizerId);
        }
        startActivity(intent);
        finish();
    }

    private void resetForm() {
        etTitle.setText("");
        etDescription.setText("");
        etGuidelines.setText("");
        etLocation.setText("");
        etCapacity.setText("");
        if (etTags != null) {
            etTags.setText("");
        }
        interests.clear();
        if (chipGroupTags != null) {
            chipGroupTags.removeAllViews();
        }
        rgEntrants.check(R.id.rbAny);
        etCapacity.setVisibility(View.GONE);
        etCapacity.setEnabled(false);
        regStartEpochMs = 0L;
        regEndEpochMs = 0L;
        deadlineEpochMs = 0L;
        eventStartEpochMs = 0L;
        btnStart.setText("Select");
        btnEnd.setText("Select");
        btnDeadline.setText("Select Deadline");
        if (btnEventStart != null) btnEventStart.setText("Select Event Start Date");
        posterUri = null;
        btnPickPoster.setImageResource(android.R.drawable.ic_menu_camera);
        if (posterPreviewCard != null) {
            posterPreviewCard.setVisibility(View.GONE);
        }
        if (btnCropPoster != null) {
            btnCropPoster.setVisibility(View.GONE);
        }
        if (posterPreview != null) {
            posterPreview.setImageDrawable(null);
        }
        // Reset crop values
        scaleFactor = 1.0f;
        translateX = 0f;
        translateY = 0f;
    }
    
    /**
     * Loads image into preview with initial centerCrop scaling
     */
    private void loadImageIntoPreview(Uri uri) {
        if (posterPreview == null || uri == null) return;
        
        Glide.with(this)
                .asBitmap()
                .load(uri)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, 
                                                @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        if (posterPreview == null) return;
                        
                        posterPreview.setImageBitmap(resource);
                        applyInitialCrop(resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        if (posterPreview != null) {
                            posterPreview.setImageDrawable(placeholder);
                        }
                    }
                });
    }
    
    /**
     * Applies initial centerCrop transformation to the image
     */
    private void applyInitialCrop(android.graphics.Bitmap bitmap) {
        if (posterPreview == null || bitmap == null || imageMatrix == null) return;
        
        int viewWidth = posterPreview.getWidth();
        int viewHeight = posterPreview.getHeight();
        
        if (viewWidth == 0 || viewHeight == 0) {
            // View not measured yet, wait for layout
            posterPreview.post(() -> applyInitialCrop(bitmap));
            return;
        }
        
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        
        // Calculate scale to fill view (centerCrop)
        float scaleX = viewWidth / bitmapWidth;
        float scaleY = viewHeight / bitmapHeight;
        float scale = Math.max(scaleX, scaleY);
        
        // Center the image
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float dx = (viewWidth - scaledWidth) / 2f;
        float dy = (viewHeight - scaledHeight) / 2f;
        
        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        
        scaleFactor = scale;
        translateX = dx;
        translateY = dy;
        
        posterPreview.setImageMatrix(imageMatrix);
    }
    
    /**
     * Shows a dialog for cropping the image with pan and zoom controls
     */
    private void showCropDialog() {
        if (posterUri == null || posterPreview == null) {
            toast("Please select an image first");
            return;
        }
        
        // Create a dialog with a larger crop view
        android.app.Dialog cropDialog = new android.app.Dialog(this);
        cropDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        cropDialog.setContentView(R.layout.dialog_crop_poster);
        
        ImageView cropImageView = cropDialog.findViewById(R.id.cropImageView);
        Button btnZoomIn = cropDialog.findViewById(R.id.btnZoomIn);
        Button btnZoomOut = cropDialog.findViewById(R.id.btnZoomOut);
        Button btnReset = cropDialog.findViewById(R.id.btnReset);
        Button btnDone = cropDialog.findViewById(R.id.btnDone);
        Button btnCancel = cropDialog.findViewById(R.id.btnCancel);
        
        if (cropImageView == null) {
            // Create layout programmatically if not found
            createCropDialogLayout(cropDialog);
            return;
        }
        
        // Load image into crop view
        Glide.with(this)
                .asBitmap()
                .load(posterUri)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, 
                                                @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        setupCropView(cropImageView, resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                });
        
        // Setup zoom buttons
        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> zoomImage(cropImageView, 1.2f));
        }
        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> zoomImage(cropImageView, 0.8f));
        }
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> resetCrop(cropImageView));
        }
        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                applyCropToPreview(cropImageView);
                cropDialog.dismiss();
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> cropDialog.dismiss());
        }
        
        // Setup touch listeners for pan
        setupPanAndZoom(cropImageView);
        
        cropDialog.show();
    }
    
    /**
     * Creates crop dialog layout programmatically if XML doesn't exist
     */
    private void createCropDialogLayout(android.app.Dialog dialog) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        
        // Title
        TextView title = new TextView(this);
        title.setText("Adjust Poster Crop");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);
        
        // Crop image view
        ImageView cropView = new ImageView(this);
        cropView.setId(R.id.cropImageView);
        cropView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 400));
        cropView.setScaleType(ImageView.ScaleType.MATRIX);
        cropView.setBackgroundColor(Color.BLACK);
        root.addView(cropView);
        
        // Instructions
        TextView instructions = new TextView(this);
        instructions.setText("Pinch to zoom, drag to pan");
        instructions.setTextColor(Color.GRAY);
        instructions.setTextSize(14);
        instructions.setPadding(0, 16, 0, 16);
        root.addView(instructions);
        
        // Buttons
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 16, 0, 0);
        
        Button zoomIn = new Button(this);
        zoomIn.setText("Zoom In");
        zoomIn.setId(R.id.btnZoomIn);
        Button zoomOut = new Button(this);
        zoomOut.setText("Zoom Out");
        zoomOut.setId(R.id.btnZoomOut);
        Button reset = new Button(this);
        reset.setText("Reset");
        reset.setId(R.id.btnReset);
        Button done = new Button(this);
        done.setText("Done");
        done.setId(R.id.btnDone);
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setId(R.id.btnCancel);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.setMargins(4, 0, 4, 0);
        
        buttonRow.addView(zoomIn, btnParams);
        buttonRow.addView(zoomOut, btnParams);
        buttonRow.addView(reset, btnParams);
        buttonRow.addView(done, btnParams);
        buttonRow.addView(cancel, btnParams);
        
        root.addView(buttonRow);
        
        dialog.setContentView(root);
        
        // Load image and setup
        Glide.with(this)
                .asBitmap()
                .load(posterUri)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, 
                                                @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        setupCropView(cropView, resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                });
        
        zoomIn.setOnClickListener(v -> zoomImage(cropView, 1.2f));
        zoomOut.setOnClickListener(v -> zoomImage(cropView, 0.8f));
        reset.setOnClickListener(v -> resetCrop(cropView));
        done.setOnClickListener(v -> {
            applyCropToPreview(cropView);
            dialog.dismiss();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        
        setupPanAndZoom(cropView);
    }
    
    /**
     * Sets up the crop view with initial image
     */
    private void setupCropView(ImageView cropView, android.graphics.Bitmap bitmap) {
        if (cropView == null || bitmap == null) return;
        
        cropView.setImageBitmap(bitmap);
        cropView.setScaleType(ImageView.ScaleType.MATRIX);
        
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        int viewWidth = cropView.getWidth();
        int viewHeight = cropView.getHeight();
        
        if (viewWidth == 0 || viewHeight == 0) {
            cropView.post(() -> setupCropView(cropView, bitmap));
            return;
        }
        
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        
        // Initial centerCrop
        float scaleX = viewWidth / bitmapWidth;
        float scaleY = viewHeight / bitmapHeight;
        float scale = Math.max(scaleX, scaleY);
        
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float dx = (viewWidth - scaledWidth) / 2f;
        float dy = (viewHeight - scaledHeight) / 2f;
        
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        
        cropView.setImageMatrix(matrix);
        cropView.setTag(matrix); // Store matrix for later use
    }
    
    /**
     * Zooms the image in crop view
     */
    private void zoomImage(ImageView cropView, float factor) {
        android.graphics.Matrix matrix = (android.graphics.Matrix) cropView.getTag();
        if (matrix == null) {
            matrix = new android.graphics.Matrix();
            cropView.getImageMatrix().invert(matrix);
            matrix.invert(matrix);
        }
        
        matrix.postScale(factor, factor, cropView.getWidth() / 2f, cropView.getHeight() / 2f);
        cropView.setImageMatrix(matrix);
        cropView.setTag(matrix);
    }
    
    /**
     * Resets crop to initial centerCrop
     */
    private void resetCrop(ImageView cropView) {
        if (posterUri == null) return;
        
        Glide.with(this)
                .asBitmap()
                .load(posterUri)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, 
                                                @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        setupCropView(cropView, resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                });
    }
    
    /**
     * Applies the crop from dialog to the preview
     */
    private void applyCropToPreview(ImageView cropView) {
        android.graphics.Matrix matrix = (android.graphics.Matrix) cropView.getTag();
        if (matrix != null && posterPreview != null) {
            imageMatrix.set(matrix);
            posterPreview.setImageMatrix(imageMatrix);
            
            // Extract scale and translation
            float[] values = new float[9];
            matrix.getValues(values);
            scaleFactor = values[android.graphics.Matrix.MSCALE_X];
            translateX = values[android.graphics.Matrix.MTRANS_X];
            translateY = values[android.graphics.Matrix.MTRANS_Y];
        }
    }
    
    /**
     * Sets up pan and zoom gestures for crop view
     */
    private void setupPanAndZoom(ImageView cropView) {
        if (cropView == null) return;
        
        cropView.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;
            private float startDistance;
            private android.graphics.Matrix savedMatrix = new android.graphics.Matrix();
            private static final int NONE = 0;
            private static final int DRAG = 1;
            private static final int ZOOM = 2;
            private int mode = NONE;
            
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                ImageView view = (ImageView) v;
                android.graphics.Matrix matrix = (android.graphics.Matrix) view.getTag();
                if (matrix == null) {
                    matrix = new android.graphics.Matrix(view.getImageMatrix());
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
                                matrix.postScale(scale, scale, view.getWidth() / 2f, view.getHeight() / 2f);
                            }
                        }
                        break;
                }
                
                view.setImageMatrix(matrix);
                view.setTag(matrix);
                return true;
            }
            
            private float getDistance(android.view.MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return (float) Math.sqrt(x * x + y * y);
            }
        });
    }

    private static String safe(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
