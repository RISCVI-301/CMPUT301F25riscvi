/**
 * Activity for organizers to view and manage entrants for an event.
 *
 * <p>This activity displays entrants in three categories:
 * <ul>
 *   <li>Selected Entrants - Users who have been selected by the organizer</li>
 *   <li>Not Selected Entrants - Users who were not selected</li>
 *   <li>Cancelled Entrants - Users who have cancelled their participation</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>View all entrants organized by selection status</li>
 *   <li>Send invitations to selected entrants</li>
 *   <li>View final entrant list (admitted entrants) in a dialog</li>
 *   <li>Export final entrants list to CSV format</li>
 * </ul>
 *
 * <p>The activity loads entrants from Firestore subcollections and allows organizers to
 * manage the selection process for their events.
 */
package com.example.eventease.ui.organizer;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.Calendar;

import com.example.eventease.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;


public class OrganizerViewEntrantsActivity extends AppCompatActivity {
    private static final String TAG = "OrganizerViewEntrants";

    private ListView listSelected, listNotSelected, listCancelled;
    private FirebaseFirestore db;
    private String eventId;
    private String eventTitle;

    private final List<String> selectedList = new ArrayList<>();
    private final List<String> notSelectedList = new ArrayList<>();
    private final List<String> cancelledList = new ArrayList<>();

    private ArrayAdapter<String> selectedAdapter;
    private ArrayAdapter<String> notSelectedAdapter;
    private ArrayAdapter<String> cancelledAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewentrants);

        ImageView back = findViewById(R.id.back_button);
        if (back != null) back.setOnClickListener(v -> finish());

        ImageButton btnMailSelected = findViewById(R.id.btnMailSelected);
        if (btnMailSelected != null) {
            btnMailSelected.setOnClickListener(v -> showSendInvitationsConfirmation());
        }

        listSelected = findViewById(R.id.recyclerSelected);
        listNotSelected = findViewById(R.id.recyclerNotSelected);
        listCancelled = findViewById(R.id.recyclerCancelled);

        db = FirebaseFirestore.getInstance();

        selectedAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, selectedList);
        notSelectedAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, notSelectedList);
        cancelledAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, cancelledList);

        listSelected.setAdapter(selectedAdapter);
        listNotSelected.setAdapter(notSelectedAdapter);
        listCancelled.setAdapter(cancelledAdapter);

        // Add click listeners for moving entrants between categories
        listSelected.setOnItemClickListener((parent, view, position, id) -> {
            String entrantName = selectedList.get(position);
            showMoveEntrantDialog(entrantName, "SelectedEntrants", position);
        });

        listNotSelected.setOnItemClickListener((parent, view, position, id) -> {
            String entrantName = notSelectedList.get(position);
            showMoveEntrantDialog(entrantName, "NonSelectedEntrants", position);
        });

        listCancelled.setOnItemClickListener((parent, view, position, id) -> {
            String entrantName = cancelledList.get(position);
            showMoveEntrantDialog(entrantName, "CancelledEntrants", position);
        });

        eventId = getIntent().getStringExtra("eventId");
        if (eventId != null && !eventId.isEmpty()) {
            checkAndProcessSelection(eventId);
            loadEventTitle();
            // Also check and process deadline for non-responders
            checkAndProcessDeadline(eventId);
        }

        loadEntrantsFromFirestore();

        // Set up Final Entrants button
        Button btnFinalEntrants = findViewById(R.id.finalentrant_button);
        if (btnFinalEntrants != null) {
            btnFinalEntrants.setOnClickListener(v -> showFinalEntrantListDialog());
        }

        // Set up Replacement Swap button
        ImageButton btnSwapNotSelected = findViewById(R.id.btnSwapNotSelected);
        if (btnSwapNotSelected != null) {
            btnSwapNotSelected.setOnClickListener(v -> showReplacementSwapDialog());
        }

        // Set up notification button for Not Selected Entrants
        ImageButton btnMailNotSelected = findViewById(R.id.btnMailNotSelected);
        if (btnMailNotSelected != null) {
            btnMailNotSelected.setOnClickListener(v -> showSendNotificationsToNotSelectedConfirmation());
        }

        // Set up notification button for Cancelled Entrants
        ImageButton btnMailCancelled = findViewById(R.id.btnMailCancelled);
        if (btnMailCancelled != null) {
            btnMailCancelled.setOnClickListener(v -> showSendNotificationsToCancelledConfirmation());
        }
    }

    private void loadEventTitle() {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        eventTitle = doc.getString("title");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load event title", e);
                });
    }

    private void showSendInvitationsConfirmation() {
        if (selectedList.isEmpty()) {
            Toast.makeText(this, "No selected entrants to send notifications to", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = selectedList.size();
        String message = "Send notifications to " + count + " selected entrant" + (count > 1 ? "s" : "") + "? They will receive push notifications even when the app is closed.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Notifications")
                .setMessage(message)
                .setPositiveButton("Send", (dialog, which) -> sendNotificationsToSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendNotificationsToSelected() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventTitle == null || eventTitle.isEmpty()) {
            Toast.makeText(this, "Event title not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Sending notifications...", Toast.LENGTH_SHORT).show();

        // Use NotificationHelper to send notifications via Cloud Function
        // This works even when the app is closed (user logged in with "remember me")
        NotificationHelper notificationHelper = new NotificationHelper();
        String message = "You have an update regarding \"" + eventTitle + "\". Please check your invitations.";
        
        notificationHelper.sendNotificationsToSelected(eventId, eventTitle, message, new NotificationHelper.NotificationCallback() {
            @Override
            public void onComplete(int sentCount) {
                Toast.makeText(OrganizerViewEntrantsActivity.this,
                        "Successfully sent notifications to " + sentCount + " selected entrant" + (sentCount > 1 ? "s" : ""),
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Sent notifications to " + sentCount + " selected entrants");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OrganizerViewEntrantsActivity.this,
                        "Failed to send notifications: " + error,
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to send notifications: " + error);
            }
        });
    }

    private void showSendNotificationsToNotSelectedConfirmation() {
        if (notSelectedList.isEmpty()) {
            Toast.makeText(this, "No not selected entrants to send notifications to", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = notSelectedList.size();
        String message = "Send notifications to " + count + " not selected entrant" + (count > 1 ? "s" : "") + "? They will receive push notifications even when the app is closed.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Notifications")
                .setMessage(message)
                .setPositiveButton("Send", (dialog, which) -> sendNotificationsToNotSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendNotificationsToNotSelected() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventTitle == null || eventTitle.isEmpty()) {
            Toast.makeText(this, "Event title not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Sending notifications...", Toast.LENGTH_SHORT).show();

        // Not Selected Entrants are stored in NonSelectedEntrants collection
        NotificationHelper notificationHelper = new NotificationHelper();
        // Use default message from NotificationHelper
        notificationHelper.sendNotificationsToNonSelected(eventId, eventTitle, null, new NotificationHelper.NotificationCallback() {
            @Override
            public void onComplete(int sentCount) {
                Toast.makeText(OrganizerViewEntrantsActivity.this,
                        "Successfully sent notifications to " + sentCount + " not selected entrant" + (sentCount > 1 ? "s" : ""),
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Sent notifications to " + sentCount + " not selected entrants");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OrganizerViewEntrantsActivity.this,
                        "Failed to send notifications: " + error,
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to send notifications: " + error);
            }
        });
    }

    private void showSendNotificationsToCancelledConfirmation() {
        if (cancelledList.isEmpty()) {
            Toast.makeText(this, "No cancelled entrants to send notifications to", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = cancelledList.size();
        String message = "Send notifications to " + count + " cancelled entrant" + (count > 1 ? "s" : "") + "? They will receive push notifications even when the app is closed.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Notifications")
                .setMessage(message)
                .setPositiveButton("Send", (dialog, which) -> sendNotificationsToCancelled())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendNotificationsToCancelled() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventTitle == null || eventTitle.isEmpty()) {
            Toast.makeText(this, "Event title not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Sending notifications...", Toast.LENGTH_SHORT).show();

        NotificationHelper notificationHelper = new NotificationHelper();
        // Use default message from NotificationHelper
        notificationHelper.sendNotificationsToCancelled(eventId, eventTitle, null, new NotificationHelper.NotificationCallback() {
            @Override
            public void onComplete(int sentCount) {
                Toast.makeText(OrganizerViewEntrantsActivity.this,
                        "Successfully sent notifications to " + sentCount + " cancelled entrant" + (sentCount > 1 ? "s" : ""),
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Sent notifications to " + sentCount + " cancelled entrants");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OrganizerViewEntrantsActivity.this,
                        "Failed to send notifications: " + error,
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to send notifications: " + error);
            }
        });
    }

    private void checkAndProcessSelection(String eventId) {
        EventSelectionHelper selectionHelper = new EventSelectionHelper();
        selectionHelper.checkAndProcessEventSelection(eventId, new EventSelectionHelper.SelectionCallback() {
            @Override
            public void onComplete(int selectedCount) {
                if (selectedCount > 0) {
                    loadEntrantsFromFirestore();
                }
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    /**
     * Checks and processes deadline for non-responders.
     * This ensures that entrants who didn't respond by the deadline are moved to CancelledEntrants.
     */
    private void checkAndProcessDeadline(String eventId) {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        return;
                    }
                    
                    Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
                    long currentTime = System.currentTimeMillis();
                    
                    // Only process if deadline has passed
                    if (deadlineEpochMs != null && deadlineEpochMs > 0 && currentTime >= deadlineEpochMs) {
                        InvitationDeadlineProcessor deadlineProcessor = new InvitationDeadlineProcessor();
                        deadlineProcessor.processDeadlineForEvent(eventId, new InvitationDeadlineProcessor.DeadlineCallback() {
                            @Override
                            public void onComplete(int processedCount) {
                                if (processedCount > 0) {
                                    Log.d(TAG, "Processed " + processedCount + " non-responders, reloading entrants");
                                    loadEntrantsFromFirestore();
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Error processing deadline: " + error);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check deadline", e);
                });
    }

    private void loadEntrantsFromFirestore() {
        String eventId = getIntent().getStringExtra("eventId");
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Missing eventId", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedList.clear();
        notSelectedList.clear();
        cancelledList.clear();

        db.collection("events").document(eventId).collection("SelectedEntrants").get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        selectedList.add(safeName(doc));
                    }
                    selectedAdapter.notifyDataSetChanged();
                });

        // Not Selected Entrants are in NonSelectedEntrants collection (moved there after deadline)
        // Waitlisted entrants stay in WaitlistedEntrants until first random roll happens
        db.collection("events").document(eventId).collection("NonSelectedEntrants").get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        notSelectedList.add(safeName(doc));
                    }
                    notSelectedAdapter.notifyDataSetChanged();
                });

        db.collection("events").document(eventId).collection("CancelledEntrants").get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        cancelledList.add(safeName(doc));
                    }
                    cancelledAdapter.notifyDataSetChanged();
                    
                    // NOTE: Automatic replacement is disabled - organizer must manually replace via button
                });
    }

    private String safeName(DocumentSnapshot doc) {
        String name = doc.getString("name");
        if (name == null || name.trim().isEmpty()) {
            String full = doc.getString("fullName");
            if (full != null && !full.trim().isEmpty()) return full;
            String first = doc.getString("firstName");
            String last = doc.getString("lastName");
            if ((first != null && !first.isEmpty()) || (last != null && !last.isEmpty())) {
                return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            }
            name = "(unknown)";
        }
        return name;
    }

    private void showFinalEntrantListDialog() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);

        // Create custom dialog with full screen to show blur background
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.organizer_dialog_final_entrants);

        // Set window properties
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

            // Disable dim since we have our own blur background
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0f;
            window.setAttributes(layoutParams);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        // Apply blurred background
        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurredBitmap != null && blurBackground != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }

        // Make the background clickable to dismiss
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }

        // Get the CardView for animation
        CardView cardView = dialog.findViewById(R.id.dialogCardView);

        // Get the container for entrant names
        LinearLayout entrantListContainer = dialog.findViewById(R.id.entrantListContainer);

        // Load final selected entrants (after all replacements)
        List<Map<String, String>> finalEntrantsData = new ArrayList<>();
        loadFinalSelectedEntrants(eventId, entrantListContainer, finalEntrantsData);

        // Set up Export to CSV button
        Button btnExportCSV = dialog.findViewById(R.id.btnExportCSV);
        if (btnExportCSV != null) {
            btnExportCSV.setOnClickListener(v -> exportToCSV(finalEntrantsData, dialog));
        }

        // Set up Close button
        Button btnClose = dialog.findViewById(R.id.btnDialogOk);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();

        // Apply animations after dialog is shown
        if (blurBackground != null && cardView != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);

            blurBackground.startAnimation(fadeIn);
            cardView.startAnimation(zoomIn);
        }
    }

    private void loadFinalSelectedEntrants(String eventId, LinearLayout container, List<Map<String, String>> entrantsData) {
        if (container == null || eventId == null || eventId.isEmpty()) {
            return;
        }

        // Clear container first
        container.removeAllViews();
        entrantsData.clear();

        // Show loading message
        TextView loadingText = new TextView(this);
        loadingText.setText("Loading final entrants...");
        loadingText.setTextSize(16);
        loadingText.setTextColor(0xFF223C65);
        loadingText.setPadding(16, 16, 16, 16);
        container.addView(loadingText);

        // Load from SelectedEntrants (final list after all replacements)
        db.collection("events").document(eventId).collection("SelectedEntrants")
                .get()
                .addOnSuccessListener((QuerySnapshot querySnapshot) -> {
                    container.removeAllViews();

                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        TextView emptyText = new TextView(this);
                        emptyText.setText("No final entrants yet. Entrants will appear here after selection.");
                        emptyText.setTextSize(16);
                        emptyText.setTextColor(0xFF223C65);
                        emptyText.setPadding(16, 16, 16, 16);
                        emptyText.setGravity(android.view.Gravity.CENTER);
                        container.addView(emptyText);
                        return;
                    }

                    // Store full entrant data for CSV export
                    List<Map<String, String>> entrantsList = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Map<String, String> entrantData = new HashMap<>();
                        entrantData.put("name", safeName(doc));
                        entrantData.put("email", doc.getString("email") != null ? doc.getString("email") : "");
                        entrantData.put("phoneNumber", doc.getString("phoneNumber") != null ? doc.getString("phoneNumber") : "");
                        entrantData.put("userId", doc.getString("userId") != null ? doc.getString("userId") : doc.getId());
                        entrantsList.add(entrantData);
                    }

                    // Sort by name alphabetically
                    entrantsList.sort((a, b) -> {
                        String nameA = a.get("name");
                        String nameB = b.get("name");
                        if (nameA == null) nameA = "";
                        if (nameB == null) nameB = "";
                        return nameA.compareToIgnoreCase(nameB);
                    });

                    // Update the shared data list
                    entrantsData.addAll(entrantsList);

                    // Add each name as a TextView
                    for (int i = 0; i < entrantsList.size(); i++) {
                        Map<String, String> entrant = entrantsList.get(i);
                        TextView nameText = new TextView(this);
                        nameText.setText((i + 1) + ". " + entrant.get("name"));
                        nameText.setTextSize(18);
                        nameText.setTextColor(0xFF223C65);
                        nameText.setPadding(16, 12, 16, 12);
                        nameText.setTypeface(android.graphics.Typeface.DEFAULT);

                        // Add divider except for last item
                        if (i < entrantsList.size() - 1) {
                            View divider = new View(this);
                            divider.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    1
                            ));
                            divider.setBackgroundColor(0xFFE0E0E0);
                            container.addView(nameText);
                            container.addView(divider);
                        } else {
                            container.addView(nameText);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load final selected entrants", e);
                    container.removeAllViews();
                    TextView errorText = new TextView(this);
                    errorText.setText("Failed to load final entrants. Please try again.");
                    errorText.setTextSize(16);
                    errorText.setTextColor(0xFFFF0000);
                    errorText.setPadding(16, 16, 16, 16);
                    container.addView(errorText);
                });
    }

    private void exportToCSV(List<Map<String, String>> entrantsData, android.app.Dialog dialog) {
        if (entrantsData == null || entrantsData.isEmpty()) {
            Toast.makeText(this, "No entrants to export", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Generate CSV content
            StringBuilder csvContent = new StringBuilder();

            // CSV Header
            csvContent.append("Name,Email,Phone Number,User ID\n");

            // CSV Data rows
            for (Map<String, String> entrant : entrantsData) {
                String name = escapeCSV(entrant.get("name") != null ? entrant.get("name") : "");
                String email = escapeCSV(entrant.get("email") != null ? entrant.get("email") : "");
                String phone = escapeCSV(entrant.get("phoneNumber") != null ? entrant.get("phoneNumber") : "");
                String userId = escapeCSV(entrant.get("userId") != null ? entrant.get("userId") : "");

                csvContent.append(name).append(",")
                        .append(email).append(",")
                        .append(phone).append(",")
                        .append(userId).append("\n");
            }

            // Generate filename with event title and timestamp
            String eventTitleSafe = (eventTitle != null && !eventTitle.isEmpty())
                    ? eventTitle.replaceAll("[^a-zA-Z0-9]", "_")
                    : "Event";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String fileName = eventTitleSafe + "_FinalEntrants_" + timestamp + ".csv";

            // Save to Downloads folder
            boolean success = saveFileToDownloads(fileName, csvContent.toString());

            if (success) {
                Toast.makeText(this, "CSV file saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
                Log.d(TAG, "CSV exported successfully: " + fileName);
            } else {
                Toast.makeText(this, "Failed to save CSV file", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to export CSV");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error exporting CSV", e);
            Toast.makeText(this, "Error exporting CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, newline, or quote, wrap it in quotes and escape quotes
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean saveFileToDownloads(String fileName, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+) - Use MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        if (outputStream != null) {
                            outputStream.write(content.getBytes("UTF-8"));
                            outputStream.flush();
                            return true;
                        }
                    }
                }
            } else {
                // Android 9 and below - Direct file access
                java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                java.io.File file = new java.io.File(downloadsDir, fileName);
                try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                    writer.write(content);
                    writer.flush();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving file to Downloads", e);
        }
        return false;
    }

    private Bitmap captureScreenshot() {
        try {
            android.view.View rootView = getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screenshot", e);
            return null;
        }
    }

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

    private void showReplacementSwapDialog() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check event deadline first
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Long eventStart = eventDoc.getLong("eventStart");
                    Long deadlineEpochMs = eventDoc.getLong("deadlineEpochMs");
                    Long capacity = eventDoc.getLong("capacity");
                    long currentTime = System.currentTimeMillis();

                    // Check if we're within 72 hours before event (last date to click replacement button)
                    if (eventStart != null) {
                        long seventyTwoHoursBeforeEvent = eventStart - (72L * 60 * 60 * 1000);
                        if (currentTime >= seventyTwoHoursBeforeEvent) {
                            Toast.makeText(this, "Replacement deadline has passed. Last replacement must be at least 72 hours before event.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }

                    // Check if event has started
                    if (eventStart != null && currentTime >= eventStart) {
                        Toast.makeText(this, "Event has started. No more replacements allowed.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Check if deadline has passed and capacity not fulfilled
                    boolean deadlinePassed = deadlineEpochMs != null && currentTime >= deadlineEpochMs;
                    int capacityInt = capacity != null ? capacity.intValue() : -1;
                    
                    // Count selected entrants to check if capacity is fulfilled
                    db.collection("events").document(eventId).collection("SelectedEntrants").get()
                            .addOnSuccessListener(selectedSnapshot -> {
                                int selectedCount = selectedSnapshot != null ? selectedSnapshot.size() : 0;
                                boolean capacityNotFulfilled = capacityInt > 0 && selectedCount < capacityInt;

                                // Count cancelled and waitlisted entrants
                                db.collection("events").document(eventId).collection("CancelledEntrants").get()
                                        .addOnSuccessListener(cancelledSnapshot -> {
                                            int cancelledCount = cancelledSnapshot != null ? cancelledSnapshot.size() : 0;

                                            db.collection("events").document(eventId).collection("WaitlistedEntrants").get()
                                                    .addOnSuccessListener(waitlistSnapshot -> {
                                                        int waitlistCount = waitlistSnapshot != null ? waitlistSnapshot.size() : 0;

                                                        if (cancelledCount == 0) {
                                                            Toast.makeText(this, "No cancelled entrants to replace", Toast.LENGTH_SHORT).show();
                                                            return;
                                                        }

                                                        if (waitlistCount == 0) {
                                                            Toast.makeText(this, "No waitlisted entrants available for replacement", Toast.LENGTH_SHORT).show();
                                                            return;
                                                        }

                                                        // If deadline passed and capacity not fulfilled, ask for new deadline
                                                        if (deadlinePassed && capacityNotFulfilled) {
                                                            showNewDeadlineDialog(eventId, eventStart, deadlineEpochMs, cancelledCount, waitlistCount);
                                                        } else {
                                                            // Normal replacement flow
                                                            int toReplace = Math.min(cancelledCount, waitlistCount);
                                                            String message = String.format(
                                                                    "Replace %d cancelled entrant%s with %d entrant%s from waitlist?",
                                                                    cancelledCount, cancelledCount == 1 ? "" : "s",
                                                                    toReplace, toReplace == 1 ? "" : "s"
                                                            );

                                                            new MaterialAlertDialogBuilder(this)
                                                                    .setTitle("Replacement Swap")
                                                                    .setMessage(message)
                                                                    .setPositiveButton("Replace", (dialog, which) -> performReplacementSwap(toReplace, deadlineEpochMs))
                                                                    .setNegativeButton("Cancel", null)
                                                                    .show();
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e(TAG, "Failed to load waitlisted entrants", e);
                                                        Toast.makeText(this, "Failed to load waitlisted entrants", Toast.LENGTH_SHORT).show();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to load cancelled entrants", e);
                                            Toast.makeText(this, "Failed to load cancelled entrants", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load selected entrants", e);
                                Toast.makeText(this, "Failed to load selected entrants", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event", e);
                    Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show();
                });
    }

    private void performReplacementSwap(int count) {
        // Use current deadline from event
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    Long deadlineEpochMs = eventDoc != null ? eventDoc.getLong("deadlineEpochMs") : null;
                    performReplacementSwap(count, deadlineEpochMs);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event deadline", e);
                    performReplacementSwap(count, null);
                });
    }

    private void performReplacementSwap(int count, Long deadlineEpochMs) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        Toast.makeText(this, "Processing replacement swap...", Toast.LENGTH_SHORT).show();

        // Calculate deadline for replacement invitations
        final long currentTime = System.currentTimeMillis();
        long initialDeadline = currentTime + (7L * 24 * 60 * 60 * 1000); // 7 days from now
        
        if (deadlineEpochMs != null) {
            initialDeadline = Math.min(initialDeadline, deadlineEpochMs);
        }
        
        final long baseDeadline = initialDeadline;
        
        // Get event start as fallback if needed
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    Long eventStart = eventDoc != null ? eventDoc.getLong("eventStart") : null;
                    
                    // Calculate deadline
                    long calculatedDeadline = baseDeadline;
                    if (eventStart != null && deadlineEpochMs == null) {
                        calculatedDeadline = Math.min(calculatedDeadline, eventStart);
                    }
                    // Ensure minimum 2 days
                    long minDeadline = currentTime + (2L * 24 * 60 * 60 * 1000);
                    calculatedDeadline = Math.max(calculatedDeadline, minDeadline);
                    final long deadlineToAccept = calculatedDeadline;

                    // Fetch waitlisted entrants
                    db.collection("events").document(eventId).collection("WaitlistedEntrants").get()
                            .addOnSuccessListener(waitlistSnapshot -> {
                                if (waitlistSnapshot == null || waitlistSnapshot.isEmpty()) {
                                    Toast.makeText(this, "No waitlisted entrants available", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                List<DocumentSnapshot> waitlistDocs = waitlistSnapshot.getDocuments();
                                if (waitlistDocs.size() < count) {
                                    Toast.makeText(this, "Not enough waitlisted entrants available", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Randomly select entrants
                                List<DocumentSnapshot> selectedForReplacement = randomlySelect(waitlistDocs, count);
                                
                                DocumentReference eventRef = db.collection("events").document(eventId);
                                WriteBatch batch = db.batch();
                                List<String> userIds = new ArrayList<>();

                                // Move selected entrants from WaitlistedEntrants to SelectedEntrants
                                for (DocumentSnapshot doc : selectedForReplacement) {
                                    String userId = doc.getId();
                                    Map<String, Object> data = doc.getData();
                                    userIds.add(userId);

                                    if (data != null) {
                                        // Move to SelectedEntrants
                                        batch.set(eventRef.collection("SelectedEntrants").document(userId), data);
                                        // Remove from WaitlistedEntrants
                                        batch.delete(eventRef.collection("WaitlistedEntrants").document(userId));
                                        
                                        // Create invitation for replacement
                                        Map<String, Object> invitation = new HashMap<>();
                                        invitation.put("eventId", eventId);
                                        invitation.put("userId", userId);
                                        invitation.put("status", "PENDING");
                                        invitation.put("createdAt", System.currentTimeMillis());
                                        invitation.put("expiresAt", deadlineToAccept);
                                        invitation.put("isReplacement", true);
                                        
                                        batch.set(db.collection("invitations").document(UUID.randomUUID().toString()), invitation);
                                    }
                                }

                                batch.commit()
                                        .addOnSuccessListener(v -> {
                                            Toast.makeText(this, 
                                                    "Successfully replaced " + count + " entrant" + (count == 1 ? "" : "s"),
                                                    Toast.LENGTH_LONG).show();
                                            
                                            // Send notifications
                                            String eventTitleStr = eventTitle != null ? eventTitle : "the event";
                                            sendReplacementNotifications(userIds, eventTitleStr, deadlineToAccept);
                                            
                                            // Reload entrants
                                            loadEntrantsFromFirestore();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to perform replacement swap", e);
                                            Toast.makeText(this, "Failed to perform replacement swap: " + e.getMessage(), 
                                                    Toast.LENGTH_LONG).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load waitlisted entrants for replacement", e);
                                Toast.makeText(this, "Failed to load waitlisted entrants", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for replacement", e);
                    Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show();
                });
    }

    private void showNewDeadlineDialog(String eventId, Long eventStart, Long oldDeadline, int cancelledCount, int waitlistCount) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.organizer_dialog_new_deadline);
        dialog.setCancelable(true);

        Button btnSelectDeadline = dialog.findViewById(R.id.btnSelectNewDeadline);
        TextView tvSelectedDeadline = dialog.findViewById(R.id.tvSelectedDeadline);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirmDeadline);
        Button btnCancel = dialog.findViewById(R.id.btnCancelDeadline);

        long[] newDeadlineMs = {0L};

        // Calculate min and max dates for deadline
        long minDeadlineMs = oldDeadline != null ? oldDeadline : System.currentTimeMillis();
        long maxDeadlineMs = eventStart != null ? (eventStart - (48L * 60 * 60 * 1000)) : Long.MAX_VALUE; // 48 hours before event

        btnSelectDeadline.setOnClickListener(v -> {
            final Calendar now = Calendar.getInstance();
            Calendar minDate = Calendar.getInstance();
            minDate.setTimeInMillis(minDeadlineMs);
            
            DatePickerDialog dp = new DatePickerDialog(
                    this, (view, y, m, d) -> {
                TimePickerDialog tp = new TimePickerDialog(
                        this, (vv, hh, mm) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(y, m, d, hh, mm, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    long chosenMs = chosen.getTimeInMillis();
                    
                    // Validate: must be after old deadline
                    if (chosenMs <= minDeadlineMs) {
                        Toast.makeText(this, "New deadline must be after the previous deadline", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Validate: must be at least 48 hours before event
                    if (eventStart != null && chosenMs >= maxDeadlineMs) {
                        Toast.makeText(this, "Deadline must be at least 48 hours before event date", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    newDeadlineMs[0] = chosenMs;
                    tvSelectedDeadline.setText(android.text.format.DateFormat
                            .format("MMM d, yyyy  h:mm a", chosen));
                    tvSelectedDeadline.setVisibility(android.view.View.VISIBLE);
                    btnConfirm.setEnabled(true);
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
                tp.show();
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            
            dp.getDatePicker().setMinDate(minDeadlineMs);
            if (eventStart != null) {
                dp.getDatePicker().setMaxDate(maxDeadlineMs);
            }
            dp.show();
        });

        btnConfirm.setOnClickListener(v -> {
            if (newDeadlineMs[0] == 0L) {
                Toast.makeText(this, "Please select a new deadline", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Update deadline in Firestore
            db.collection("events").document(eventId)
                    .update("deadlineEpochMs", newDeadlineMs[0])
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "New deadline set successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        
                        // Perform replacement with new deadline
                        int toReplace = Math.min(cancelledCount, waitlistCount);
                        performReplacementSwap(toReplace, newDeadlineMs[0]);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update deadline", e);
                        Toast.makeText(this, "Failed to update deadline: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private List<DocumentSnapshot> randomlySelect(List<DocumentSnapshot> allDocs, int count) {
        List<DocumentSnapshot> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private void sendReplacementNotifications(List<String> userIds, String eventTitle, long deadlineMs) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
        String deadlineStr = sdf.format(new Date(deadlineMs));
        String message = String.format(
                "You've been selected as a replacement for \"%s\"! Please accept or decline by %s",
                eventTitle, deadlineStr
        );

        // Use NotificationHelper for push notifications (works when app is closed via Cloud Functions)
        NotificationHelper notificationHelper = new NotificationHelper();
        notificationHelper.sendNotificationsToUsers(
                userIds,
                "Replacement Invitation",
                message,
                eventId,
                eventTitle != null ? eventTitle : "the event",
                false, // filterDeclined = false (they're new replacements, haven't declined yet)
                new NotificationHelper.NotificationCallback() {
                    @Override
                    public void onComplete(int sentCount) {
                        Log.d(TAG, "Successfully sent " + sentCount + " replacement push notifications");
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to send replacement notifications: " + error);
                    }
                }
        );
    }

    /**
     * Shows a dialog to move an entrant between categories (Selected, Not Selected, Cancelled).
     */
    private void showMoveEntrantDialog(String entrantName, String currentCollection, int position) {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find the userId by name - we need to search in the current collection
        DocumentReference eventRef = db.collection("events").document(eventId);
        final String finalCurrentCollection = currentCollection; // Make effectively final
        eventRef.collection(currentCollection).get()
                .addOnSuccessListener(snapshot -> {
                    String foundUserId = null;
                    Map<String, Object> foundEntrantData = null;
                    
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String name = safeName(doc);
                        if (name.equals(entrantName)) {
                            foundUserId = doc.getId();
                            foundEntrantData = doc.getData();
                            break;
                        }
                    }
                    
                    if (foundUserId == null) {
                        Toast.makeText(this, "Entrant not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Make final for use in lambda
                    final String finalUserId = foundUserId;
                    final Map<String, Object> finalEntrantData = foundEntrantData;
                    
                    // Show move options dialog
                    String[] options;
                    if ("SelectedEntrants".equals(finalCurrentCollection)) {
                        options = new String[]{"Move to Not Selected", "Move to Cancelled"};
                    } else if ("NonSelectedEntrants".equals(finalCurrentCollection)) {
                        options = new String[]{"Move to Selected", "Move to Cancelled"};
                    } else { // CancelledEntrants
                        options = new String[]{"Move to Selected", "Move to Not Selected"};
                    }
                    
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Move Entrant: " + entrantName)
                            .setItems(options, (dialog, which) -> {
                                String targetCollection;
                                if ("SelectedEntrants".equals(finalCurrentCollection)) {
                                    targetCollection = which == 0 ? "NonSelectedEntrants" : "CancelledEntrants";
                                } else if ("NonSelectedEntrants".equals(finalCurrentCollection)) {
                                    targetCollection = which == 0 ? "SelectedEntrants" : "CancelledEntrants";
                                } else { // CancelledEntrants
                                    targetCollection = which == 0 ? "SelectedEntrants" : "NonSelectedEntrants";
                                }
                                
                                moveEntrantBetweenCollections(finalUserId, finalEntrantData, finalCurrentCollection, targetCollection);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to find entrant", e);
                    Toast.makeText(this, "Failed to find entrant: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Moves an entrant from one collection to another.
     */
    private void moveEntrantBetweenCollections(String userId, Map<String, Object> entrantData,
                                                String fromCollection, String toCollection) {
        if (eventId == null || eventId.isEmpty() || userId == null) {
            Toast.makeText(this, "Invalid data", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference fromRef = eventRef.collection(fromCollection).document(userId);
        DocumentReference toRef = eventRef.collection(toCollection).document(userId);

        WriteBatch batch = db.batch();
        
        if (entrantData != null) {
            batch.set(toRef, entrantData);
        } else {
            // If no data, fetch from users collection
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(userDoc -> {
                        Map<String, Object> userData = new HashMap<>();
                        if (userDoc != null && userDoc.exists()) {
                            String name = userDoc.getString("fullName");
                            if (name == null || name.trim().isEmpty()) {
                                name = userDoc.getString("name");
                            }
                            if (name == null || name.trim().isEmpty()) {
                                String first = userDoc.getString("firstName");
                                String last = userDoc.getString("lastName");
                                name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                            }
                            if (name != null && !name.trim().isEmpty()) {
                                userData.put("name", name);
                            }
                            String email = userDoc.getString("email");
                            if (email != null && !email.trim().isEmpty()) {
                                userData.put("email", email);
                            }
                            userData.put("userId", userId);
                        }
                        
                        WriteBatch moveBatch = db.batch();
                        moveBatch.set(toRef, userData);
                        moveBatch.delete(fromRef);
                        moveBatch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Entrant moved successfully", Toast.LENGTH_SHORT).show();
                                    loadEntrantsFromFirestore();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to move entrant", e);
                                    Toast.makeText(this, "Failed to move entrant: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch user data", e);
                        Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show();
                    });
            return;
        }
        
        batch.delete(fromRef);
        
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Entrant moved successfully", Toast.LENGTH_SHORT).show();
                    loadEntrantsFromFirestore();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to move entrant", e);
                    Toast.makeText(this, "Failed to move entrant: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
