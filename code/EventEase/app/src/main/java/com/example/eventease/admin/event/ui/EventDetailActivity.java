package com.example.eventease.admin.event.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.eventease.R;
import com.example.eventease.admin.event.data.Event;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.function.Consumer;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "com.example.eventease.extra.EVENT";
    public static final String EXTRA_WAITLIST_COUNT = "com.example.eventease.extra.WAITLIST_COUNT";

    private static Consumer<Event> onDeleteCallback; // receives the delete function

    /** Helper to start this screen and pass a delete callback. */
    public static void start(Context context, Event event, Consumer<Event> deleteCallback) {
        onDeleteCallback = deleteCallback;
        Intent i = new Intent(context, EventDetailActivity.class);
        i.putExtra(EXTRA_EVENT, event);
        i.putExtra(EXTRA_WAITLIST_COUNT, event.getWaitlist_count()); // keeping existing behavior
        context.startActivity(i);
    }

    /** Existing helper preserved (no callback). */
    public static void start(Context context, Event event, int waitlistCount) {
        Intent i = new Intent(context, EventDetailActivity.class);
        i.putExtra(EXTRA_EVENT, event);
        i.putExtra(EXTRA_WAITLIST_COUNT, waitlistCount);
        context.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        ImageButton btnBack      = findViewById(R.id.btnBack);
        TextView tvEventTitle    = findViewById(R.id.tvEventTitle);
        ImageView ivPoster       = findViewById(R.id.ivPoster);
        TextView tvDescription   = findViewById(R.id.tvDescription);
        TextView tvWaitlistCount = findViewById(R.id.tvWaitlistCount);
        TextView tvGuidelines    = findViewById(R.id.tvGuidelines);
        MaterialButton btnEventQR = findViewById(R.id.btnEventQR);
        MaterialButton btnDelete = findViewById(R.id.btnDeleteEvent);

        // Check if views are found
        if (btnBack == null || tvEventTitle == null || ivPoster == null || 
            tvDescription == null || tvWaitlistCount == null || tvGuidelines == null || 
            btnDelete == null) {
            Toast.makeText(this, "Error loading event details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Event event = (Event) getIntent().getSerializableExtra(EXTRA_EVENT);
        int waitlistCount = getIntent().getIntExtra(EXTRA_WAITLIST_COUNT, 0);

        if (event == null) {
            Toast.makeText(this, "No event supplied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Setup Event QR button
        if (btnEventQR != null) {
            btnEventQR.setOnClickListener(v -> showEventQRDialog(event));
        }

        // Set event title
        if (event.getTitle() != null) {
            tvEventTitle.setText(event.getTitle());
        }

        // Load poster image
        String posterUrl = event.getPosterUrl();
        if (!TextUtils.isEmpty(posterUrl)) {
            Glide.with(this)
                    .load(posterUrl)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivPoster);
        }

        // Set description
        tvDescription.setText(
                TextUtils.isEmpty(event.getDescription())
                        ? "No description provided."
                        : event.getDescription()
        );

        // Set waitlist count
        tvWaitlistCount.setText(String.valueOf(waitlistCount));

        // Set guidelines
        String guidelines = event.getGuidelines();
        tvGuidelines.setText(TextUtils.isEmpty(guidelines)
                ? "No guidelines provided."
                : guidelines);

        // Setup back button
        btnBack.setOnClickListener(v -> {
            try {
                onBackPressed();
            } catch (Exception e) {
                finish();
            }
        });

        // Setup delete button
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete event?")
                    .setMessage("This action cannot be undone.")
                    .setPositiveButton("Delete", (d, which) -> {
                        if (onDeleteCallback != null) {
                            onDeleteCallback.accept(event);
                        }
                        Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    /**
     * Shows the Event QR code dialog
     */
    private void showEventQRDialog(Event event) {
        String eventId = event.getId();
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use stored qrPayload if available, otherwise generate it
        final String qrPayload;
        String storedQrPayload = event.getQrPayload();
        if (storedQrPayload != null && !storedQrPayload.isEmpty()) {
            qrPayload = storedQrPayload;
        } else {
            // Generate QR payload - use HTTP URL format for better QR scanner compatibility
            qrPayload = "https://eventease.app/event/" + eventId;
        }

        final String eventTitleText = event.getTitle() != null ? event.getTitle() : "Event";

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
            android.util.Log.e("AdminEventDetail", "QR code generation failed", e);
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
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Scan this QR to view the event: " + payload);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share QR code"));
        } catch (java.io.IOException e) {
            android.util.Log.e("AdminEventDetail", "Failed to share QR bitmap", e);
            Toast.makeText(this, "Unable to share QR. Try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shares the QR code as text
     */
    private void shareQrText(String payload) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(shareIntent, "Share event link"));
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
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(android.net.Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                    return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AdminEventDetail", "Failed to save QR code", e);
        }
        return false;
    }
}