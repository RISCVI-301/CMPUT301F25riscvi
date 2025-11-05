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

import com.example.eventease.OrganizerWaitlistActivity;
import com.example.eventease.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrganizerMyEventActivity extends AppCompatActivity {

    private LinearLayout emptyState;
    private RecyclerView rvMyEvents;
    private LinearLayout btnMyEvents, btnAccount;
    private View fabAdd;

    // Use your card adapter (with poster ImageView)
    private OrganizerMyEventAdapter adapter;
    private final List<Map<String, Object>> items = new ArrayList<>();

    private static final String DEV_ORGANIZER_ID = "organizer_test_1";

    private FirebaseFirestore db;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organizer_my_events);

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
                            .setPersistenceEnabled(false) // DEV ONLY: show console deletes instantly
                            .build()
            );
        } catch (Throwable ignored) { }

        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, OrganizerCreateEventActivity.class))
        );
        btnMyEvents.setOnClickListener(v -> refreshFromServer());
        btnAccount.setOnClickListener(v ->
                startActivity(new Intent(this, com.example.eventease.OrganizerAccountActivity.class))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshFromServer();
        attachRealtime();
    }

    @Override
    protected void onStop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onStop();
    }

    private Query baseQuery() {
        // If you ever need to see ALL events regardless of owner, temporarily remove whereEqualTo.
        return db.collection("events")
                .whereEqualTo("organizerId", DEV_ORGANIZER_ID)
                .orderBy("createdAt", Query.Direction.DESCENDING);
    }

    /** Force a SERVER read so app matches console deletes/edits immediately. */
    private void refreshFromServer() {
        baseQuery().get(Source.SERVER)
                .addOnSuccessListener(this::applySnapshot)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Server refresh failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    /** Realtime updates for future creates/edits/deletes. */
    private void attachRealtime() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        registration = baseQuery().addSnapshotListener(
                MetadataChanges.INCLUDE,
                (snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Listen failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshots != null) applySnapshot(snapshots);
                }
        );
    }

    /** Convert Firestore docs -> maps expected by OrganizerMyEventAdapter. */
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

    // ---------- mapping helpers (tolerant to missing/typed fields) ----------
    private Map<String, Object> toAdapterMap(DocumentSnapshot d) {
        Map<String, Object> m = new HashMap<>();

        String id = getStringOr(d.getString("id"), d.getId());
        String title = getStringOr(d.getString("title"), "Untitled");

        // IMPORTANT: keep this EXACT key — your adapter reads "posterUrl"
        String posterUrl = d.getString("posterUrl"); // must be https URL or gs:// handled by Glide (ok if Storage public-read)
        String location = d.getString("location");

        long regStart = coerceLong(d.get("registrationStart"));
        long regEnd   = coerceLong(d.get("registrationEnd"));
        int capacity  = coerceInt(d.get("capacity"), -1);

        m.put("id", id);
        m.put("title", title);
        m.put("posterUrl", posterUrl);   // <-- used by Glide in your adapter
        m.put("location", location);
        m.put("registrationStart", regStart);
        m.put("registrationEnd", regEnd);
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