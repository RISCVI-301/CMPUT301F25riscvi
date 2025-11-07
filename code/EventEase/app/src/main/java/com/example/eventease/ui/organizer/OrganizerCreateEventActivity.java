package com.example.eventease.ui.organizer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.example.eventease.util.AuthHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Activity for an organizer to create a new event.
 * <p>
 * This class provides a form for organizers to input event details such as title,
 * description, registration times, capacity, and an event poster. It performs
 * validation on the user's input before saving the new event to Firestore.
 * The process involves uploading the poster image to Firebase Storage and then
 * creating a new document in the 'events' collection in Firestore.
 */
public class OrganizerCreateEventActivity extends AppCompatActivity {
    private static final String TAG = "CreateEvent";
    // --- UI Elements ---
    private ImageButton btnBack, btnPickPoster;
    private EditText etTitle, etDescription, etCapacity;
    private Button btnStart, btnEnd, btnSave;
    private Switch swGeo, swQr;
    private RadioGroup rgEntrants;
    private RadioButton rbAny, rbSpecific;

    // --- Data Holders ---
    private long regStartEpochMs = 0L, regEndEpochMs = 0L;
    private Uri posterUri = null;

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
        etCapacity = findViewById(R.id.etCapacity);
        btnStart = findViewById(R.id.btnStart);
        btnEnd = findViewById(R.id.btnEnd);
        btnSave = findViewById(R.id.btnSave);
        swGeo = findViewById(R.id.swGeo);
        swQr = findViewById(R.id.swQr);
        rgEntrants = findViewById(R.id.rgEntrants);
        rbAny = findViewById(R.id.rbAny);
        rbSpecific = findViewById(R.id.rbSpecific);

        rgEntrants.setOnCheckedChangeListener((g, checkedId) -> {
            boolean specific = (checkedId == R.id.rbSpecific);
            etCapacity.setEnabled(specific);
            if (!specific) etCapacity.setText("");
        });

        btnStart.setOnClickListener(v -> pickDateTime(true));
        btnEnd.setOnClickListener(v -> pickDateTime(false));
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
        DatePickerDialog dp = new DatePickerDialog(
                this, (view, y, m, d) -> {
            TimePickerDialog tp = new TimePickerDialog(
                    this, (vv, hh, mm) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, d, hh, mm, 0);
                chosen.set(Calendar.MILLISECOND, 0);
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
        if (regEndEpochMs < regStartEpochMs) { toast("End must be after Start"); return; }

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
        String organizerId = AuthHelper.getCurrentOrganizerIdOrNull();
        if (organizerId == null) {
            toast("Not signed in. Please sign in again.");
            btnSave.setEnabled(true);
            btnSave.setText("SAVE CHANGES");
            return;
        }
        String description = safe(etDescription.getText());
        boolean useGeo = swGeo.isChecked();
        boolean generateQr = swQr.isChecked();

        Map<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("title", title);
        doc.put("description", TextUtils.isEmpty(description) ? null : description);
        doc.put("registrationStart", regStartEpochMs);
        doc.put("registrationEnd", regEndEpochMs);
        doc.put("capacity", chosenCapacity);
        doc.put("geolocation", useGeo);
        doc.put("qrEnabled", generateQr);
        doc.put("posterUrl", posterUrl);
        doc.put("organizerId", organizerId);
        doc.put("createdAt", System.currentTimeMillis());
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
        StringBuilder msg = new StringBuilder("Your event was created.\n\n");
        msg.append("Title: ").append(title).append("\n");
        msg.append("ID: ").append(id);
        if (qrPayload != null) {
            msg.append("\nQR payload: ").append(qrPayload);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Event created")
                .setMessage(msg.toString())
                .setPositiveButton("View My Events", (d, w) -> {
                    startActivity(new Intent(
                            this, com.example.eventease.ui.organizer.OrganizerMyEventActivity.class));
                    finish();
                })
                .setNegativeButton("Create Another", (d, w) -> {
                    resetForm();
                })
                .show();
    }

    private void resetForm() {
        etTitle.setText("");
        etDescription.setText("");
        etCapacity.setText("");
        rbAny.setChecked(true);
        regStartEpochMs = 0L;
        regEndEpochMs = 0L;
        btnStart.setText("Select");
        btnEnd.setText("Select");
        posterUri = null;
        btnPickPoster.setImageResource(android.R.drawable.ic_menu_camera);
    }

    private static String safe(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}