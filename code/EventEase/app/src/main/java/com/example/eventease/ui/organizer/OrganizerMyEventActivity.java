package com.example.eventease.ui.organizer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.SetOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrganizerMyEventActivity extends AppCompatActivity {

    public static final String EXTRA_ORGANIZER_ID = "extra_organizer_id";

    private LinearLayout emptyState;
    private RecyclerView rvMyEvents;
    private LinearLayout btnMyEvents, btnAccount;
    private View fabAdd;
    private String organizerId;
    private boolean isResolvingOrganizerId;

    private OrganizerMyEventAdapter adapter;
    private final List<Map<String, Object>> items = new ArrayList<>();

    private FirebaseFirestore db;
    private ListenerRegistration registration;
    private ListenerRegistration legacyRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organizer_my_events);

        organizerId = getIntent().getStringExtra(EXTRA_ORGANIZER_ID);

        emptyState  = findViewById(R.id.emptyState);
        rvMyEvents  = findViewById(R.id.rvMyEvents);
        btnMyEvents = findViewById(R.id.btnMyEvents);
        btnAccount  = findViewById(R.id.btnAccount);
        fabAdd      = findViewById(R.id.fabAdd);

        rvMyEvents.setLayoutManager(new LinearLayoutManager(this));

        adapter = new OrganizerMyEventAdapter(this, eventId -> {
            // This is the code that runs when an event card is clicked.
            Log.d("OrganizerMyEventActivity", "Event clicked. Opening details for ID: " + eventId);

            // Create an Intent to open your details screen.
            Intent intent = new Intent(OrganizerMyEventActivity.this, OrganizerWaitlistActivity.class);

            // Pass the clicked event's ID to your screen.
            intent.putExtra("eventId", eventId);
            
            // Launch your screen.
            startActivity(intent);
        });

        rvMyEvents.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        try {
            db.setFirestoreSettings(
                    new FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(false)
                            .build()
            );
        } catch (Throwable ignored) { }

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizerCreateEventActivity.class);
            intent.putExtra(EXTRA_ORGANIZER_ID, organizerId);
            startActivity(intent);
        });
        btnMyEvents.setOnClickListener(v -> refreshFromServer());
        btnAccount.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizerAccountActivity.class);
            intent.putExtra(EXTRA_ORGANIZER_ID, organizerId);
            startActivity(intent);
        });
    }

    private void ensureOrganizerId(Runnable onReady) {
        if (organizerId != null && !organizerId.trim().isEmpty()) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (isResolvingOrganizerId) {
            return;
        }
        // Get device ID as organizer ID
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(this);
        String deviceId = authManager.getUid();
        
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }
        
        isResolvingOrganizerId = true;
        organizerId = deviceId; // Use device ID directly
        
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(deviceId)
                .get()
                .addOnSuccessListener(doc -> {
                    isResolvingOrganizerId = false;
                    if (organizerId == null || organizerId.trim().isEmpty()) {
                        Toast.makeText(this, "Organizer ID not set for this account", Toast.LENGTH_LONG).show();
                    } else if (onReady != null) {
                        onReady.run();
                    }
                })
                .addOnFailureListener(e -> {
                    isResolvingOrganizerId = false;
                    Toast.makeText(this, "Failed to load organizer profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Device auth - always have profile by this point
        com.example.eventease.auth.DeviceAuthManager authManager = 
            new com.example.eventease.auth.DeviceAuthManager(this);
        if (!authManager.hasCachedProfile()) {
            Toast.makeText(this, "Please complete your profile setup", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ensureOrganizerId(() -> {
            refreshFromServer();
            attachRealtime();
        });
    }

    @Override
    protected void onStop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        if (legacyRegistration != null) {
            legacyRegistration.remove();
            legacyRegistration = null;
        }
        super.onStop();
    }

    private Query organizerIdQuery() {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            return db.collection("events").whereEqualTo("organizerId", "__organizer_not_set__");
        }
        return db.collection("events")
                .whereEqualTo("organizerId", organizerId);
    }

    private Query legacyOrganizerIdQuery() {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            return db.collection("events").whereEqualTo("organizerID", "__organizer_not_set__");
        }
        return db.collection("events")
                .whereEqualTo("organizerID", organizerId);
    }

    private void refreshFromServer() {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            Toast.makeText(this, "Organizer profile not configured yet", Toast.LENGTH_LONG).show();
            return;
        }
        Task<QuerySnapshot> primaryTask = organizerIdQuery().get(Source.SERVER);
        Task<QuerySnapshot> legacyTask = legacyOrganizerIdQuery().get(Source.SERVER);

        Tasks.whenAllComplete(primaryTask, legacyTask)
                .addOnCompleteListener(all -> {
                    QuerySnapshot primarySnap = primaryTask.isSuccessful() ? primaryTask.getResult() : null;
                    QuerySnapshot legacySnap = legacyTask.isSuccessful() ? legacyTask.getResult() : null;

                    if (!primaryTask.isSuccessful() && primaryTask.getException() != null) {
                        Log.w("OrganizerMyEventActivity", "Primary query failed", primaryTask.getException());
                    }
                    if (!legacyTask.isSuccessful() && legacyTask.getException() != null) {
                        Log.w("OrganizerMyEventActivity", "Legacy query failed", legacyTask.getException());
                    }

                    int primaryCount = primarySnap != null ? primarySnap.size() : 0;
                    int legacyCount = legacySnap != null ? legacySnap.size() : 0;
                    Log.d("OrganizerMyEventActivity", "Initial load counts primary=" + primaryCount + " legacy=" + legacyCount);

                    if ((primarySnap == null || primarySnap.isEmpty()) && (legacySnap == null || legacySnap.isEmpty())) {
                        loadAllEventsFallback();
                        return;
                    }

                    // For initial load, clear items first to ensure clean state
                    items.clear();
                    mergeSnapshotsAndDisplayFromQuery(primarySnap, legacySnap);
                    backfillLegacy(legacySnap);
                });
    }

    private void loadAllEventsFallback() {
        Log.d("OrganizerMyEventActivity", "Fallback: loading all events for filtering");
        db.collection("events")
                .get(Source.SERVER)
                .addOnSuccessListener(all -> {
                    List<DocumentSnapshot> matches = new ArrayList<>();
                    for (DocumentSnapshot doc : all) {
                        String primary = doc.getString("organizerId");
                        String legacy = doc.getString("organizerID");
                        if ((primary != null && primary.equals(organizerId)) ||
                                (legacy != null && legacy.equals(organizerId))) {
                            matches.add(doc);
                        }
                    }
                    Log.d("OrganizerMyEventActivity", "Fallback matched=" + matches.size());
                    mergeSnapshotsAndDisplay(matches, null);
                    backfillLegacyFromDocs(matches);
                })
                .addOnFailureListener(e -> Log.e("OrganizerMyEventActivity", "Fallback load failed", e));
    }

    private void attachRealtime() {
        if (organizerId == null || organizerId.trim().isEmpty()) {
            return;
        }
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        if (legacyRegistration != null) {
            legacyRegistration.remove();
            legacyRegistration = null;
        }
        registration = organizerIdQuery().addSnapshotListener(
                MetadataChanges.INCLUDE,
                (snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Listen failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshots != null) mergeSnapshotsAndDisplayFromQuery(snapshots, null);
                    else loadAllEventsFallback();
                }
        );

        legacyRegistration = legacyOrganizerIdQuery().addSnapshotListener(
                MetadataChanges.INCLUDE,
                (snapshots, e) -> {
                    if (e != null) {
                        Log.w("OrganizerMyEventActivity", "Legacy listen failed", e);
                        return;
                    }
                    if (snapshots != null && !snapshots.isEmpty()) {
                        mergeSnapshotsAndDisplayFromQuery(null, snapshots);
                        backfillLegacy(snapshots);
                    }
                }
        );
    }

    /**
     * Handles QuerySnapshot objects and processes DocumentChange events to properly handle deletions.
     * This method processes ADDED, MODIFIED, and REMOVED changes from Firestore snapshots.
     * For initial loads, items should be cleared before calling this method.
     */
    private void mergeSnapshotsAndDisplayFromQuery(@Nullable QuerySnapshot primary,
                                                    @Nullable QuerySnapshot legacy) {
        // Start with current items to preserve state (for incremental updates)
        // If items is empty (initial load), this will start empty
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String id = (String) item.get("id");
            if (id != null) {
                merged.put(id, item);
            }
        }

        // Process primary snapshot changes
        if (primary != null) {
            List<DocumentChange> changes = primary.getDocumentChanges();
            if (!changes.isEmpty()) {
                // Process changes from snapshot listener
                for (DocumentChange change : changes) {
                    String docId = change.getDocument().getId();
                    DocumentChange.Type changeType = change.getType();
                    
                    if (changeType == DocumentChange.Type.REMOVED) {
                        // Remove deleted event from the map
                        merged.remove(docId);
                        Log.d("OrganizerMyEventActivity", "Removed event: " + docId);
                    } else if (changeType == DocumentChange.Type.ADDED || changeType == DocumentChange.Type.MODIFIED) {
                        // Add or update event
                        Map<String, Object> m = toAdapterMap(change.getDocument());
                        if (m != null) {
                            merged.put(docId, m);
                            Log.d("OrganizerMyEventActivity", "Added/Modified event: " + docId);
                        }
                    }
                }
            } else {
                // Fallback: if no changes (e.g., from .get() call), process all documents as ADDED
                for (DocumentSnapshot doc : primary) {
                    String docId = doc.getId();
                    Map<String, Object> m = toAdapterMap(doc);
                    if (m != null) {
                        merged.put(docId, m);
                        Log.d("OrganizerMyEventActivity", "Added event (fallback): " + docId);
                    }
                }
            }
        }

        // Process legacy snapshot changes
        if (legacy != null) {
            List<DocumentChange> changes = legacy.getDocumentChanges();
            if (!changes.isEmpty()) {
                // Process changes from snapshot listener
                for (DocumentChange change : changes) {
                    String docId = change.getDocument().getId();
                    DocumentChange.Type changeType = change.getType();
                    
                    if (changeType == DocumentChange.Type.REMOVED) {
                        // Remove deleted event from the map
                        merged.remove(docId);
                        Log.d("OrganizerMyEventActivity", "Removed legacy event: " + docId);
                    } else if (changeType == DocumentChange.Type.ADDED || changeType == DocumentChange.Type.MODIFIED) {
                        // Add or update event
                        Map<String, Object> m = toAdapterMap(change.getDocument());
                        if (m != null) {
                            merged.put(docId, m);
                            Log.d("OrganizerMyEventActivity", "Added/Modified legacy event: " + docId);
                        }
                    }
                }
            } else {
                // Fallback: if no changes (e.g., from .get() call), process all documents as ADDED
                for (DocumentSnapshot doc : legacy) {
                    String docId = doc.getId();
                    Map<String, Object> m = toAdapterMap(doc);
                    if (m != null) {
                        merged.put(docId, m);
                        Log.d("OrganizerMyEventActivity", "Added legacy event (fallback): " + docId);
                    }
                }
            }
        }

        // Update the UI with the merged results
        items.clear();
        items.addAll(merged.values());
        adapter.setData(items);
        toggleEmpty(!items.isEmpty());
    }

    private void mergeSnapshotsAndDisplay(@Nullable Iterable<? extends DocumentSnapshot> primary,
                                          @Nullable Iterable<? extends DocumentSnapshot> legacy) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        int primaryCount = 0;
        int legacyCount = 0;

        if (primary != null) {
            for (DocumentSnapshot d : primary) {
                primaryCount++;
                Log.d("OrganizerMyEventActivity", "Primary event doc=" + d.getId());
                Map<String, Object> m = toAdapterMap(d);
                if (m != null) merged.put(d.getId(), m);
            }
        }

        if (legacy != null) {
            for (DocumentSnapshot d : legacy) {
                legacyCount++;
                Log.d("OrganizerMyEventActivity", "Legacy event doc=" + d.getId());
                Map<String, Object> m = toAdapterMap(d);
                if (m != null) merged.put(d.getId(), m);
            }
        }

        Log.d("OrganizerMyEventActivity", "Merging events: primary=" + primaryCount + " legacy=" + legacyCount);
        items.clear();
        items.addAll(merged.values());
        adapter.setData(items);
        toggleEmpty(!items.isEmpty());
    }

    private void backfillLegacy(@Nullable QuerySnapshot legacySnap) {
        if (legacySnap == null || legacySnap.isEmpty()) return;
        backfillLegacyFromDocs(legacySnap.getDocuments());
    }

    private void backfillLegacyFromDocs(@Nullable Iterable<DocumentSnapshot> docs) {
        if (docs == null) return;
        for (DocumentSnapshot doc : docs) {
            String existing = doc.getString("organizerId");
            if (organizerId != null && (existing == null || existing.trim().isEmpty())) {
                doc.getReference().set(Collections.singletonMap("organizerId", organizerId), SetOptions.merge());
            }
        }
    }

    private void applySnapshot(QuerySnapshot snapshots) {
        items.clear();
        for (QueryDocumentSnapshot d : snapshots) {
            Map<String, Object> m = toAdapterMap(d);
            if (m != null) items.add(m);
        }
        adapter.setData(items);
        toggleEmpty(!items.isEmpty());
    }

    private void toggleEmpty(boolean hasItems) {
        emptyState.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        rvMyEvents.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    private Map<String, Object> toAdapterMap(DocumentSnapshot d) {
        Map<String, Object> m = new HashMap<>();

        String id = getStringOr(d.getString("id"), d.getId());
        String title = getStringOr(d.getString("title"), "Untitled");
        String posterUrl = d.getString("posterUrl");
        String location = d.getString("location");
        @SuppressWarnings("unchecked")
        List<String> interests = (List<String>) d.get("interests");

        long regStart = coerceLong(d.get("registrationStart"));
        long regEnd   = coerceLong(d.get("registrationEnd"));
        long deadline = coerceLong(d.get("deadlineEpochMs"));
        int capacity  = coerceInt(d.get("capacity"), -1);

        m.put("id", id);
        m.put("title", title);
        m.put("posterUrl", posterUrl);
        m.put("location", location);
        m.put("interests", interests != null ? interests : new ArrayList<String>());
        m.put("registrationStart", regStart);
        m.put("registrationEnd", regEnd);
        m.put("deadlineEpochMs", deadline);
        m.put("capacity", capacity);

        return m;
    }

    private static String getStringOr(String v, String def) {
        return (v != null && !v.trim().isEmpty()) ? v : def;
    }

    private static long coerceLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong((String) v); } catch (Exception ignored) {}
        }
        return 0L;
    }

    private static int coerceInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return def;
    }

}