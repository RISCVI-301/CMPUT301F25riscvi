package com.example.eventease.ui.organizer;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Locale;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
    private MaterialButton changeDeadlinesButton;
    private Button btnEventQR;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String currentEventId;
    private ArrayList<String> entrantNamesList;
    private ArrayAdapter<String> waitlistAdapter;
    
    // For poster crop functionality
    private Uri posterUri;
    private android.graphics.Matrix imageMatrix;
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    posterUri = uri;
                    // Open crop dialog after image selection
                    showCropDialog();
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_waitlist);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        
        // Initialize image matrix for crop functionality
        imageMatrix = new android.graphics.Matrix();

        eventNameTextView = findViewById(R.id.event_name_title);
        overviewEditText = findViewById(R.id.overview_edittext);
        eventPosterImageView = findViewById(R.id.event_poster_placeholder);
        waitlistListView = findViewById(R.id.waitlist_listview);
        backButton = findViewById(R.id.back_button);
        entrantDetailsButton = findViewById(R.id.entrant_details_button);
        deleteEventButton = findViewById(R.id.delete_event_button);
        changeDeadlinesButton = findViewById(R.id.change_deadlines_button);
        btnEventQR = findViewById(R.id.btnEventQR);

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
        
        // Set up change deadlines button (for testing)
        if (changeDeadlinesButton != null) {
            changeDeadlinesButton.setOnClickListener(v -> showChangeDeadlinesDialog());
        }
        
        // Set up Event QR button
        if (btnEventQR != null) {
            btnEventQR.setOnClickListener(v -> showEventQRDialog());
        }

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

        signInAndLoadData();
    }

    private void signInAndLoadData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkAndProcessSelection();
            loadEventDataFromFirestore(currentEventId);
        } else {
            mAuth.signInAnonymously()
                    .addOnSuccessListener(r -> {
                        checkAndProcessSelection();
                        loadEventDataFromFirestore(currentEventId);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Auth failed", Toast.LENGTH_SHORT).show());
        }
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
     * Shows a dialog for cropping the image with pan and zoom controls
     */
    private void showCropDialog() {
        if (posterUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create a dialog with a larger crop view
        android.app.Dialog cropDialog = new android.app.Dialog(this);
        cropDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        cropDialog.setContentView(R.layout.dialog_crop_poster);
        
        ImageView cropImageView = cropDialog.findViewById(R.id.cropImageView);
        android.widget.Button btnZoomIn = cropDialog.findViewById(R.id.btnZoomIn);
        android.widget.Button btnZoomOut = cropDialog.findViewById(R.id.btnZoomOut);
        android.widget.Button btnReset = cropDialog.findViewById(R.id.btnReset);
        android.widget.Button btnDone = cropDialog.findViewById(R.id.btnDone);
        android.widget.Button btnCancel = cropDialog.findViewById(R.id.btnCancel);
        
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
                applyCropFromDialog(cropImageView);
                cropDialog.dismiss();
                // Upload after crop is applied
                uploadPosterImage();
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                cropDialog.dismiss();
                // Reset if cancelled
                imageMatrix.reset();
            });
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
        root.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));
        
        // Title
        TextView title = new TextView(this);
        title.setText("Adjust Poster Crop");
        title.setTextColor(android.graphics.Color.WHITE);
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
        cropView.setBackgroundColor(android.graphics.Color.BLACK);
        root.addView(cropView);
        
        // Instructions
        TextView instructions = new TextView(this);
        instructions.setText("Pinch to zoom, drag to pan");
        instructions.setTextColor(android.graphics.Color.GRAY);
        instructions.setTextSize(14);
        instructions.setPadding(0, 16, 0, 16);
        root.addView(instructions);
        
        // Buttons
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 16, 0, 0);
        
        android.widget.Button zoomIn = new android.widget.Button(this);
        zoomIn.setText("Zoom In");
        zoomIn.setId(R.id.btnZoomIn);
        android.widget.Button zoomOut = new android.widget.Button(this);
        zoomOut.setText("Zoom Out");
        zoomOut.setId(R.id.btnZoomOut);
        android.widget.Button reset = new android.widget.Button(this);
        reset.setText("Reset");
        reset.setId(R.id.btnReset);
        android.widget.Button done = new android.widget.Button(this);
        done.setText("Done");
        done.setId(R.id.btnDone);
        android.widget.Button cancel = new android.widget.Button(this);
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
            applyCropFromDialog(cropView);
            dialog.dismiss();
            uploadPosterImage();
        });
        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            imageMatrix.reset();
        });
        
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
     * Applies the crop from dialog to the image matrix
     */
    private void applyCropFromDialog(ImageView cropView) {
        android.graphics.Matrix matrix = (android.graphics.Matrix) cropView.getTag();
        if (matrix != null) {
            imageMatrix.set(matrix);
            
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
        
        cropView.setOnTouchListener(new android.view.View.OnTouchListener() {
            private float lastX, lastY;
            private float startDistance;
            private android.graphics.Matrix savedMatrix = new android.graphics.Matrix();
            private static final int NONE = 0;
            private static final int DRAG = 1;
            private static final int ZOOM = 2;
            private int mode = NONE;
            
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
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

    private void uploadPosterImage() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (posterUri == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading message
        Toast.makeText(this, "Uploading poster...", Toast.LENGTH_SHORT).show();

        // Create storage reference using the event ID
        StorageReference storageRef = storage.getReference("posters/" + currentEventId + ".jpg");

        // Set metadata
        StorageMetadata metadata = new StorageMetadata.Builder()
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
                                uploadCroppedBitmap(croppedBitmap, storageRef, metadata);
                            } else {
                                // Fallback to original upload
                                storageRef.putFile(posterUri, metadata)
                                        .continueWithTask(task -> {
                                            if (!task.isSuccessful()) throw task.getException();
                                            return storageRef.getDownloadUrl();
                                        })
                                        .addOnSuccessListener(downloadUri -> updateEventPosterUrl(downloadUri.toString()))
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Poster upload failed", e);
                                            Toast.makeText(OrganizerWaitlistActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        });
                            }
                        }
                        
                        @Override
                        public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                    });
        } else {
            // No crop applied, upload original
            storageRef.putFile(posterUri, metadata)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException();
                        return storageRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri -> updateEventPosterUrl(downloadUri.toString()))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Poster upload failed", e);
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }
    
    /**
     * Applies the crop transformation to the bitmap based on the current matrix.
     * Uses a standard crop view size (400dp height, full width) for calculations.
     */
    private android.graphics.Bitmap applyCropToBitmap(android.graphics.Bitmap originalBitmap) {
        if (originalBitmap == null || imageMatrix == null) {
            return null;
        }
        
        // Use standard crop dialog dimensions (matches dialog_crop_poster layout)
        // Default to 400dp height, but we'll calculate based on actual bitmap
        float bitmapWidth = originalBitmap.getWidth();
        float bitmapHeight = originalBitmap.getHeight();
        
        // Get matrix values
        float[] values = new float[9];
        imageMatrix.getValues(values);
        
        float scale = values[android.graphics.Matrix.MSCALE_X];
        float transX = values[android.graphics.Matrix.MTRANS_X];
        float transY = values[android.graphics.Matrix.MTRANS_Y];
        
        // Calculate the initial scale that was applied in setupCropView
        // We need to reverse-engineer the crop view dimensions from the matrix
        // The initial scale centers the image, so we can calculate view dimensions
        float initialScaleX = bitmapWidth > 0 ? 1.0f : 1.0f;
        float initialScaleY = bitmapHeight > 0 ? 1.0f : 1.0f;
        
        // Estimate crop view size (typically 400dp height, but we'll use a reasonable default)
        // We'll use the scale to determine the view size
        // If scale > 1, image is zoomed in; if scale < initial, it's zoomed out
        float estimatedViewWidth = 800; // Approximate width in pixels
        float estimatedViewHeight = 400; // Approximate height in pixels
        
        // Calculate initial centerCrop scale
        float initialScaleX_view = estimatedViewWidth / bitmapWidth;
        float initialScaleY_view = estimatedViewHeight / bitmapHeight;
        float initialScale = Math.max(initialScaleX_view, initialScaleY_view);
        
        // Current scale relative to initial
        float relativeScale = scale / initialScale;
        
        // Calculate translation in bitmap coordinates
        float cropX = -transX / initialScale;
        float cropY = -transY / initialScale;
        float cropWidth = estimatedViewWidth / initialScale;
        float cropHeight = estimatedViewHeight / initialScale;
        
        // Adjust for zoom
        float zoomFactor = relativeScale;
        float centerX = estimatedViewWidth / 2f;
        float centerY = estimatedViewHeight / 2f;
        
        // Calculate the visible region in bitmap coordinates
        float visibleWidth = cropWidth / zoomFactor;
        float visibleHeight = cropHeight / zoomFactor;
        float visibleX = (centerX - transX) / initialScale - visibleWidth / 2f;
        float visibleY = (centerY - transY) / initialScale - visibleHeight / 2f;
        
        // Ensure crop region is within bounds
        visibleX = Math.max(0, Math.min(visibleX, bitmapWidth - visibleWidth));
        visibleY = Math.max(0, Math.min(visibleY, bitmapHeight - visibleHeight));
        visibleWidth = Math.min(visibleWidth, bitmapWidth - visibleX);
        visibleHeight = Math.min(visibleHeight, bitmapHeight - visibleY);
        
        // Create cropped bitmap
        if (visibleWidth > 0 && visibleHeight > 0) {
            android.graphics.Bitmap cropped = android.graphics.Bitmap.createBitmap(
                originalBitmap,
                (int) visibleX,
                (int) visibleY,
                (int) visibleWidth,
                (int) visibleHeight
            );
            return cropped;
        }
        
        // Fallback: return original if calculation fails
        return originalBitmap;
    }
    
    /**
     * Uploads a cropped bitmap to Firebase Storage
     */
    private void uploadCroppedBitmap(android.graphics.Bitmap bitmap, StorageReference ref, StorageMetadata meta) {
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
                    .addOnSuccessListener(downloadUri -> updateEventPosterUrl(downloadUri.toString()))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Poster upload failed", e);
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload cropped bitmap", e);
            Toast.makeText(this, "Failed to process image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Updates the event document and UI with the new poster URL
     */
    private void updateEventPosterUrl(String newPosterUrl) {
        Log.d(TAG, "Poster uploaded successfully. URL: " + newPosterUrl);

        // Update the event document in Firestore with the new poster URL
        DocumentReference eventRef = db.collection("events").document(currentEventId);
        eventRef.update("posterUrl", newPosterUrl)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event poster URL updated in Firestore");
                    
                    // Update the ImageView to show the new poster
                    if (eventPosterImageView != null) {
                        Glide.with(this)
                                .load(newPosterUrl)
                                .placeholder(R.drawable.rounded_panel_bg)
                                .error(R.drawable.rounded_panel_bg)
                                .into(eventPosterImageView);
                    }
                    
                    Toast.makeText(this, "Poster updated successfully", Toast.LENGTH_SHORT).show();
                    
                    // Reset crop state
                    imageMatrix.reset();
                    scaleFactor = 1.0f;
                    translateX = 0f;
                    translateY = 0f;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update poster URL in Firestore", e);
                    Toast.makeText(this, "Poster uploaded but failed to update event: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
    }

    private void showDeleteEventConfirmation() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String eventTitle = eventNameTextView != null ? eventNameTextView.getText().toString() : "this event";
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Event")
                .setMessage("Are you sure you want to delete \"" + eventTitle + "\"? This action cannot be undone. All event data including waitlists, entrants, and invitations will be permanently deleted from the database.")
                .setPositiveButton("Delete", (dialog, which) -> deleteEvent())
                .setNegativeButton("Cancel", null)
                .show();
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
     * Shows a dialog to change event deadlines for testing purposes.
     */
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
                        // Generate QR payload if not stored (use HTTP URL format for better QR scanner compatibility)
                        qrPayload = "https://eventease.app/event/" + currentEventId;
                    }

                    final String eventTitleText = eventTitle != null ? eventTitle : "Event";

                    // Create dialog
                    Dialog dialog = createCardDialog(R.layout.dialog_qr_preview);
                    TextView titleView = dialog.findViewById(R.id.tvEventTitle);
                    ImageView imgQr = dialog.findViewById(R.id.imgQr);
                    MaterialButton btnShare = dialog.findViewById(R.id.btnShare);
                    MaterialButton btnSave = dialog.findViewById(R.id.btnSave);
                    MaterialButton btnViewEvents = dialog.findViewById(R.id.btnViewEvents);

                    if (titleView != null) {
                        titleView.setText(eventTitleText);
                    }

                    final Bitmap qrBitmap = generateQrBitmap(qrPayload);
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
                                boolean saved = saveQrToGallery(qrBitmap, eventTitleText);
                                if (saved) {
                                    Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(this, "QR not ready yet.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    if (btnViewEvents != null) {
                        btnViewEvents.setOnClickListener(v -> dialog.dismiss());
                    }

                    dialog.show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch event data", e);
                    Toast.makeText(this, "Failed to load event data", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Creates a card-style dialog
     */
    private Dialog createCardDialog(int layoutRes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(layoutRes);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Generates a QR code bitmap from the given payload
     */
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

    /**
     * Shares the QR code bitmap
     */
    private void shareQrBitmap(Bitmap bitmap, String payload) {
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "qr");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new java.io.IOException("Unable to create cache directory");
            }
            java.io.File file = new java.io.File(cacheDir, "qr_" + System.currentTimeMillis() + ".png");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", file);
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

    /**
     * Shares the QR code as text
     */
    private void shareQrText(String payload) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, payload);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share event link"));
    }

    /**
     * Saves QR code to gallery
     */
    private boolean saveQrToGallery(Bitmap bitmap, String title) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "EventEase_QR_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/EventEase");
                android.net.Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            return true;
                        }
                    }
                }
            } else {
                java.io.File picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES);
                java.io.File eventEaseDir = new java.io.File(picturesDir, "EventEase");
                if (!eventEaseDir.exists()) {
                    eventEaseDir.mkdirs();
                }
                java.io.File file = new java.io.File(eventEaseDir, "EventEase_QR_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    // Notify media scanner
                    android.content.Intent mediaScanIntent = new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(android.net.Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save QR code", e);
        }
        return false;
    }
}


