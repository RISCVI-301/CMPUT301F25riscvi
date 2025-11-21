package com.example.eventease.ui.organizer;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    // --- UI Elements ---
    private ImageButton btnBack, btnPickPoster;
    private EditText etTitle, etDescription, etGuidelines, etLocation, etCapacity;
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

    /**
     * Handles the result of the image picker intent.
     * When an image is selected from the gallery, its URI is stored and displayed.
     */
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    posterUri = uri;
                    Glide.with(this).load(uri).into(btnPickPoster);
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
        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etGuidelines = findViewById(R.id.etGuidelines);
        etLocation = findViewById(R.id.etLocation);
        etCapacity = findViewById(R.id.etCapacity);
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

        organizerId = getIntent().getStringExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID);
        if (organizerId == null || organizerId.trim().isEmpty()) {
            resolveOrganizerId(null);
        }

        rgEntrants.setOnCheckedChangeListener((g, checkedId) -> {
            boolean specific = (checkedId == R.id.rbSpecific);
            etCapacity.setEnabled(specific);
            etCapacity.setVisibility(specific ? View.VISIBLE : View.GONE);
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
        btnSave.setOnClickListener(v -> beginSaveEvent());
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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            btnSave.setEnabled(false);
            btnSave.setText("Signing in…");
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(r -> doValidateAndSave())
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("SAVE CHANGES");
                        toast("Sign-in failed: " + e.getMessage());
                        Log.e(TAG, "Anon sign-in failed", e);
                    });
        } else {
            doValidateAndSave();
        }
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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            toast("Please sign in again.");
            return;
        }
        isResolvingOrganizerId = true;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    organizerId = doc != null ? doc.getString("organizerId") : null;
                    if (organizerId == null || organizerId.trim().isEmpty()) {
                        organizerId = doc != null ? doc.getId() : user.getUid();
                    }
                    isResolvingOrganizerId = false;
                    if (organizerId == null || organizerId.trim().isEmpty()) {
                        toast("Organizer profile not configured yet.");
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
            if (capStr.isEmpty()) { etCapacity.setError("Enter capacity"); etCapacity.requestFocus(); return; }
            try {
                int v = Integer.parseInt(capStr);
                if (v < 1 || v > 500) { etCapacity.setError("1–500 only"); etCapacity.requestFocus(); return; }
                chosenCapacity = v;
            } catch (NumberFormatException e) {
                etCapacity.setError("Enter a number"); etCapacity.requestFocus(); return;
            }
        }

        if (organizerId == null || organizerId.trim().isEmpty()) {
            toast("Fetching organizer profile…");
            resolveOrganizerId(this::doValidateAndSave);
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Saving…");
        doUploadAndSave(title, chosenCapacity);
    }
    /**
     * Uploads the selected event poster to Firebase Storage and then calls
     * {@link #writeEventDoc} to save the event details to Firestore.
     *
     * @param title          The validated title of the event.
     * @param chosenCapacity The validated capacity of the event (-1 for unlimited).
     */
    private void doUploadAndSave(String title, int chosenCapacity) {
        final String id = UUID.randomUUID().toString();
        final StorageReference ref = FirebaseStorage.getInstance()
                .getReference("posters/" + id + ".jpg");

        StorageMetadata meta = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        ref.putFile(posterUri, meta)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(download -> writeEventDoc(id, title, chosenCapacity, download.toString()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Poster upload failed", e);
                    toast("Upload failed: " + e.getMessage());
                    btnSave.setEnabled(true);
                    btnSave.setText("SAVE CHANGES");
                });
    }
    /**
     * Writes the complete event document to the 'events' collection in Firestore.
     *
     * @param id             The unique ID generated for this event.
     * @param title          The title of the event.
     * @param chosenCapacity The maximum number of attendees (-1 for no limit).
     * @param posterUrl      The public URL of the uploaded poster in Firebase Storage.
     */
    private void writeEventDoc(String id, String title, int chosenCapacity, String posterUrl) {
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
        doc.put("registrationStart", regStartEpochMs);
        doc.put("registrationEnd", regEndEpochMs);
        doc.put("deadlineEpochMs", deadlineEpochMs);
        doc.put("eventStart", eventStartEpochMs);
        doc.put("eventStartEpochMs", eventStartEpochMs); // Also save with consistent naming
        doc.put("capacity", chosenCapacity);
        doc.put("geolocation", useGeo);
        doc.put("qrEnabled", generateQr);
        doc.put("posterUrl", posterUrl);
        doc.put("organizerId", organizerId);
        doc.put("createdAt", System.currentTimeMillis());
        doc.put("createdAtEpochMs", System.currentTimeMillis());
        doc.put("qrPayload", generateQr ? ("event:" + id) : null);
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(id)
                .set(doc)
                .addOnSuccessListener(v -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("SAVE CHANGES");
                    showSuccessDialog(id, title, generateQr ? ("event:" + id) : null);
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

        if (btnViewEvents != null) {
            btnViewEvents.setOnClickListener(v -> {
                dialog.dismiss();
                goToMyEvents();
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
    }

    private static String safe(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}