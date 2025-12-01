package com.example.eventease.ui.organizer;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.app.Dialog;

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
    
    // Bottom nav icon and label views
    private ImageView navIconMyEvents, navIconCreate, navIconAccount;
    private TextView navLabelMyEvents, navLabelCreate, navLabelAccount;

    private OrganizerMyEventAdapter adapter;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private final List<Map<String, Object>> allItems = new ArrayList<>(); // Store all items for filtering

    private FirebaseFirestore db;
    private ListenerRegistration registration;
    private ListenerRegistration legacyRegistration;

    // Search and filter fields
    private EditText searchInput;
    private String searchQuery = "";
    private String locationFilter = "";

    private enum DateFilterOption {
        ANY_DATE,
        TODAY,
        THIS_MONTH,
        CUSTOM
    }

    private DateFilterOption activeDateFilter = DateFilterOption.ANY_DATE;
    private long customDateFilterStartMs = 0L;
    private final SimpleDateFormat filterDateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

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
        
        // Get bottom nav icon and label views
        navIconMyEvents = findViewById(R.id.nav_icon_my_events);
        navIconCreate = findViewById(R.id.nav_icon_create);
        navIconAccount = findViewById(R.id.nav_icon_account);
        navLabelMyEvents = findViewById(R.id.nav_label_my_events);
        navLabelCreate = findViewById(R.id.nav_label_create);
        navLabelAccount = findViewById(R.id.nav_label_account);

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
            overridePendingTransition(0, 0); // Remove slide animation
        });
        btnMyEvents.setOnClickListener(v -> refreshFromServer());
        btnAccount.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizerAccountActivity.class);
            intent.putExtra(EXTRA_ORGANIZER_ID, organizerId);
            startActivity(intent);
            overridePendingTransition(0, 0); // Remove slide animation
            finish(); // Close this activity to show new one instantly
        });
        
        // Set icon states - My Events is selected (light), others are dark
        updateNavigationSelection("myEvents");

        // Setup search input
        searchInput = findViewById(R.id.etMyEventsSearch);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    updateSearchQuery(s != null ? s.toString() : "");
                }
            });
        }

        // Setup filter button
        View filterButton = findViewById(R.id.myEvents_filter);
        if (filterButton != null) {
            filterButton.setOnClickListener(v -> showFilterDialog());
        }
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
        allItems.clear();
        allItems.addAll(items); // Store all items for filtering
        applyFiltersAndUpdateUI();
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
        allItems.clear();
        allItems.addAll(items); // Store all items for filtering
        applyFiltersAndUpdateUI();
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
        allItems.clear();
        allItems.addAll(items); // Store all items for filtering
        applyFiltersAndUpdateUI();
    }

    private void applyFiltersAndUpdateUI() {
        if (adapter == null) return;

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> event : allItems) {
            if (passesDateFilter(event) && passesLocationFilter(event) && passesSearchFilter(event)) {
                filtered.add(event);
            }
        }

        items.clear();
        items.addAll(filtered);
        List<OrganizerMyEventAdapter.EventListItem> sectionedList = createSectionedList(items);
        adapter.setSectionedData(sectionedList);
        toggleEmpty(!items.isEmpty());
    }

    private boolean passesLocationFilter(Map<String, Object> event) {
        if (TextUtils.isEmpty(locationFilter)) {
            return true;
        }
        String eventLocation = asString(event.get("location"));
        if (TextUtils.isEmpty(eventLocation)) {
            return false;
        }
        return eventLocation.toLowerCase(Locale.getDefault())
                .contains(locationFilter.toLowerCase(Locale.getDefault()));
    }

    private boolean passesSearchFilter(Map<String, Object> event) {
        if (TextUtils.isEmpty(searchQuery)) {
            return true;
        }
        String queryLower = searchQuery.toLowerCase(Locale.getDefault());
        String title = asString(event.get("title"));
        if (!TextUtils.isEmpty(title) && title.toLowerCase(Locale.getDefault()).contains(queryLower)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        List<String> interests = (List<String>) event.get("interests");
        if (interests == null || interests.isEmpty()) {
            return false;
        }
        for (String interest : interests) {
            if (interest != null && interest.toLowerCase(Locale.getDefault()).contains(queryLower)) {
                return true;
            }
        }
        return false;
    }

    private boolean passesDateFilter(Map<String, Object> event) {
        if (activeDateFilter == DateFilterOption.ANY_DATE) {
            return true;
        }

        long eventDate = getEventDateForFilter(event);
        if (eventDate <= 0) {
            return false;
        }

        switch (activeDateFilter) {
            case TODAY:
                return isSameDay(eventDate, startOfDay(System.currentTimeMillis()));
            case THIS_MONTH:
                return isSameMonth(eventDate, System.currentTimeMillis());
            case CUSTOM:
                if (customDateFilterStartMs <= 0) return true;
                return isSameDay(eventDate, customDateFilterStartMs);
            default:
                return true;
        }
    }

    private long getEventDateForFilter(Map<String, Object> event) {
        long startsAt = coerceLong(event.get("startsAtEpochMs"));
        if (startsAt > 0) {
            return startsAt;
        }
        long regStart = coerceLong(event.get("registrationStart"));
        return regStart > 0 ? regStart : 0;
    }

    private boolean isSameDay(long timestampMs, long dayStartMs) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(dayStartMs);

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestampMs);

        return day.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && day.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isSameMonth(long timestampMs, long referenceMs) {
        Calendar ref = Calendar.getInstance();
        ref.setTimeInMillis(referenceMs);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestampMs);
        return ref.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && ref.get(Calendar.MONTH) == target.get(Calendar.MONTH);
    }

    private long startOfDay(long timeMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void updateSearchQuery(String query) {
        String cleaned = query != null ? query.trim() : "";
        if (cleaned.equals(searchQuery)) {
            return;
        }
        searchQuery = cleaned;
        applyFiltersAndUpdateUI();
    }

    private void showFilterDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_discover_filter);
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }

        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        ImageButton closeButton = dialog.findViewById(R.id.btnFilterClose);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        RadioButton radioAnyDate = dialog.findViewById(R.id.radioAnyDate);
        RadioButton radioToday = dialog.findViewById(R.id.radioToday);
        RadioButton radioThisMonth = dialog.findViewById(R.id.radioTomorrow);
        RadioButton radioCustom = dialog.findViewById(R.id.radioCustomDate);
        TextView chooseDateValue = dialog.findViewById(R.id.textChooseDateValue);
        View chooseDateRow = dialog.findViewById(R.id.chooseDateRow);
        EditText etFilterLocation = dialog.findViewById(R.id.etFilterLocation);
        if (etFilterLocation != null) {
            etFilterLocation.setText(locationFilter);
        }

        restoreDateSelection(radioAnyDate, radioToday, radioThisMonth, radioCustom, chooseDateValue);
        setDateSelectionHandlers(radioAnyDate, radioToday, radioThisMonth, radioCustom, chooseDateRow, chooseDateValue);

        Button applyButton = dialog.findViewById(R.id.btnApplyFilters);
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> {
                if (etFilterLocation != null) {
                    locationFilter = etFilterLocation.getText() != null
                            ? etFilterLocation.getText().toString().trim()
                            : "";
                }
                applyFiltersAndUpdateUI();
                dialog.dismiss();
            });
        }

        dialog.show();

        View card = dialog.findViewById(R.id.dialogCardView);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);

            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }

    private void restoreDateSelection(RadioButton any, RadioButton today,
                                      RadioButton thisMonth, RadioButton custom,
                                      TextView chooseDateValue) {
        clearDateChecks(any, today, thisMonth, custom);
        if (any == null || today == null || thisMonth == null || custom == null) return;

        switch (activeDateFilter) {
            case ANY_DATE:
                any.setChecked(true);
                break;
            case TODAY:
                today.setChecked(true);
                break;
            case THIS_MONTH:
                thisMonth.setChecked(true);
                break;
            case CUSTOM:
                custom.setChecked(true);
                break;
        }

        updateChooseDateLabel(chooseDateValue);
    }

    private void updateChooseDateLabel(TextView chooseDateValue) {
        if (chooseDateValue == null) return;
        if (customDateFilterStartMs > 0) {
            chooseDateValue.setText(filterDateFormat.format(new Date(customDateFilterStartMs)));
        } else {
            chooseDateValue.setText("No date selected");
        }
    }

    private void setDateSelectionHandlers(RadioButton any, RadioButton today,
                                          RadioButton thisMonth, RadioButton custom,
                                          View chooseDateRow, TextView chooseDateValue) {
        View.OnClickListener anyHandler = v -> setDateChoice(DateFilterOption.ANY_DATE, any, today, thisMonth, custom, chooseDateValue, false);
        View.OnClickListener todayHandler = v -> setDateChoice(DateFilterOption.TODAY, today, any, thisMonth, custom, chooseDateValue, false);
        View.OnClickListener thisMonthHandler = v -> setDateChoice(DateFilterOption.THIS_MONTH, thisMonth, any, today, custom, chooseDateValue, false);
        View.OnClickListener customHandler = v -> setDateChoice(DateFilterOption.CUSTOM, custom, any, today, thisMonth, chooseDateValue, true);

        if (any != null) any.setOnClickListener(anyHandler);
        if (today != null) today.setOnClickListener(todayHandler);
        if (thisMonth != null) thisMonth.setOnClickListener(thisMonthHandler);
        if (custom != null) custom.setOnClickListener(customHandler);
        if (chooseDateRow != null) chooseDateRow.setOnClickListener(customHandler);
    }

    private void setDateChoice(DateFilterOption option, RadioButton target,
                               RadioButton other1, RadioButton other2, RadioButton other3,
                               TextView chooseDateValue, boolean launchPickerIfNeeded) {
        clearDateChecks(target, other1, other2, other3);
        if (target != null) {
            target.setChecked(true);
        }
        activeDateFilter = option;
        if (option == DateFilterOption.CUSTOM) {
            if (customDateFilterStartMs <= 0 || launchPickerIfNeeded) {
                openDatePicker(chooseDateValue, target);
            } else {
                updateChooseDateLabel(chooseDateValue);
            }
        } else {
            updateChooseDateLabel(chooseDateValue);
        }
    }

    private void clearDateChecks(RadioButton... radios) {
        if (radios == null) return;
        for (RadioButton rb : radios) {
            if (rb != null) {
                rb.setChecked(false);
            }
        }
    }

    private void openDatePicker(TextView chooseDateValue, RadioButton customRadio) {
        Calendar cal = Calendar.getInstance();
        if (customDateFilterStartMs > 0) {
            cal.setTimeInMillis(customDateFilterStartMs);
        }
        DatePickerDialog picker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth);
                    customDateFilterStartMs = selected.getTimeInMillis();
                    if (customRadio != null) customRadio.setChecked(true);
                    updateChooseDateLabel(chooseDateValue);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private Bitmap captureScreenshot() {
        try {
            if (getWindow() == null) return null;
            View rootView = getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null) return null;

        try {
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

            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private static String asString(Object o) {
        return o instanceof String ? (String) o : null;
    }

    /**
     * Creates a sectioned list with "Upcoming Events" and "Old Events" headers.
     * Events are split based on their start time (startsAtEpochMs) or registration end time.
     */
    private List<OrganizerMyEventAdapter.EventListItem> createSectionedList(List<Map<String, Object>> events) {
        List<OrganizerMyEventAdapter.EventListItem> sectionedList = new ArrayList<>();
        
        long currentTime = System.currentTimeMillis();
        List<Map<String, Object>> upcomingEvents = new ArrayList<>();
        List<Map<String, Object>> oldEvents = new ArrayList<>();
        
        for (Map<String, Object> event : events) {
            // Check startsAtEpochMs first, then fall back to registrationEnd
            long startTime = coerceLong(event.get("startsAtEpochMs"));
            if (startTime == 0) {
                startTime = coerceLong(event.get("registrationEnd"));
            }
            
            if (startTime > currentTime) {
                upcomingEvents.add(event);
            } else {
                oldEvents.add(event);
            }
        }
        
        // Add upcoming events section
        if (!upcomingEvents.isEmpty()) {
            sectionedList.add(OrganizerMyEventAdapter.EventListItem.createHeader("Upcoming Events"));
            for (Map<String, Object> event : upcomingEvents) {
                sectionedList.add(OrganizerMyEventAdapter.EventListItem.createEvent(event));
            }
        }
        
        // Add old events section
        if (!oldEvents.isEmpty()) {
            sectionedList.add(OrganizerMyEventAdapter.EventListItem.createHeader("Old Events"));
            for (Map<String, Object> event : oldEvents) {
                sectionedList.add(OrganizerMyEventAdapter.EventListItem.createEvent(event));
            }
        }
        
        return sectionedList;
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
        long startsAt = coerceLong(d.get("startsAtEpochMs"));
        int capacity  = coerceInt(d.get("capacity"), -1);

        m.put("id", id);
        m.put("title", title);
        m.put("posterUrl", posterUrl);
        m.put("location", location);
        m.put("interests", interests != null ? interests : new ArrayList<String>());
        m.put("registrationStart", regStart);
        m.put("registrationEnd", regEnd);
        m.put("deadlineEpochMs", deadline);
        m.put("startsAtEpochMs", startsAt);
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
     * Updates the bottom navigation icons and labels based on the selected page.
     * Selected icons are light, unselected are dark.
     */
    private void updateNavigationSelection(String selectedPage) {
        // Dark blue for unselected items (brand color)
        int unselectedColor = android.graphics.Color.parseColor("#223C65");
        // iOS blue color for selected items
        int selectedColor = android.graphics.Color.parseColor("#446EAF");

        // Reset all to unselected (dark circles and dark text)
        if (navIconMyEvents != null) {
            navIconMyEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_dark);
        }
        if (navIconCreate != null) {
            navIconCreate.setImageResource(R.drawable.organizer_ic_add_circle_dark);
        }
        if (navIconAccount != null) {
            navIconAccount.setImageResource(R.drawable.entrant_ic_account_circle_dark);
        }
        if (navLabelMyEvents != null) {
            navLabelMyEvents.setTextColor(unselectedColor);
        }
        if (navLabelCreate != null) {
            navLabelCreate.setTextColor(unselectedColor);
        }
        if (navLabelAccount != null) {
            navLabelAccount.setTextColor(unselectedColor);
        }

        // Set selected (light circle and blue text) based on page
        if ("myEvents".equals(selectedPage)) {
            if (navIconMyEvents != null) {
                navIconMyEvents.setImageResource(R.drawable.entrant_ic_my_events_circle_light);
            }
            if (navLabelMyEvents != null) {
                navLabelMyEvents.setTextColor(selectedColor);
            }
        } else if ("create".equals(selectedPage)) {
            if (navIconCreate != null) {
                navIconCreate.setImageResource(R.drawable.organizer_ic_add_circle_light);
            }
            if (navLabelCreate != null) {
                navLabelCreate.setTextColor(selectedColor);
            }
        } else if ("account".equals(selectedPage)) {
            if (navIconAccount != null) {
                navIconAccount.setImageResource(R.drawable.entrant_ic_account_circle_light);
            }
            if (navLabelAccount != null) {
                navLabelAccount.setTextColor(selectedColor);
            }
        }
    }

}