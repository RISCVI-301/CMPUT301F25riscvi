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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class OrganizerWaitlistActivity extends AppCompatActivity {
    private static final String TAG = "OrganizerWaitlist";

    private TextView eventNameTextView;
    private ImageView eventPosterImageView;
    private EditText overviewEditText;
    private ListView waitlistListView;
    private ImageView backButton;
    private MaterialButton deleteEventButton;
    private MaterialButton entrantDetailsButton;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String currentEventId;
    private ArrayList<String> entrantNamesList;
    private ArrayAdapter<String> waitlistAdapter;
    private boolean isDescriptionEditing = false;
    private ImageView editDescriptionButton;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadPosterImage(uri);
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

        eventNameTextView = findViewById(R.id.event_name_title);
        overviewEditText = findViewById(R.id.overview_edittext);
        eventPosterImageView = findViewById(R.id.event_poster_placeholder);
        waitlistListView = findViewById(R.id.waitlist_listview);
        backButton = findViewById(R.id.back_button);
        entrantDetailsButton = findViewById(R.id.entrant_details_button);
        deleteEventButton = findViewById(R.id.delete_event_button);

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
        // Note: Visibility will be controlled by geolocation setting in loadEventDataFromFirestore
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

        // Set up edit description button
        editDescriptionButton = findViewById(R.id.edit_description_button);
        if (editDescriptionButton != null) {
            editDescriptionButton.setOnClickListener(v -> toggleDescriptionEditing());
        }

        // Set up focus change listener to save when clicking outside
        overviewEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && isDescriptionEditing) {
                // User clicked outside, save the description
                saveDescription();
            }
        });

        // Touch interceptor will be handled in dispatchTouchEvent override

        signInAndLoadData();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        // Intercept touches to clear EditText focus when clicking outside
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN && isDescriptionEditing && overviewEditText != null && overviewEditText.hasFocus()) {
            // Get touch coordinates relative to screen
            float x = ev.getRawX();
            float y = ev.getRawY();
            
            // Get EditText bounds on screen
            int[] editTextLocation = new int[2];
            overviewEditText.getLocationOnScreen(editTextLocation);
            int editTextLeft = editTextLocation[0];
            int editTextTop = editTextLocation[1];
            int editTextRight = editTextLeft + overviewEditText.getWidth();
            int editTextBottom = editTextTop + overviewEditText.getHeight();
            
            // Check if touch is outside EditText
            boolean isOutsideEditText = (x < editTextLeft || x > editTextRight || y < editTextTop || y > editTextBottom);
            
            if (isOutsideEditText) {
                // Check if not clicking on the edit icon
                if (editDescriptionButton != null) {
                    int[] iconLocation = new int[2];
                    editDescriptionButton.getLocationOnScreen(iconLocation);
                    int iconLeft = iconLocation[0];
                    int iconTop = iconLocation[1];
                    int iconRight = iconLeft + editDescriptionButton.getWidth();
                    int iconBottom = iconTop + editDescriptionButton.getHeight();
                    
                    // If not clicking on icon either, clear focus
                    boolean isOnIcon = (x >= iconLeft && x <= iconRight && y >= iconTop && y <= iconBottom);
                    if (!isOnIcon) {
                        overviewEditText.clearFocus();
                    }
                } else {
                    overviewEditText.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
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
            
            // Read geolocation setting
            Boolean geolocationEnabled = documentSnapshot.getBoolean("geolocation");
            boolean hasGeolocation = geolocationEnabled != null && geolocationEnabled;

            if (eventNameTextView != null) eventNameTextView.setText(title);
            if (overviewEditText != null) overviewEditText.setText(description);
            if (eventPosterImageView != null) {
                Glide.with(this)
                        .load(posterUrl)
                        .placeholder(R.drawable.rounded_panel_bg)
                        .error(R.drawable.rounded_panel_bg)
                        .into(eventPosterImageView);
            }
            
            // Hide location icon if geolocation is disabled
            ImageView locationIcon = findViewById(R.id.location_icon);
            if (locationIcon != null) {
                locationIcon.setVisibility(hasGeolocation ? android.view.View.VISIBLE : android.view.View.GONE);
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

    private void uploadPosterImage(Uri uri) {
        // Placeholder: implement upload if needed
        Toast.makeText(this, "Upload not implemented yet", Toast.LENGTH_SHORT).show();
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

    private void toggleDescriptionEditing() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isDescriptionEditing) {
            // Enable editing mode
            isDescriptionEditing = true;
            overviewEditText.setFocusable(true);
            overviewEditText.setFocusableInTouchMode(true);
            overviewEditText.setClickable(true);
            overviewEditText.requestFocus();
            // Optionally show keyboard
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(overviewEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            // Save and disable editing mode
            saveDescription();
        }
    }

    private void saveDescription() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String newDescription = overviewEditText.getText().toString();
        
        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(overviewEditText.getWindowToken(), 0);
        }

        // Update Firestore
        db.collection("events").document(currentEventId)
                .update("description", newDescription)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Description updated successfully", Toast.LENGTH_SHORT).show();
                    isDescriptionEditing = false;
                    overviewEditText.setFocusable(false);
                    overviewEditText.setFocusableInTouchMode(false);
                    overviewEditText.setClickable(false);
                    // Clear focus to prevent cursor from showing
                    overviewEditText.clearFocus();
                    Log.d(TAG, "Description updated successfully");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update description: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update description", e);
                });
    }

}


