package com.example.eventease.ui.organizer;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import androidx.annotation.LayoutRes;
import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.cardview.widget.CardView;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OrganizerWaitlistActivity extends AppCompatActivity {
    private static final String TAG = "OrganizerWaitlist";

    private TextView eventNameTextView;
    private ImageView eventPosterImageView;
    private EditText overviewEditText;
    private ListView waitlistListView;
    private ImageView backButton;
    private MaterialButton deleteEventButton;
    private MaterialButton entrantDetailsButton;
    private TextView tvRegStart;
    private TextView tvRegEnd;
    private TextView tvDeadline;
    private TextView tvEventStart;

    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String currentEventId;
    private ArrayList<String> entrantNamesList;
    private ArrayAdapter<String> waitlistAdapter;
    
    // For poster cropping
    private Uri selectedPosterUri;
    private android.graphics.Matrix imageMatrix;
    private boolean posterCropped = false;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPosterUri = uri;
                    showCropPosterDialog(uri);
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_waitlist);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        eventNameTextView = findViewById(R.id.event_name_title);
        overviewEditText = findViewById(R.id.overview_edittext);
        eventPosterImageView = findViewById(R.id.event_poster_placeholder);
        waitlistListView = findViewById(R.id.waitlist_listview);
        backButton = findViewById(R.id.back_button);
        entrantDetailsButton = findViewById(R.id.entrant_details_button);
        deleteEventButton = findViewById(R.id.delete_event_button);
        tvRegStart = findViewById(R.id.tvRegStart);
        tvRegEnd = findViewById(R.id.tvRegEnd);
        tvDeadline = findViewById(R.id.tvDeadline);
        tvEventStart = findViewById(R.id.tvEventStart);

        currentEventId = getIntent().getStringExtra("eventId");

        entrantNamesList = new ArrayList<>();
        waitlistAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, entrantNamesList);
        waitlistListView.setAdapter(waitlistAdapter);

        backButton.setOnClickListener(v -> finish());
        entrantDetailsButton.setOnClickListener(v -> {
            if (currentEventId == null || currentEventId.isEmpty()) {
                Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.Intent i = new android.content.Intent(this, OrganizerViewEntrantsActivity.class);
            i.putExtra("eventId", currentEventId);
            startActivity(i);
        });
        eventPosterImageView.setOnClickListener(v -> {
            if (currentEventId != null && !currentEventId.isEmpty()) {
                pickImageLauncher.launch("image/*");
            }
        });
        deleteEventButton.setOnClickListener(v -> showDeleteEventConfirmation());

        // Set up notification button for Waitlisted Entrants
        ImageView mailIcon = findViewById(R.id.mail_icon);
        if (mailIcon != null) {
            mailIcon.setOnClickListener(v -> showSendNotificationsToWaitlistedConfirmation());
        }

        // Set up location button to view entrant locations on map
        ImageView locationIcon = findViewById(R.id.location_icon);
        if (locationIcon != null) {
            locationIcon.setClickable(true);
            locationIcon.setFocusable(true);
            locationIcon.setOnClickListener(v -> {
                if (currentEventId == null || currentEventId.isEmpty()) {
                    Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
                    return;
                }
                android.content.Intent mapIntent = new android.content.Intent(this, OrganizerEntrantLocationsActivity.class);
                mapIntent.putExtra("eventId", currentEventId);
                startActivity(mapIntent);
            });
        }

        // Set up share button to show QR code
        ImageView shareButton = findViewById(R.id.share_button);
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> showEventQRDialog());
        }

        signInAndLoadData();
    }

    private void signInAndLoadData() {
        // Device auth - no sign in needed, just load data
        checkAndProcessSelection();
        loadEventDataFromFirestore(currentEventId);
    }
    
    private void checkAndProcessSelection() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            return;
        }
        
        EventSelectionHelper selectionHelper = new EventSelectionHelper();
        selectionHelper.checkAndProcessEventSelection(currentEventId, new EventSelectionHelper.SelectionCallback() {
            @Override
            public void onComplete(int selectedCount) {
                if (selectedCount > 0) {
                    Log.d(TAG, "Selection processed: " + selectedCount + " entrants selected");
                    loadEventDataFromFirestore(currentEventId);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Selection processing error: " + error);
            }
        });
    }

    private void loadEventDataFromFirestore(String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        db.collection("events").document(eventId).get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) return;

            String title = documentSnapshot.getString("title");
            String description = documentSnapshot.getString("description");
            String posterUrl = documentSnapshot.getString("posterUrl");

            if (eventNameTextView != null) eventNameTextView.setText(title);
            if (overviewEditText != null) overviewEditText.setText(description);
            if (eventPosterImageView != null) {
                Glide.with(this)
                        .load(posterUrl)
                        .placeholder(R.drawable.rounded_panel_bg)
                        .error(R.drawable.rounded_panel_bg)
                        .into(eventPosterImageView);
            }

            // Load and display dates
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault());
            
            Long registrationStart = documentSnapshot.getLong("registrationStart");
            if (tvRegStart != null) {
                if (registrationStart != null && registrationStart > 0) {
                    tvRegStart.setText("Registration Start: " + dateFormat.format(new Date(registrationStart)));
                } else {
                    tvRegStart.setText("Registration Start: TBD");
                }
            }

            Long registrationEnd = documentSnapshot.getLong("registrationEnd");
            if (tvRegEnd != null) {
                if (registrationEnd != null && registrationEnd > 0) {
                    tvRegEnd.setText("Registration End: " + dateFormat.format(new Date(registrationEnd)));
                } else {
                    tvRegEnd.setText("Registration End: TBD");
                }
            }

            Long deadlineEpochMs = documentSnapshot.getLong("deadlineEpochMs");
            if (tvDeadline != null) {
                if (deadlineEpochMs != null && deadlineEpochMs > 0) {
                    tvDeadline.setText("Accept/Decline Deadline: " + dateFormat.format(new Date(deadlineEpochMs)));
                } else {
                    tvDeadline.setText("Accept/Decline Deadline: TBD");
                }
            }

            // Check for event start date with fallback to legacy field names for backward compatibility
            Long startsAtEpochMs = documentSnapshot.getLong("startsAtEpochMs");
            if (startsAtEpochMs == null || startsAtEpochMs == 0) {
                // Fallback to eventStartEpochMs (with "event" prefix)
                startsAtEpochMs = documentSnapshot.getLong("eventStartEpochMs");
            }
            if (startsAtEpochMs == null || startsAtEpochMs == 0) {
                // Fallback to eventStart (without EpochMs suffix)
                startsAtEpochMs = documentSnapshot.getLong("eventStart");
            }
            if (tvEventStart != null) {
                if (startsAtEpochMs != null && startsAtEpochMs > 0) {
                    tvEventStart.setText("Event Start Date: " + dateFormat.format(new Date(startsAtEpochMs)));
                } else {
                    tvEventStart.setText("Event Start Date: TBD");
                }
            }

            entrantNamesList.clear();

            List<String> waitlistedIds = (List<String>) documentSnapshot.get("waitlistedEntrants");
            if (waitlistedIds != null && !waitlistedIds.isEmpty()) {
                for (String userId : waitlistedIds) {
                    fetchEntrantNameFromWaitlistSub(eventId, userId);
                }
                return;
            }

            // No array on event: read all docs in the subcollection directly
            db.collection("events").document(eventId).collection("WaitlistedEntrants").get()
                    .addOnSuccessListener(snap -> {
                        if (snap.isEmpty()) {
                            Log.d(TAG, "No waitlisted entrants found in subcollection.");
                            waitlistAdapter.notifyDataSetChanged();
                            return;
                        }
                        for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                            String name = extractNameFromMap(d.getData());
                            if (name != null) addEntrantName(name);
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "Failed to read WaitlistedEntrants subcollection", e));

            Object legacyWaitlist = documentSnapshot.get("waitlist");
            if (legacyWaitlist instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) legacyWaitlist;
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    String userId = e.getKey();
                    Object val = e.getValue();
                    if (val instanceof Map) {
                        String name = extractNameFromMap((Map<String, Object>) val);
                        if (name != null) addEntrantName(name); else fetchEntrantNameFromWaitlistSub(eventId, userId);
                    } else {
                        fetchEntrantNameFromWaitlistSub(eventId, userId);
                    }
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Event load failed", e));
    }

    private void fetchEntrantNameFromWaitlistSub(String eventId, String userId) {
        db.collection("events").document(eventId).collection("WaitlistedEntrants").document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = extractNameFromMap(doc.getData());
                    if (name != null) addEntrantName(name); else addEntrantName("Entrant (" + userId.substring(0, Math.min(6, userId.length())) + ")");
                })
                .addOnFailureListener(e -> addEntrantName("Entrant (" + userId.substring(0, Math.min(6, userId.length())) + ")"));
    }

    private String extractNameFromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return null;
        Object name = data.get("name");
        if (name instanceof String && !((String) name).trim().isEmpty()) return (String) name;
        Object full = data.get("fullName");
        if (full instanceof String && !((String) full).trim().isEmpty()) return (String) full;
        String first = data.get("firstName") instanceof String ? (String) data.get("firstName") : null;
        String last = data.get("lastName") instanceof String ? (String) data.get("lastName") : null;
        if ((first != null && !first.isEmpty()) || (last != null && !last.isEmpty())) {
            return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        }
        return null;
    }

    private void addEntrantName(String displayName) {
        entrantNamesList.add(displayName);
        waitlistAdapter.notifyDataSetChanged();
    }

    /**
     * Shows a crop dialog for the poster image with zoom and pan controls
     */
    private void showCropPosterDialog(Uri imageUri) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_crop_poster);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        ImageView posterPreview = dialog.findViewById(R.id.posterPreviewCrop);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelCrop);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSaveCrop);
        TextView tvInstructions = dialog.findViewById(R.id.tvCropInstructions);
        
        // Initialize matrix for image transformations
        imageMatrix = new android.graphics.Matrix();
        posterPreview.setScaleType(ImageView.ScaleType.MATRIX);
        
        // Load image into preview
        Glide.with(this)
                .asBitmap()
                .load(imageUri)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@androidx.annotation.NonNull android.graphics.Bitmap bitmap,
                                                @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        setupImageCropping(posterPreview, bitmap);
                    }
                    
                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                    }
                });
        
        btnCancel.setOnClickListener(v -> {
            selectedPosterUri = null;
            imageMatrix = null;
            posterCropped = false;
            dialog.dismiss();
        });
        
        btnSave.setOnClickListener(v -> {
            posterCropped = true;
            dialog.dismiss();
            uploadPosterImage(imageUri);
        });
        
        dialog.show();
    }
    
    /**
     * Sets up image cropping with touch gestures for zoom and pan
     */
    private void setupImageCropping(ImageView imageView, android.graphics.Bitmap bitmap) {
        int viewWidth = imageView.getWidth();
        int viewHeight = imageView.getHeight();
        
        if (viewWidth == 0 || viewHeight == 0) {
            // View not measured yet, post to run after layout
            imageView.post(() -> setupImageCropping(imageView, bitmap));
            return;
        }
        
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        
        // Calculate scale to fit width
        float scaleX = (float) viewWidth / bitmapWidth;
        float scaleY = (float) viewHeight / bitmapHeight;
        float scale = Math.max(scaleX, scaleY); // Fill the view
        
        // Center the image
        float dx = (viewWidth - bitmapWidth * scale) / 2f;
        float dy = (viewHeight - bitmapHeight * scale) / 2f;
        
        imageMatrix.setScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        imageView.setImageBitmap(bitmap);
        imageView.setImageMatrix(imageMatrix);
        
        // Set up touch listener for zoom and pan
        setupTouchListener(imageView, bitmap);
    }
    
    /**
     * Sets up touch gestures for image manipulation
     */
    private void setupTouchListener(ImageView imageView, android.graphics.Bitmap bitmap) {
        final android.graphics.Matrix savedMatrix = new android.graphics.Matrix();
        final android.graphics.PointF startPoint = new android.graphics.PointF();
        final android.graphics.PointF midPoint = new android.graphics.PointF();
        final float[] oldDist = new float[1]; // Use array for mutability in lambda
        oldDist[0] = 1f;
        final int[] mode = new int[1]; // Use array for mutability in lambda (0 = none, 1 = drag, 2 = zoom)
        mode[0] = 0;
        
        imageView.setOnTouchListener((v, event) -> {
            switch (event.getAction() & android.view.MotionEvent.ACTION_MASK) {
                case android.view.MotionEvent.ACTION_DOWN:
                    savedMatrix.set(imageMatrix);
                    startPoint.set(event.getX(), event.getY());
                    mode[0] = 1;
                    break;
                    
                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    oldDist[0] = spacing(event);
                    if (oldDist[0] > 10f) {
                        savedMatrix.set(imageMatrix);
                        midpoint(midPoint, event);
                        mode[0] = 2;
                    }
                    break;
                    
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_POINTER_UP:
                    mode[0] = 0;
                    break;
                    
                case android.view.MotionEvent.ACTION_MOVE:
                    if (mode[0] == 1) {
                        // Drag
                        imageMatrix.set(savedMatrix);
                        float dx = event.getX() - startPoint.x;
                        float dy = event.getY() - startPoint.y;
                        imageMatrix.postTranslate(dx, dy);
                    } else if (mode[0] == 2) {
                        // Zoom
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            imageMatrix.set(savedMatrix);
                            float scale = newDist / oldDist[0];
                            imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y);
                        }
                    }
                    break;
            }
            
            imageView.setImageMatrix(imageMatrix);
            return true;
        });
    }
    
    /**
     * Calculate distance between two touch points
     */
    private float spacing(android.view.MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
    
    /**
     * Calculate midpoint between two touch points
     */
    private void midpoint(android.graphics.PointF point, android.view.MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }
    
    /**
     * Applies the crop transformation and uploads the poster image to Firebase Storage
     */
    private void uploadPosterImage(Uri uri) {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Updating poster...", Toast.LENGTH_SHORT).show();
        
        // Get device ID for auth
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(this);
        String deviceId = authManager.getUid();
        
        // Create storage reference with event ID
        final StorageReference ref = storage.getReference("posters/" + currentEventId + "_updated.jpg");
        
        // Create metadata
        com.google.firebase.storage.StorageMetadata.Builder metaBuilder = 
            new com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg");
        
        if (deviceId != null && !deviceId.isEmpty()) {
            metaBuilder.setCustomMetadata("deviceId", deviceId);
            metaBuilder.setCustomMetadata("updatedAt", String.valueOf(System.currentTimeMillis()));
        }
        
        final com.google.firebase.storage.StorageMetadata meta = metaBuilder.build();
        
        // Apply crop if user cropped the image
        if (posterCropped && imageMatrix != null) {
            Glide.with(this)
                    .asBitmap()
                    .load(uri)
                    .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                        @Override
                        public void onResourceReady(@androidx.annotation.NonNull android.graphics.Bitmap originalBitmap,
                                                    @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                            android.graphics.Bitmap croppedBitmap = applyCropToBitmap(originalBitmap);
                            if (croppedBitmap != null) {
                                uploadBitmapToStorage(croppedBitmap, ref, meta);
                            } else {
                                // Fallback to original
                                uploadUriToStorage(uri, ref, meta);
                            }
                        }
                        
                        @Override
                        public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                        }
                    });
        } else {
            // Upload original image without cropping
            uploadUriToStorage(uri, ref, meta);
        }
    }
    
    /**
     * Applies the crop transformation to the bitmap
     */
    private android.graphics.Bitmap applyCropToBitmap(android.graphics.Bitmap originalBitmap) {
        if (originalBitmap == null || imageMatrix == null) {
            return null;
        }
        
        try {
            // Use standard event card dimensions (400x200)
            int targetWidth = 400;
            int targetHeight = 200;
            
            // Get the transformation matrix values
            float[] values = new float[9];
            imageMatrix.getValues(values);
            float scale = values[android.graphics.Matrix.MSCALE_X];
            float transX = values[android.graphics.Matrix.MTRANS_X];
            float transY = values[android.graphics.Matrix.MTRANS_Y];
            
            // Calculate the source rectangle in the original bitmap
            float srcLeft = -transX / scale;
            float srcTop = -transY / scale;
            float srcWidth = targetWidth / scale;
            float srcHeight = targetHeight / scale;
            
            // Ensure coordinates are within bitmap bounds
            srcLeft = Math.max(0, Math.min(srcLeft, originalBitmap.getWidth()));
            srcTop = Math.max(0, Math.min(srcTop, originalBitmap.getHeight()));
            srcWidth = Math.min(srcWidth, originalBitmap.getWidth() - srcLeft);
            srcHeight = Math.min(srcHeight, originalBitmap.getHeight() - srcTop);
            
            if (srcWidth <= 0 || srcHeight <= 0) {
                return originalBitmap;
            }
            
            // Create cropped bitmap
            android.graphics.Bitmap croppedBitmap = android.graphics.Bitmap.createBitmap(
                originalBitmap,
                (int)srcLeft,
                (int)srcTop,
                (int)srcWidth,
                (int)srcHeight
            );
            
            // Scale to target dimensions
            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                croppedBitmap, targetWidth, targetHeight, true);
            
            if (scaledBitmap != croppedBitmap) {
                croppedBitmap.recycle();
            }
            
            return scaledBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply crop", e);
            return null;
        }
    }
    
    /**
     * Uploads a bitmap to Firebase Storage
     */
    private void uploadBitmapToStorage(android.graphics.Bitmap bitmap, StorageReference ref, 
                                      com.google.firebase.storage.StorageMetadata meta) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] imageData = baos.toByteArray();
            
            ref.putBytes(imageData, meta)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException();
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri -> {
                        Log.d(TAG, "Poster uploaded successfully: " + downloadUri.toString());
                        updateEventPosterUrl(downloadUri.toString());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to upload poster", e);
                        Toast.makeText(this, "Failed to upload poster: " + e.getMessage(), 
                                      Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to compress bitmap", e);
            Toast.makeText(this, "Failed to process image: " + e.getMessage(), 
                          Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Uploads a URI to Firebase Storage (fallback for no crop)
     */
    private void uploadUriToStorage(Uri uri, StorageReference ref, 
                                   com.google.firebase.storage.StorageMetadata meta) {
        ref.putFile(uri, meta)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    Log.d(TAG, "Poster uploaded successfully: " + downloadUri.toString());
                    updateEventPosterUrl(downloadUri.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload poster", e);
                    Toast.makeText(this, "Failed to upload poster: " + e.getMessage(), 
                                  Toast.LENGTH_LONG).show();
                });
    }
    
    /**
     * Updates the event document with the new poster URL
     */
    private void updateEventPosterUrl(String posterUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("posterUrl", posterUrl);
        updates.put("posterUpdatedAt", System.currentTimeMillis());
        
        db.collection("events").document(currentEventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event poster URL updated successfully");
                    Toast.makeText(this, "Poster updated successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Reload the poster image
                    if (eventPosterImageView != null) {
                        Glide.with(this)
                                .load(posterUrl)
                                .placeholder(R.drawable.rounded_panel_bg)
                                .error(R.drawable.rounded_panel_bg)
                                .into(eventPosterImageView);
                    }
                    
                    // Reset crop state
                    selectedPosterUri = null;
                    imageMatrix = null;
                    posterCropped = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update event poster URL", e);
                    Toast.makeText(this, "Failed to update event: " + e.getMessage(), 
                                  Toast.LENGTH_LONG).show();
                });
    }

    private void showDeleteEventConfirmation() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String eventTitle = eventNameTextView != null ? eventNameTextView.getText().toString() : "this event";
        
        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
        
        // Create custom dialog with full screen to show blur background
        Dialog deleteDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        deleteDialog.setContentView(R.layout.dialog_delete_event);
        
        // Set window properties
        Window window = deleteDialog.getWindow();
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // Disable dim since we have our own blur background
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0f;
            window.setAttributes(layoutParams);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Apply blurred background
        View blurBackground = deleteDialog.findViewById(R.id.dialogBlurBackground);
        if (blurredBitmap != null && blurBackground != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }
        
        // Make the background clickable to dismiss
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> deleteDialog.dismiss());
        }
        
        // Get the CardView for animation
        CardView cardView = deleteDialog.findViewById(R.id.dialogCardView);
        
        // Set event title in message
        TextView tvDeleteMessage = deleteDialog.findViewById(R.id.tvDeleteMessage);
        if (tvDeleteMessage != null) {
            String message = "Are you sure you want to delete \"" + eventTitle + "\"? This action cannot be undone. All event data including waitlists, entrants, and invitations will be permanently deleted from the database.";
            tvDeleteMessage.setText(message);
        }
        
        // Setup buttons
        MaterialButton btnCancel = deleteDialog.findViewById(R.id.btnCancelDelete);
        MaterialButton btnConfirm = deleteDialog.findViewById(R.id.btnConfirmDelete);
        
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> deleteDialog.dismiss());
        }
        
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                deleteDialog.dismiss();
                deleteEvent();
            });
        }
        
        deleteDialog.show();
        
        // Apply animations after dialog is shown
        android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
        android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);
        
        if (blurBackground != null) {
            blurBackground.startAnimation(fadeIn);
        }
        if (cardView != null) {
            cardView.startAnimation(zoomIn);
        }
    }
    
    /**
     * Captures a screenshot of the current activity
     */
    private Bitmap captureScreenshot() {
        try {
            View rootView = getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screenshot", e);
            return null;
        }
    }
    
    /**
     * Blurs a bitmap using RenderScript
     */
    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(this);
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
            Log.e(TAG, "Failed to blur bitmap", e);
            return bitmap;
        }
    }

    private void deleteEvent() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        deleteEventButton.setEnabled(false);
        Toast.makeText(this, "Deleting event...", Toast.LENGTH_SHORT).show();

        DocumentReference eventRef = db.collection("events").document(currentEventId);
        
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                deleteEventButton.setEnabled(true);
                return;
            }

            String posterUrl = eventDoc.getString("posterUrl");
            boolean hasPoster = posterUrl != null && !posterUrl.isEmpty();
            
            deleteEventSubcollections(eventRef, () -> {
                deletePosterImage(hasPoster, () -> {
                    deleteInvitationsForEvent(() -> {
                        eventRef.delete()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Event document deleted successfully");
                                    Toast.makeText(this, "Event deleted successfully", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to delete event document", e);
                                    Toast.makeText(this, "Failed to delete event: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    deleteEventButton.setEnabled(true);
                                });
                    });
                });
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to load event document", e);
            Toast.makeText(this, "Failed to load event: " + e.getMessage(), Toast.LENGTH_LONG).show();
            deleteEventButton.setEnabled(true);
        });
    }
    
    private void deleteEventSubcollections(DocumentReference eventRef, Runnable onComplete) {
        String[] subcollections = {
            "WaitlistedEntrants",
            "SelectedEntrants",
            "NonSelectedEntrants",
            "CancelledEntrants",
            "AdmittedEntrants"
        };

        List<com.google.android.gms.tasks.Task<QuerySnapshot>> getTasks = new ArrayList<>();
        
        for (String subcollectionName : subcollections) {
            getTasks.add(eventRef.collection(subcollectionName).get());
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(getTasks)
                .addOnSuccessListener(tasks -> {
                    List<com.google.android.gms.tasks.Task<Void>> deleteTasks = new ArrayList<>();
                    WriteBatch batch = db.batch();
                    int batchCount = 0;
                    final int MAX_BATCH_SIZE = 500;

                    for (int i = 0; i < getTasks.size(); i++) {
                        com.google.android.gms.tasks.Task<QuerySnapshot> task = getTasks.get(i);
                        if (task.isSuccessful() && task.getResult() != null) {
                            QuerySnapshot snapshot = task.getResult();
                            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                batch.delete(doc.getReference());
                                batchCount++;

                                if (batchCount >= MAX_BATCH_SIZE) {
                                    final WriteBatch currentBatch = batch;
                                    deleteTasks.add(currentBatch.commit());
                                    batch = db.batch();
                                    batchCount = 0;
                                }
                            }
                        }
                    }

                    if (batchCount > 0) {
                        deleteTasks.add(batch.commit());
                    }

                    if (!deleteTasks.isEmpty()) {
                        com.google.android.gms.tasks.Tasks.whenAll(deleteTasks)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "All subcollections deleted successfully");
                                    onComplete.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to delete some subcollections", e);
                                    onComplete.run();
                                });
                    } else {
                        Log.d(TAG, "No subcollections to delete");
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query subcollections", e);
                    onComplete.run();
                });
    }

    private void deletePosterImage(boolean hasPoster, Runnable onComplete) {
        if (!hasPoster || currentEventId == null || currentEventId.isEmpty()) {
            onComplete.run();
            return;
        }

        StorageReference storageRef = storage.getReference("posters/" + currentEventId + ".jpg");
        storageRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Poster image deleted successfully");
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to delete poster image", e);
                    onComplete.run();
                });
    }

    private void deleteInvitationsForEvent(Runnable onComplete) {
        db.collection("invitations")
                .whereEqualTo("eventId", currentEventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        onComplete.run();
                        return;
                    }

                    List<com.google.android.gms.tasks.Task<Void>> deleteTasks = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        deleteTasks.add(doc.getReference().delete());
                    }

                    if (!deleteTasks.isEmpty()) {
                        com.google.android.gms.tasks.Tasks.whenAll(deleteTasks)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "All invitations deleted successfully");
                                    onComplete.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to delete some invitations", e);
                                    onComplete.run();
                                });
                    } else {
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to query invitations", e);
                    onComplete.run();
                });
    }

    private void showSendNotificationsToWaitlistedConfirmation() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (entrantNamesList.isEmpty()) {
            Toast.makeText(this, "No waitlisted entrants to send notifications to", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = entrantNamesList.size();
        String message = "Send notifications to " + count + " waitlisted entrant" + (count > 1 ? "s" : "") + "? They will receive push notifications even when the app is closed.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Notifications")
                .setMessage(message)
                .setPositiveButton("Send", (dialog, which) -> sendNotificationsToWaitlisted())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendNotificationsToWaitlisted() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String eventTitle = eventNameTextView != null ? eventNameTextView.getText().toString() : "Event";
        if (eventTitle == null || eventTitle.isEmpty()) {
            eventTitle = "Event";
        }

        Toast.makeText(this, "Sending notifications...", Toast.LENGTH_SHORT).show();

        NotificationHelper notificationHelper = new NotificationHelper();
        // Use default message from NotificationHelper
        notificationHelper.sendNotificationsToWaitlisted(currentEventId, eventTitle, null, new NotificationHelper.NotificationCallback() {
            @Override
            public void onComplete(int sentCount) {
                Toast.makeText(OrganizerWaitlistActivity.this,
                        "Successfully sent notifications to " + sentCount + " waitlisted entrant" + (sentCount > 1 ? "s" : ""),
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Sent notifications to " + sentCount + " waitlisted entrants");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OrganizerWaitlistActivity.this,
                        "Failed to send notifications: " + error,
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to send notifications: " + error);
            }
        });
    }
    
    /**
     * @deprecated This method is no longer used. Removed change deadlines testing functionality.
     */
    @Deprecated
    private void showChangeDeadlinesDialog() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Load current event data
        db.collection("events").document(currentEventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Long registrationEnd = eventDoc.getLong("registrationEnd");
                    Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
                    Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                    
                    // Create dialog layout
                    LinearLayout dialogLayout = new LinearLayout(this);
                    dialogLayout.setOrientation(LinearLayout.VERTICAL);
                    dialogLayout.setPadding(50, 40, 50, 10);
                    
                    // Registration End Date/Time
                    TextView regEndLabel = new TextView(this);
                    regEndLabel.setText("Registration End:");
                    regEndLabel.setTextSize(16);
                    regEndLabel.setTextColor(android.graphics.Color.BLACK);
                    dialogLayout.addView(regEndLabel);
                    
                    TextView regEndDisplay = new TextView(this);
                    regEndDisplay.setId(android.R.id.text1);
                    regEndDisplay.setTextSize(14);
                    regEndDisplay.setPadding(0, 5, 0, 15);
                    regEndDisplay.setTextColor(android.graphics.Color.DKGRAY);
                    if (registrationEnd != null && registrationEnd > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                        regEndDisplay.setText("Current: " + sdf.format(new java.util.Date(registrationEnd)));
                    } else {
                        regEndDisplay.setText("Not set");
                    }
                    dialogLayout.addView(regEndDisplay);
                    
                    // Invitation Deadline Date/Time
                    TextView deadlineLabel = new TextView(this);
                    deadlineLabel.setText("Invitation Deadline:");
                    deadlineLabel.setTextSize(16);
                    deadlineLabel.setTextColor(android.graphics.Color.BLACK);
                    dialogLayout.addView(deadlineLabel);
                    
                    TextView deadlineDisplay = new TextView(this);
                    deadlineDisplay.setId(android.R.id.text2);
                    deadlineDisplay.setTextSize(14);
                    deadlineDisplay.setPadding(0, 5, 0, 15);
                    deadlineDisplay.setTextColor(android.graphics.Color.DKGRAY);
                    if (deadlineEpochMs != null && deadlineEpochMs > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                        deadlineDisplay.setText("Current: " + sdf.format(new java.util.Date(deadlineEpochMs)));
                    } else {
                        deadlineDisplay.setText("Not set");
                    }
                    dialogLayout.addView(deadlineDisplay);
                    
                    // Event Start Date/Time
                    TextView startLabel = new TextView(this);
                    startLabel.setText("Event Start:");
                    startLabel.setTextSize(16);
                    startLabel.setTextColor(android.graphics.Color.BLACK);
                    dialogLayout.addView(startLabel);
                    
                    TextView startDisplay = new TextView(this);
                    startDisplay.setId(android.R.id.button1);
                    startDisplay.setTextSize(14);
                    startDisplay.setPadding(0, 5, 0, 15);
                    startDisplay.setTextColor(android.graphics.Color.DKGRAY);
                    if (startsAtEpochMs != null && startsAtEpochMs > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                        startDisplay.setText("Current: " + sdf.format(new java.util.Date(startsAtEpochMs)));
                    } else {
                        startDisplay.setText("Not set");
                    }
                    dialogLayout.addView(startDisplay);
                    
                    // Store current values for use in pickers
                    final long[] newRegistrationEnd = {registrationEnd != null ? registrationEnd : System.currentTimeMillis()};
                    final long[] newDeadlineEpochMs = {deadlineEpochMs != null ? deadlineEpochMs : System.currentTimeMillis()};
                    final long[] newStartsAtEpochMs = {startsAtEpochMs != null ? startsAtEpochMs : System.currentTimeMillis()};
                    
                    // Create date/time picker helper
                    Runnable updateRegEnd = () -> {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(newRegistrationEnd[0]);
                        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                            cal.set(year, month, dayOfMonth);
                            new TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                cal.set(Calendar.MINUTE, minute);
                                newRegistrationEnd[0] = cal.getTimeInMillis();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                                regEndDisplay.setText("New: " + sdf.format(cal.getTime()));
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                    };
                    
                    Runnable updateDeadline = () -> {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(newDeadlineEpochMs[0]);
                        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                            cal.set(year, month, dayOfMonth);
                            new TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                cal.set(Calendar.MINUTE, minute);
                                newDeadlineEpochMs[0] = cal.getTimeInMillis();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                                deadlineDisplay.setText("New: " + sdf.format(cal.getTime()));
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                    };
                    
                    Runnable updateStart = () -> {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(newStartsAtEpochMs[0]);
                        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                            cal.set(year, month, dayOfMonth);
                            new TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                cal.set(Calendar.MINUTE, minute);
                                newStartsAtEpochMs[0] = cal.getTimeInMillis();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                                startDisplay.setText("New: " + sdf.format(cal.getTime()));
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                    };
                    
                    // Make text views clickable
                    regEndDisplay.setOnClickListener(v -> updateRegEnd.run());
                    deadlineDisplay.setOnClickListener(v -> updateDeadline.run());
                    startDisplay.setOnClickListener(v -> updateStart.run());
                    
                    regEndDisplay.setClickable(true);
                    regEndDisplay.setFocusable(true);
                    deadlineDisplay.setClickable(true);
                    deadlineDisplay.setFocusable(true);
                    startDisplay.setClickable(true);
                    startDisplay.setFocusable(true);
                    
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Change Deadlines (Testing)")
                            .setView(dialogLayout)
                            .setMessage("Tap on each date/time to change it. This is for testing purposes only.")
                            .setPositiveButton("Save", (dialog, which) -> {
                                updateDeadlines(newRegistrationEnd[0], newDeadlineEpochMs[0], newStartsAtEpochMs[0]);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for deadline change", e);
                    Toast.makeText(this, "Failed to load event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    /**
     * Updates the event deadlines in Firestore.
     */
    private void updateDeadlines(long registrationEnd, long deadlineEpochMs, long startsAtEpochMs) {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Updating deadlines...", Toast.LENGTH_SHORT).show();
        
        // FIX: First, update the waitlistCount to match the actual count in the subcollection
        // This ensures the capacity check is accurate after deadline changes
        DocumentReference eventRef = db.collection("events").document(currentEventId);
        eventRef.collection("WaitlistedEntrants").get()
                .addOnSuccessListener(waitlistSnapshot -> {
                    int actualWaitlistCount = waitlistSnapshot != null ? waitlistSnapshot.size() : 0;
                    Log.d(TAG, "Actual waitlist count: " + actualWaitlistCount);
                    
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("registrationEnd", registrationEnd);
                    updates.put("deadlineEpochMs", deadlineEpochMs);
                    updates.put("startsAtEpochMs", startsAtEpochMs);
                    updates.put("waitlistCount", actualWaitlistCount); // FIX: Update waitlistCount to actual count
                    
                    // Also reset flags so automatic processes can run again
                    updates.put("selectionProcessed", false);
                    updates.put("selectionNotificationSent", false);
                    updates.put("deadlineNotificationSent", false);
                    updates.put("sorryNotificationSent", false);
                    
                    eventRef.update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Deadlines updated successfully. WaitlistCount set to: " + actualWaitlistCount);
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                                String message = "Deadlines updated:\n" +
                                        "Registration End: " + sdf.format(new java.util.Date(registrationEnd)) + "\n" +
                                        "Invitation Deadline: " + sdf.format(new java.util.Date(deadlineEpochMs)) + "\n" +
                                        "Event Start: " + sdf.format(new java.util.Date(startsAtEpochMs)) + "\n" +
                                        "Waitlist Count: " + actualWaitlistCount;
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                
                                // Reload event data to reflect changes
                                loadEventDataFromFirestore(currentEventId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to update deadlines", e);
                                Toast.makeText(this, "Failed to update deadlines: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to count waitlist entries, proceeding with update anyway", e);
                    // On error, still update deadlines but set waitlistCount to 0 as fallback
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("registrationEnd", registrationEnd);
                    updates.put("deadlineEpochMs", deadlineEpochMs);
                    updates.put("startsAtEpochMs", startsAtEpochMs);
                    updates.put("waitlistCount", 0); // Fallback to 0 if we can't count
                    
                    // Also reset flags so automatic processes can run again
                    updates.put("selectionProcessed", false);
                    updates.put("selectionNotificationSent", false);
                    updates.put("deadlineNotificationSent", false);
                    updates.put("sorryNotificationSent", false);
                    
                    eventRef.update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Deadlines updated (with fallback waitlistCount=0)");
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
                                String message = "Deadlines updated:\n" +
                                        "Registration End: " + sdf.format(new java.util.Date(registrationEnd)) + "\n" +
                                        "Invitation Deadline: " + sdf.format(new java.util.Date(deadlineEpochMs)) + "\n" +
                                        "Event Start: " + sdf.format(new java.util.Date(startsAtEpochMs));
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                
                                // Reload event data to reflect changes
                                loadEventDataFromFirestore(currentEventId);
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "Failed to update deadlines", e2);
                                Toast.makeText(this, "Failed to update deadlines: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });
    }

    /**
     * Shows the Event QR code dialog
     */
    private void showEventQRDialog() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch event data to get qrPayload
        db.collection("events").document(currentEventId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String eventTitle = documentSnapshot.getString("title");
                    String storedQrPayload = documentSnapshot.getString("qrPayload");

                    // Use stored qrPayload if available, otherwise generate it
                    final String qrPayload;
                    if (storedQrPayload != null && !storedQrPayload.isEmpty()) {
                        qrPayload = storedQrPayload;
                    } else {
                        // Generate QR payload if not stored (use custom scheme format)
                        qrPayload = "eventease://event/" + currentEventId;
                    }

                    final String eventTitleText = eventTitle != null ? eventTitle : "Event";

                    // Show QR dialog
                    showQrDialog(eventTitleText, qrPayload);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch event data", e);
                    Toast.makeText(this, "Failed to load event data", Toast.LENGTH_SHORT).show();
                });
    }

    private void showQrDialog(String title, String qrPayload) {
        android.app.Dialog dialog = createCardDialog(R.layout.dialog_qr_preview);
        TextView titleView = dialog.findViewById(R.id.tvEventTitle);
        ImageView imgQr = dialog.findViewById(R.id.imgQr);
        MaterialButton btnShare = dialog.findViewById(R.id.btnShare);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSave);
        MaterialButton btnCopyLink = dialog.findViewById(R.id.btnCopyLink);
        MaterialButton btnViewEvents = dialog.findViewById(R.id.btnViewEvents);

        if (titleView != null) {
            titleView.setText(title);
        }

        final android.graphics.Bitmap qrBitmap = generateQrBitmap(qrPayload);
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
                        Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "QR not ready yet.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCopyLink != null) {
            btnCopyLink.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Event Link", qrPayload);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(OrganizerWaitlistActivity.this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnViewEvents != null) {
            btnViewEvents.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Creates a card-style dialog
     */
    private android.app.Dialog createCardDialog(int layoutRes) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(layoutRes);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private android.graphics.Bitmap generateQrBitmap(String payload) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix matrix = writer.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }
            return bmp;
        } catch (com.google.zxing.WriterException e) {
            Log.e(TAG, "QR code generation failed", e);
            return null;
        }
    }

    private void shareQrBitmap(android.graphics.Bitmap bitmap, String payload) {
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "qr");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new java.io.IOException("Unable to create cache directory");
            }
            java.io.File file = new java.io.File(cacheDir, "qr_" + System.currentTimeMillis() + ".png");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }

            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Scan this QR to view the event: " + payload);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share QR code"));
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to share QR bitmap", e);
            Toast.makeText(this, "Unable to share QR. Try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareQrText(String payload) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, payload);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share event link"));
    }

    private boolean saveQrToGallery(android.graphics.Bitmap bitmap, String title) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Saving to gallery requires Android 10 or newer. Use Share instead.", Toast.LENGTH_SHORT).show();
            return false;
        }

        String fileName = "EventEase_" + System.currentTimeMillis() + ".png";
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/EventEase");

            Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new java.io.IOException("Unable to create MediaStore entry");
            }
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Unable to open output stream");
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            return true;
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to save QR to gallery", e);
            Toast.makeText(this, "Unable to save QR. Try again.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

}


