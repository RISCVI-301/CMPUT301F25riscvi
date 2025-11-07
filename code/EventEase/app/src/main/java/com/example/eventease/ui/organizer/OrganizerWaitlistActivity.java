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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.List;
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
        deleteEventButton.setOnClickListener(v -> Toast.makeText(this, "Delete coming soon", Toast.LENGTH_SHORT).show());

        signInAndLoadData();
    }

    private void signInAndLoadData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadEventDataFromFirestore(currentEventId);
        } else {
            mAuth.signInAnonymously()
                    .addOnSuccessListener(r -> loadEventDataFromFirestore(currentEventId))
                    .addOnFailureListener(e -> Toast.makeText(this, "Auth failed", Toast.LENGTH_SHORT).show());
        }
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

    private void uploadPosterImage(Uri uri) {
        // Placeholder: implement upload if needed
        Toast.makeText(this, "Upload not implemented yet", Toast.LENGTH_SHORT).show();
    }
}


