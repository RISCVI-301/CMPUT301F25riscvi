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

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

        // Load all three collections simultaneously and filter duplicates
        // A user should only appear in ONE collection (Selected, NonSelected, or Cancelled)
        db.collection("events").document(eventId).collection("SelectedEntrants").get()
                .addOnSuccessListener(selectedSnap -> {
                    // Store userIds to filter duplicates
                    Set<String> selectedUserIds = new HashSet<>();
                    for (DocumentSnapshot doc : selectedSnap.getDocuments()) {
                        selectedUserIds.add(doc.getId());
                        selectedList.add(safeName(doc));
                    }
                    selectedAdapter.notifyDataSetChanged();
                    
                    // Now load NonSelectedEntrants and filter out duplicates
                    db.collection("events").document(eventId).collection("NonSelectedEntrants").get()
                            .addOnSuccessListener(nonSelectedSnap -> {
                                Set<String> nonSelectedUserIds = new HashSet<>();
                                for (DocumentSnapshot doc : nonSelectedSnap.getDocuments()) {
                                    String userId = doc.getId();
                                    // Only add if NOT already in SelectedEntrants
                                    if (!selectedUserIds.contains(userId)) {
                                        nonSelectedUserIds.add(userId);
                                        notSelectedList.add(safeName(doc));
                                    } else {
                                        Log.w(TAG, "Filtered duplicate: " + userId + " appears in both SelectedEntrants and NonSelectedEntrants");
                                    }
                                }
                                notSelectedAdapter.notifyDataSetChanged();
                                
                                // Now load CancelledEntrants and filter out duplicates
                                db.collection("events").document(eventId).collection("CancelledEntrants").get()
                                        .addOnSuccessListener(cancelledSnap -> {
                                            for (DocumentSnapshot doc : cancelledSnap.getDocuments()) {
                                                String userId = doc.getId();
                                                // Only add if NOT already in SelectedEntrants or NonSelectedEntrants
                                                if (!selectedUserIds.contains(userId) && !nonSelectedUserIds.contains(userId)) {
                                                    cancelledList.add(safeName(doc));
                                                } else {
                                                    Log.w(TAG, "Filtered duplicate: " + userId + " appears in multiple collections");
                                                }
                                            }
                                            cancelledAdapter.notifyDataSetChanged();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to load CancelledEntrants", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load NonSelectedEntrants", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load SelectedEntrants", e);
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

                    Long eventDeadline = eventDoc.getLong("eventDeadline");
                    Long startsAtEpochMs = eventDoc.getLong("startsAtEpochMs");
                    long currentTime = System.currentTimeMillis();

                    // Allow replacements before event starts
                    if (startsAtEpochMs != null && currentTime >= startsAtEpochMs) {
                        Toast.makeText(this, "Event has already started. No more replacements allowed.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Count cancelled and non-selected entrants
                    db.collection("events").document(eventId).collection("CancelledEntrants").get()
                            .addOnSuccessListener(cancelledSnapshot -> {
                                int cancelledCount = cancelledSnapshot != null ? cancelledSnapshot.size() : 0;

                                // Check how many spots are available (sampleSize - current selected count)
                                db.collection("events").document(eventId).collection("SelectedEntrants").get()
                                        .addOnSuccessListener(selectedSnapshot -> {
                                            int selectedCount = selectedSnapshot != null ? selectedSnapshot.size() : 0;
                                            Long sampleSizeObj = eventDoc.getLong("sampleSize");
                                            int sampleSize = sampleSizeObj != null ? sampleSizeObj.intValue() : 0;
                                            
                                            // CRITICAL: Replacement should ONLY happen if selectedCount < sampleSize
                                            if (selectedCount >= sampleSize) {
                                                Toast.makeText(this, 
                                                    String.format("Cannot replace: Selected entrants (%d) already equals or exceeds sample size (%d). Sample size limit reached.", 
                                                        selectedCount, sampleSize), 
                                                    Toast.LENGTH_LONG).show();
                                                return;
                                            }
                                            
                                            int availableSpots = sampleSize - selectedCount;

                                            db.collection("events").document(eventId).collection("NonSelectedEntrants").get()
                                                    .addOnSuccessListener(nonSelectedSnapshot -> {
                                                        int nonSelectedCount = nonSelectedSnapshot != null ? nonSelectedSnapshot.size() : 0;

                                                        if (nonSelectedCount == 0) {
                                                            Toast.makeText(this, "No non-selected entrants available for replacement", Toast.LENGTH_SHORT).show();
                                                            return;
                                                        }

                                                        // Calculate how many can be replaced - NEVER exceed sampleSize
                                                        int toReplace = Math.min(availableSpots, nonSelectedCount);
                                                        
                                                        if (toReplace <= 0) {
                                                            Toast.makeText(this, "No available spots for replacement (sample size limit reached)", Toast.LENGTH_SHORT).show();
                                                            return;
                                                        }
                                                        
                                                        Log.d(TAG, "Replacement check: selectedCount=" + selectedCount + ", sampleSize=" + sampleSize + ", availableSpots=" + availableSpots + ", toReplace=" + toReplace);

                                                        String message = String.format(
                                                                "Select %d entrant%s from Non-Selected to replace cancelled/available spots?\n\nYou will need to set a deadline for them to accept/decline.",
                                                                toReplace, toReplace == 1 ? "" : "s"
                                                        );

                                                        new MaterialAlertDialogBuilder(this)
                                                                .setTitle("Manual Replacement Selection")
                                                                .setMessage(message)
                                                                .setPositiveButton("Select", (dialog, which) -> showDeadlinePickerAndPerformReplacement(toReplace, eventDoc))
                                                                .setNegativeButton("Cancel", null)
                                                                .show();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e(TAG, "Failed to load non-selected entrants", e);
                                                        Toast.makeText(this, "Failed to load non-selected entrants", Toast.LENGTH_SHORT).show();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to load selected entrants", e);
                                            Toast.makeText(this, "Failed to load selected entrants", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load cancelled entrants", e);
                                Toast.makeText(this, "Failed to load cancelled entrants", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event", e);
                    Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeadlinePickerAndPerformReplacement(int count, DocumentSnapshot eventDoc) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        Long eventStartTemp = eventDoc.getLong("startsAtEpochMs");
        if (eventStartTemp == null) {
            eventStartTemp = eventDoc.getLong("eventStart");
        }
        final Long eventStart = eventStartTemp; // Make final for lambda
        
        // Initialize calendar to current time (let user pick any time in future before event starts)
        Calendar cal = Calendar.getInstance();
        
        // Show date picker
        new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(year, month, dayOfMonth);
            // Show time picker
            new android.app.TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                long selectedDeadline = cal.getTimeInMillis();
                
                // Validate deadline - must be in the future and before event starts
                long now = System.currentTimeMillis();
                if (selectedDeadline <= now) {
                    Toast.makeText(this, "Deadline must be in the future", Toast.LENGTH_LONG).show();
                    return;
                }
                
                if (eventStart != null && eventStart > 0 && selectedDeadline >= eventStart) {
                    Toast.makeText(this, "Deadline must be before event start time", Toast.LENGTH_LONG).show();
                    return;
                }
                
                // Perform replacement with selected deadline
                performReplacementSwap(count, selectedDeadline);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void performReplacementSwap(int count, long deadlineToAccept) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        Toast.makeText(this, "Processing replacement selection...", Toast.LENGTH_SHORT).show();

        // First, fetch event document to get sampleSize
        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get().addOnSuccessListener(eventDoc -> {
            if (eventDoc == null || !eventDoc.exists()) {
                Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Long sampleSizeObj = eventDoc.getLong("sampleSize");
            int sampleSize = sampleSizeObj != null ? sampleSizeObj.intValue() : 0;
            String eventTitle = eventDoc.getString("title");
            
            // CRITICAL: Fetch ONLY non-selected entrants (NEVER cancelled or already selected)
            eventRef.collection("NonSelectedEntrants").get()
                .addOnSuccessListener(nonSelectedSnapshot -> {
                    if (nonSelectedSnapshot == null || nonSelectedSnapshot.isEmpty()) {
                        Toast.makeText(this, "No non-selected entrants available", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<DocumentSnapshot> nonSelectedDocs = nonSelectedSnapshot.getDocuments();
                    
                    // SAFETY CHECK: Filter out anyone who might be in CancelledEntrants or SelectedEntrants
                    // (This should never happen, but extra safety)
                    eventRef.collection("CancelledEntrants").get()
                            .addOnSuccessListener(cancelledSnapshot -> {
                                eventRef.collection("SelectedEntrants").get()
                                        .addOnSuccessListener(selectedSnapshot -> {
                                            // Create sets of IDs to exclude
                                            Set<String> cancelledIds = new HashSet<>();
                                            if (cancelledSnapshot != null) {
                                                for (DocumentSnapshot doc : cancelledSnapshot.getDocuments()) {
                                                    cancelledIds.add(doc.getId());
                                                }
                                            }
                                            Set<String> alreadySelectedIds = new HashSet<>();
                                            if (selectedSnapshot != null) {
                                                for (DocumentSnapshot doc : selectedSnapshot.getDocuments()) {
                                                    alreadySelectedIds.add(doc.getId());
                                                }
                                            }
                                            
                                            // Filter nonSelectedDocs to ONLY include truly non-selected users
                                            List<DocumentSnapshot> validNonSelectedDocs = new ArrayList<>();
                                            for (DocumentSnapshot doc : nonSelectedDocs) {
                                                String userId = doc.getId();
                                                if (!cancelledIds.contains(userId) && !alreadySelectedIds.contains(userId)) {
                                                    validNonSelectedDocs.add(doc);
                                                } else {
                                                    Log.w(TAG, "SAFETY: Filtered out user " + userId + " from replacement (in cancelled or selected)");
                                                }
                                            }
                                            
                                            if (validNonSelectedDocs.size() < count) {
                                                Toast.makeText(this, "Not enough valid non-selected entrants available", Toast.LENGTH_SHORT).show();
                                                return;
                                            }

                                            // Show dialog to let organizer manually select which entrants
                                            // For now, randomly select (organizer can manually move later if needed)
                                            List<DocumentSnapshot> selectedForReplacement = randomlySelect(validNonSelectedDocs, count);
                                            
                                            List<String> userIds = new ArrayList<>();

                                            // CRITICAL: Double-check sample size before committing batch
                                            // Re-check selectedCount right before batch commit to ensure we don't exceed sampleSize
                                            eventRef.collection("SelectedEntrants").get()
                                                    .addOnSuccessListener(finalSelectedCheck -> {
                                                        int currentSelectedCount = finalSelectedCheck != null ? finalSelectedCheck.size() : 0;
                                                        int finalSampleSize = sampleSize; // sampleSize is now in scope from outer lambda
                                                        
                                                        // Calculate how many we can actually add
                                                        int canAdd = finalSampleSize - currentSelectedCount;
                                                        
                                                        if (canAdd <= 0) {
                                                            Toast.makeText(this, 
                                                                String.format("Cannot replace: Selected entrants (%d) already equals sample size (%d).", 
                                                                    currentSelectedCount, finalSampleSize), 
                                                                Toast.LENGTH_LONG).show();
                                                            return;
                                                        }
                                                        
                                                        // Limit replacement to what we can actually add
                                                        List<DocumentSnapshot> finalSelectedForReplacement = selectedForReplacement;
                                                        List<String> finalUserIds = new ArrayList<>();
                                                        if (selectedForReplacement.size() > canAdd) {
                                                            Log.w(TAG, "Limiting replacement from " + selectedForReplacement.size() + " to " + canAdd + " to respect sample size");
                                                            finalSelectedForReplacement = selectedForReplacement.subList(0, canAdd);
                                                        }
                                                        
                                                        WriteBatch finalBatch = db.batch();
                                                        
                                                        // Move selected entrants from NonSelectedEntrants to SelectedEntrants
                                                        for (DocumentSnapshot doc : finalSelectedForReplacement) {
                                                            String userId = doc.getId();
                                                            Map<String, Object> data = doc.getData();
                                                            finalUserIds.add(userId);
                                                            
                                                            if (data != null) {
                                                                int index = finalUserIds.size();
                                                                Log.d(TAG, "Replacement: Moving user " + userId + " from NonSelected to Selected (will make " + (currentSelectedCount + index) + "/" + finalSampleSize + " selected)");
                                                                
                                                                // CRITICAL: Ensure mutual exclusivity - user can only exist in ONE collection
                                                                // Move to SelectedEntrants
                                                                finalBatch.set(eventRef.collection("SelectedEntrants").document(userId), data);
                                                                // Remove from ALL other collections
                                                                finalBatch.delete(eventRef.collection("NonSelectedEntrants").document(userId));
                                                                finalBatch.delete(eventRef.collection("WaitlistedEntrants").document(userId));
                                                                finalBatch.delete(eventRef.collection("CancelledEntrants").document(userId));
                                                                
                                                                // Create invitation for replacement
                                                                Map<String, Object> invitation = new HashMap<>();
                                                                invitation.put("eventId", eventId);
                                                                invitation.put("uid", userId);
                                                                invitation.put("entrantId", userId);
                                                                invitation.put("status", "PENDING");
                                                                invitation.put("issuedAt", System.currentTimeMillis());
                                                                invitation.put("expiresAt", deadlineToAccept);
                                                                invitation.put("isReplacement", true);
                                                                
                                                                finalBatch.set(db.collection("invitations").document(UUID.randomUUID().toString()), invitation);
                                                            }
                                                        }
                                                        
                                                        // Commit final batch
                                                        finalBatch.commit()
                                                                .addOnSuccessListener(v -> {
                                                                    Toast.makeText(this, 
                                                                            "Successfully selected " + finalUserIds.size() + " entrant" + (finalUserIds.size() == 1 ? "" : "s") + " for replacement",
                                                                            Toast.LENGTH_LONG).show();
                                                                    
                                                                    // Send notifications with deadline
                                                                    String eventTitleStr = eventTitle != null ? eventTitle : "the event";
                                                                    sendReplacementNotifications(finalUserIds, eventTitleStr, deadlineToAccept);
                                                                    
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
                                            Log.e(TAG, "Failed to load selected entrants for safety check", e);
                                            Toast.makeText(this, "Failed to perform safety check", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to load selected entrants for safety check", e);
                                Toast.makeText(this, "Failed to perform safety check", Toast.LENGTH_SHORT).show();
                            });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load cancelled entrants for safety check", e);
                        Toast.makeText(this, "Failed to perform safety check", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load non-selected entrants for replacement", e);
                    Toast.makeText(this, "Failed to load non-selected entrants", Toast.LENGTH_SHORT).show();
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to load event document", e);
                Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show();
            });
    }

    private List<DocumentSnapshot> randomlySelect(List<DocumentSnapshot> allDocs, int count) {
        List<DocumentSnapshot> shuffled = new ArrayList<>(allDocs);
        Collections.shuffle(shuffled, new Random());
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private void sendReplacementNotifications(List<String> userIds, String eventTitle, long deadlineMs) {
        if (userIds == null || userIds.isEmpty()) {
            Log.w(TAG, "sendReplacementNotifications: userIds is null or empty");
            return;
        }
        
        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "sendReplacementNotifications: eventId is null or empty");
            return;
        }

        Log.d(TAG, "sendReplacementNotifications called for " + userIds.size() + " users, eventId: " + eventId);
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
        String deadlineStr = sdf.format(new Date(deadlineMs));
        String notificationTitle = "You've been selected! 🎉";
        String notificationMessage = String.format(
                "Congratulations! You've been selected for \"%s\". Please check your invitations to accept or decline. Deadline to respond: %s",
                eventTitle, deadlineStr
        );

        // Get organizer ID from event
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc == null || !eventDoc.exists()) {
                        Log.e(TAG, "Event not found when sending replacement notifications");
                        return;
                    }
                    
                    String organizerId = eventDoc.getString("organizerId");
                    if (organizerId == null || organizerId.isEmpty()) {
                        organizerId = "system";
                    }
                    
                    // Create notification request (same format as initial selection)
                    Map<String, Object> notificationRequest = new HashMap<>();
                    notificationRequest.put("eventId", eventId);
                    notificationRequest.put("eventTitle", eventTitle != null ? eventTitle : "Event");
                    notificationRequest.put("organizerId", organizerId);
                    notificationRequest.put("userIds", userIds);
                    notificationRequest.put("groupType", "selection");
                    notificationRequest.put("message", notificationMessage);
                    notificationRequest.put("title", notificationTitle);
                    notificationRequest.put("status", "PENDING");
                    notificationRequest.put("createdAt", System.currentTimeMillis());
                    notificationRequest.put("processed", false);
                    
                    // Write to notificationRequests collection - Cloud Functions will handle sending
                    db.collection("notificationRequests").add(notificationRequest)
                            .addOnSuccessListener(docRef -> {
                                Log.d(TAG, "✓ Created replacement selection notification request for " + userIds.size() + " users");
                                Toast.makeText(OrganizerViewEntrantsActivity.this, 
                                        "Sent " + userIds.size() + " notification(s)", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to create replacement notification request", e);
                                Toast.makeText(OrganizerViewEntrantsActivity.this, 
                                        "Failed to send notifications: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load event for replacement notifications", e);
                });
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
        
        // CRITICAL: Ensure mutual exclusivity - remove from ALL collections before adding to target
        DocumentReference waitlistRef = eventRef.collection("WaitlistedEntrants").document(userId);
        DocumentReference selectedRef = eventRef.collection("SelectedEntrants").document(userId);
        DocumentReference nonSelectedRef = eventRef.collection("NonSelectedEntrants").document(userId);
        DocumentReference cancelledRef = eventRef.collection("CancelledEntrants").document(userId);

        WriteBatch batch = db.batch();
        
        if (entrantData != null) {
            batch.set(toRef, entrantData);
            // CRITICAL: Remove from ALL other collections to ensure user exists in only ONE collection
            // But don't delete from the target collection (that's where we're moving TO)
            if (!"WaitlistedEntrants".equals(toCollection)) {
                batch.delete(waitlistRef);
            }
            if (!"SelectedEntrants".equals(toCollection)) {
                batch.delete(selectedRef);
            }
            if (!"NonSelectedEntrants".equals(toCollection)) {
                batch.delete(nonSelectedRef);
            }
            if (!"CancelledEntrants".equals(toCollection)) {
                batch.delete(cancelledRef);
            }
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
                        // CRITICAL: Remove from ALL other collections to ensure user exists in only ONE collection
                        // But don't delete from the target collection (that's where we're moving TO)
                        if (!"WaitlistedEntrants".equals(toCollection)) {
                            moveBatch.delete(waitlistRef);
                        }
                        if (!"SelectedEntrants".equals(toCollection)) {
                            moveBatch.delete(selectedRef);
                        }
                        if (!"NonSelectedEntrants".equals(toCollection)) {
                            moveBatch.delete(nonSelectedRef);
                        }
                        if (!"CancelledEntrants".equals(toCollection)) {
                            moveBatch.delete(cancelledRef);
                        }
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
