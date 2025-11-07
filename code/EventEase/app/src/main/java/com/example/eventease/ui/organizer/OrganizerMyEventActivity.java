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

import com.example.eventease.ui.organizer.OrganizerWaitlistActivity;
import com.example.eventease.R;
import com.example.eventease.util.AuthHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrganizerMyEventActivity extends AppCompatActivity {

    private LinearLayout emptyState;
    private RecyclerView rvMyEvents;
    private LinearLayout btnMyEvents, btnAccount;
    private View fabAdd;

    private OrganizerMyEventAdapter adapter;
    private final List<Map<String, Object>> items = new ArrayList<>();

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
                            .setPersistenceEnabled(false)
                            .build()
            );
        } catch (Throwable ignored) { }

        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, OrganizerCreateEventActivity.class))
        );
        btnMyEvents.setOnClickListener(v -> refreshFromServer());
        btnAccount.setOnClickListener(v ->
                startActivity(new Intent(this, OrganizerAccountActivity.class))
        );
        
        seedSampleParticipants();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        if (!AuthHelper.isAuthenticated()) {
            Toast.makeText(this, "Please sign in to view your events", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
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
        String organizerId = AuthHelper.getCurrentOrganizerIdOrNull();
        if (organizerId == null) {
            return db.collection("events").whereEqualTo("organizerId", "__invalid__");
        }
        return db.collection("events")
                .whereEqualTo("organizerId", organizerId);
    }

    private void refreshFromServer() {
        baseQuery().get(Source.SERVER)
                .addOnSuccessListener(this::applySnapshot)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Server refresh failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

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

        long regStart = coerceLong(d.get("registrationStart"));
        long regEnd   = coerceLong(d.get("registrationEnd"));
        int capacity  = coerceInt(d.get("capacity"), -1);

        m.put("id", id);
        m.put("title", title);
        m.put("posterUrl", posterUrl);
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

    /**
     * Convenience helper you can call while developing to populate the event buckets
     * for the sample event (ID: 699bb06d-ea19-4edf-8238-ba2103fa6509) with all nine
     * demo users (waitlist) and three entrants in each bucket (selected / non-selected / cancelled).
     * Invoke manually from onCreate(), a debug button, or temporarily when you need to reseed the data.
     */
    private void seedSampleParticipants() {
        final String eventId = "c494766e-ddc8-4ef5-83a3-5fce4fbbac5f";

        Map<String, ParticipantSeed> all = new LinkedHashMap<>();
        all.put("c3hY3rcQwtPpKwJfEylBfwCar2", new ParticipantSeed("Caramel Verma", "carver123@ualberta.ca", "123123123"));
        all.put("nF9GwuUQdahEPP4oJsoPe0smmn1", new ParticipantSeed("dukh antam", "dukhaitamaslay@gmail.com", "1234567890"));
        all.put("LTrhOeKa3NnFa7SyHAtC9z2ut2", new ParticipantSeed("Baby Groot", "lamrgroot@gmail.com", "1234567890"));
        all.put("JQDhJDOqLqPmDTv0QOqvBF9v2", new ParticipantSeed("Hair Gobi", "tharigobbrocks@gmail.com", "1234567890"));
        all.put("D3W8BKiP40FHyLhhBXtK0mwdjH2", new ParticipantSeed("sam ver", "samver@gmail.com", "1234567899"));
        all.put("qsF0hQGYCpYEOGUuhNd4vFLQp1", new ParticipantSeed("Sanika Verma", "sanika123@gmail.com", "123456789"));
        all.put("LvaLG92OU0UuasiyS0ZfQSvpudym2", new ParticipantSeed("maan naam", "1manog@gmail.com", "7805551111"));
        all.put("VVopE1dwcH54jS7na9cOsDiShL2", new ParticipantSeed("gainda monke", "two@gmail.com", "696969"));
        all.put("4g0z3tuuAHc6y9miUh9hCR83dWn2", new ParticipantSeed("kyo bhang", "1kya@gmail.com", "12345678"));

        List<String> selected = Arrays.asList(
                "c3hY3rcQwtPpKwJfEylBfwCar2",
                "nF9GwuUQdahEPP4oJsoPe0smmn1",
                "LvaLG92OU0UuasiyS0ZfQSvpudym2");

        List<String> nonSelected = Arrays.asList(
                "LTrhOeKa3NnFa7SyHAtC9z2ut2",
                "JQDhJDOqLqPmDTv0QOqvBF9v2",
                "VVopE1dwcH54jS7na9cOsDiShL2");

        List<String> cancelled = Arrays.asList(
                "D3W8BKiP40FHyLhhBXtK0mwdjH2",
                "qsF0hQGYCpYEOGUuhNd4vFLQp1",
                "4g0z3tuuAHc6y9miUh9hCR83dWn2");

        WriteBatch batch = db.batch();
        DocumentReference eventRef = db.collection("events").document(eventId);

        for (Map.Entry<String, ParticipantSeed> entry : all.entrySet()) {
            String userId = entry.getKey();
            ParticipantSeed info = entry.getValue();
            Map<String, Object> payload = info.toMap();
            payload.put("userId", userId);

            batch.set(eventRef.collection("WaitlistedEntrants").document(userId), payload, SetOptions.merge());
            if (selected.contains(userId)) {
                batch.set(eventRef.collection("SelectedEntrants").document(userId), payload, SetOptions.merge());
            }
            if (nonSelected.contains(userId)) {
                batch.set(eventRef.collection("NonSelectedEntrants").document(userId), payload, SetOptions.merge());
            }
            if (cancelled.contains(userId)) {
                batch.set(eventRef.collection("CancelledEntrants").document(userId), payload, SetOptions.merge());
            }
        }

        batch.commit()
                .addOnSuccessListener(unused -> Log.d("OrganizerMyEventActivity", "Seeded sample participants for " + eventId))
                .addOnFailureListener(e -> Log.e("OrganizerMyEventActivity", "Failed to seed participants", e));
    }

    private static class ParticipantSeed {
        final String name;
        final String email;
        final String phone;

        ParticipantSeed(String name, String email, String phone) {
            this.name = name;
            this.email = email;
            this.phone = phone;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("name", name);
            m.put("email", email);
            m.put("phoneNumber", phone);
            return m;
        }
    }
}