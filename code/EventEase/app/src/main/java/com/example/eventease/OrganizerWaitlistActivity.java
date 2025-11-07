package com.example.eventease;//package com.example.eventease;


import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth; // NEW: Import Firebase Auth
import com.google.firebase.auth.FirebaseUser; // NEW: Import Firebase User
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * An activity for organizers to view and manage the details of a specific event.
 * This screen displays the event's name, description, and poster. It also shows a list of
 * entrants who are on the waitlist. Organizers can perform actions such as updating the
 * event poster and deleting the event entirely.
 *
 * This activity requires an `eventId` to be passed via an Intent to function correctly.
 */
public class OrganizerWaitlistActivity extends AppCompatActivity {

    private static final String TAG = "OrganizerWaitlist";

    // --- Views ---
    private TextView eventNameTextView;
    private ImageView eventPosterImageView;
    private TextInputEditText overviewEditText;
    private ListView waitlistListView;
    private ImageView backButton;
    private MaterialButton deleteEventButton;
    private MaterialButton entrantDetailsButton;

    // --- Firebase & Data ---
    private FirebaseAuth mAuth;       // NEW: Firebase Auth instance
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String currentEventId;
    private ArrayList<String> entrantNamesList;
    private ArrayAdapter<String> waitlistAdapter;

    /**
     * An ActivityResultLauncher that handles the result of picking an image from the device's gallery.
     */
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadPosterImage(uri);
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                }
            });
    /**
     * Called when the activity is first created. This is where you should do all of your normal
     * static set up: create views, bind data to lists, etc.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     *                           shut down then this Bundle contains the data it most recently
     *                           supplied in onSaveInstanceState(Bundle). Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_waitlist);

        // --- Initialize Firebase ---
        mAuth = FirebaseAuth.getInstance(); // NEW: Initialize Auth
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // --- Connect Views ---
        eventNameTextView = findViewById(R.id.event_name_title);
        overviewEditText = findViewById(R.id.overview_edittext);
        eventPosterImageView = findViewById(R.id.event_poster_placeholder);
        waitlistListView = findViewById(R.id.waitlist_listview);
        backButton = findViewById(R.id.back_button);
        entrantDetailsButton = findViewById(R.id.entrant_details_button);
        deleteEventButton = findViewById(R.id.delete_event_button);
        // --- Get Event ID ---
        currentEventId = getIntent().getStringExtra("eventId");

        // --- Set up ListView ---
        entrantNamesList = new ArrayList<>();
        waitlistAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entrantNamesList);
        waitlistListView.setAdapter(waitlistAdapter);

        // --- Set up Click Listeners ---
        backButton.setOnClickListener(v -> finish());
        eventPosterImageView.setOnClickListener(v -> {
            if (currentEventId != null && !currentEventId.isEmpty()) {
                pickImageLauncher.launch("image/*");
            } else {
                Toast.makeText(this, "Cannot change poster without a valid Event ID", Toast.LENGTH_LONG).show();
            }
        });
        deleteEventButton.setOnClickListener(v -> {
            // Show a confirmation dialog before deleting
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Event")
                    .setMessage("Are you sure you want to permanently delete this event? This action cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        // User clicked "Delete", so proceed with deletion
                        deleteEventFromFirestore();
                    })
                    .setNegativeButton("Cancel", null) // User clicked "Cancel", do nothing
                    .show();
        });
        // --- Start the Auth and Data Loading Process ---
        signInAndLoadData();
    }

    /**
     * Handles the authentication state and triggers the data loading process.
     * If a user is already signed in, it proceeds to load data. If not, it attempts a
     * temporary sign-in with test credentials for development purposes.
     */
    private void signInAndLoadData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // If a user is already signed in from a previous screen, use them.
        if (currentUser != null) {
            Log.d(TAG, "Already signed in as: " + currentUser.getUid());
            // Proceed to load data
            if (currentEventId != null && !currentEventId.isEmpty()) {
                loadEventDataFromFirestore(currentEventId);
            } else {
                handleMissingEventId();
            }
        } else {
            // If no user is signed in, perform a temporary login for testing.
            // **IMPORTANT**: The email/password must exist in your Firebase Auth console.
            String testEmail = "organizer@test.com"; // A test email
            String testPassword = "password123";    // A test password

            Log.d(TAG, "No user signed in. Attempting temporary sign in...");
            mAuth.signInWithEmailAndPassword(testEmail, testPassword)
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "Temporary sign in SUCCESSFUL. UID: " + authResult.getUser().getUid());
                        // Now that we are authenticated, load the data.
                        if (currentEventId != null && !currentEventId.isEmpty()) {
                            loadEventDataFromFirestore(currentEventId);
                        } else {
                            handleMissingEventId();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Temporary sign in FAILED.", e);
                        Toast.makeText(this, "Test Login Failed. Check credentials and security rules.", Toast.LENGTH_LONG).show();
                        eventNameTextView.setText("Authentication Failed");
                    });
        }
    }
    /**
     * Handles the critical error case where the activity is started without an Event ID.
     * It logs the error and updates the UI to inform the user.
     */
    private void handleMissingEventId() {
        Log.e(TAG, "CRITICAL: Event ID is null or empty. Cannot load data.");
        eventNameTextView.setText("Error: No Event ID Provided");
    }

    /**
     * Fetches the event details and waitlist from Firestore using the provided event ID.
     * Updates the UI with the event's title, description, and poster image. It also
     * populates the waitlist ListView.
     *
     * @param eventId The unique identifier for the event to be loaded.
     */
    private void loadEventDataFromFirestore(String eventId) {
        // This method remains the same, but it will now succeed because we are logged in.
        db.collection("events").document(eventId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String title = documentSnapshot.getString("title");
                String description = documentSnapshot.getString("description");
                String posterUrl = documentSnapshot.getString("posterUrl");

                eventNameTextView.setText(title);
                overviewEditText.setText(description);

                Glide.with(this)
                        .load(posterUrl)
                        .placeholder(R.drawable.rounded_panel_bg)
                        .error(R.drawable.rounded_panel_bg)
                        .into(eventPosterImageView);

                Log.d(TAG, "SUCCESS: Event details loaded for: " + title);
                entrantNamesList.clear(); // Clear old data

                Object waitlistObject = documentSnapshot.get("waitlist");
                if (waitlistObject instanceof ArrayList) {
                    ArrayList<String> waitlistUserIDs = (ArrayList<String>) waitlistObject;

                    if (waitlistUserIDs.isEmpty()) {
                        Log.d(TAG, "INFO: Waitlist array is empty. No names to display.");
                        waitlistAdapter.notifyDataSetChanged(); // Refresh UI to show empty list
                        return; // Stop here if there are no users to fetch.
                    }

                    Log.d(TAG, "Waitlist contains " + waitlistUserIDs.size() + " user IDs. Fetching names...");

                    // For each user ID, fetch the user's name from the 'users' collection.
                    for (String userId : waitlistUserIDs) {
                        db.collection("users").document(userId).get()
                                .addOnSuccessListener(userDocument -> {
                                    if (userDocument.exists()) {
                                        String name = userDocument.getString("name"); // Use "name" or "fullName"
                                        if (name != null) {
                                            entrantNamesList.add(name);
                                            Log.d(TAG, "Fetched name: " + name);
                                        } else {
                                            Log.w(TAG, "User document " + userId + " is missing a 'name' field.");
                                            entrantNamesList.add("Unnamed User (" + userId.substring(0, 5) + ")");
                                        }
                                    } else {
                                        Log.w(TAG, "User document not found for ID: " + userId);
                                        entrantNamesList.add("Unknown User");
                                    }
                                    // Update the adapter EVERY time a name is added.
                                    waitlistAdapter.notifyDataSetChanged();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to fetch user document for ID: " + userId, e);
                                    entrantNamesList.add("Error Fetching User");
                                    waitlistAdapter.notifyDataSetChanged();
                                });
                    }
                }
            } else {
                Log.w(TAG, "WARNING: Event document with ID " + eventId + " not found.");
                eventNameTextView.setText("Event Not Found");
            }
        }).addOnFailureListener(e -> {
            // This failure listener is what you are seeing now.
            Log.e(TAG, "FAILURE: Error fetching event details. This could be a permissions issue.", e);
            eventNameTextView.setText("Permission Denied");
        });

        // Fetch waitlist...
        db.collection("events").document(eventId).collection("WaitlistedEntrants").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                entrantNamesList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    String name = document.getString("name");
                    if (name != null) entrantNamesList.add(name);
                }
                waitlistAdapter.notifyDataSetChanged();
                Log.d(TAG, "SUCCESS: Waitlist loaded.");
            } else {
                Log.e(TAG, "FAILURE: Error fetching waitlist: ", task.getException());
            }
        });
    }
    /**
     * Uploads a new image to Firebase Storage to serve as the event poster.
     * Upon successful upload, it retrieves the download URL and updates the corresponding
     * event document in Firestore.
     *
     * @param imageUri The local URI of the image file to be uploaded.
     */
    private void uploadPosterImage(Uri imageUri) {
        // This method remains the same
        Toast.makeText(this, "Uploading new poster...", Toast.LENGTH_SHORT).show();
        StorageReference posterRef = storage.getReference().child("posters/" + currentEventId + ".jpg");

        posterRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    posterRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        String newPosterUrl = downloadUri.toString();
                        Map<String, Object> update = new HashMap<>();
                        update.put("posterUrl", newPosterUrl);

                        db.collection("events").document(currentEventId)
                                .update(update)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Poster updated successfully!", Toast.LENGTH_SHORT).show();
                                    Glide.with(this).load(newPosterUrl).into(eventPosterImageView);
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save new poster URL.", Toast.LENGTH_LONG).show());
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
    /**
     * Deletes the current event document from the Firestore "events" collection.
     * If successful, it displays a confirmation toast and finishes the activity.
     */
    private void deleteEventFromFirestore() {
        if (currentEventId == null || currentEventId.isEmpty()) {
            Toast.makeText(this, "Cannot delete, event ID is missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send the delete request to Firestore
        db.collection("events").document(currentEventId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Success!
                    Log.d(TAG, "Event successfully deleted from Firestore!");
                    Toast.makeText(this, "Event deleted.", Toast.LENGTH_SHORT).show();
                    // Close this activity and go back to the previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Failure!
                    Log.e(TAG, "Error deleting event from Firestore", e);
                    Toast.makeText(this, "Failed to delete event. Check logs.", Toast.LENGTH_SHORT).show();
                });
    }
}
