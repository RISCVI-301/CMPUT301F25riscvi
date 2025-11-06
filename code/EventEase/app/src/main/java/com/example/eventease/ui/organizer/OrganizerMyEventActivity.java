package com.example.eventease.ui.organizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.util.AuthHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
        adapter = new OrganizerMyEventAdapter(
                this,
                eventId -> Toast.makeText(this, "Open event: " + eventId, Toast.LENGTH_SHORT).show()
        );
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
                startActivity(new Intent(this, com.example.eventease.OrganizerAccountActivity.class))
        );
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
}