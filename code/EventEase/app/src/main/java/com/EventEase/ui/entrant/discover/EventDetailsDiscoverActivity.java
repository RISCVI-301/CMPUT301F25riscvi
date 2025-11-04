package com.EventEase.ui.entrant.discover;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.WaitlistRepository;
import com.EventEase.model.Event;
import com.bumptech.glide.Glide;
import com.example.eventease.App;
import com.example.eventease.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Displays the details for a selected event coming from the Discover screen.
 */
public class EventDetailsDiscoverActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT_TITLE = "extra_event_title";

    private TextView titleView;
    private TextView overviewView;
    private TextView waitlistCountView;
    private TextView dateView;
    private TextView locationView;
    private TextView capacityView;
    private ImageView posterView;
    private View contentContainer;
    private ProgressBar progressBar;
    private ImageButton shareButton;
    private Button waitlistButton;
    private String guidelinesBody;

    private ListenerRegistration eventRegistration;
    private ListenerRegistration waitlistRegistration;
    private FirebaseFirestore firestore;
    
    private WaitlistRepository waitlistRepo;
    private AuthManager authManager;

    private Event currentEvent;
    private String eventId;
    private boolean isUserInWaitlist = false;

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEEE, MMM d • h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details_discover);

        // Initialize repositories
        waitlistRepo = App.graph().waitlists;
        authManager = App.graph().auth;

        bindViews();

        String titleHint = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        if (!TextUtils.isEmpty(titleHint)) {
            titleView.setText(titleHint);
        }

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (TextUtils.isEmpty(eventId)) {
            Toast.makeText(this, R.string.event_details_missing_id, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        firestore = FirebaseFirestore.getInstance();

        observeEvent();
        checkWaitlistStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventRegistration != null) {
            eventRegistration.remove();
        }
        if (waitlistRegistration != null) {
            waitlistRegistration.remove();
        }
    }

    private void bindViews() {
        titleView = findViewById(R.id.tvEventName);
        overviewView = findViewById(R.id.tvOverview);
        waitlistCountView = findViewById(R.id.tvWaitlistCount);
        dateView = findViewById(R.id.tvEventDate);
        locationView = findViewById(R.id.tvEventLocation);
        capacityView = findViewById(R.id.tvEventCapacity);
        posterView = findViewById(R.id.ivEventImage);
        progressBar = findViewById(R.id.eventDetailProgress);
        contentContainer = findViewById(R.id.eventDetailContent);
        shareButton = findViewById(R.id.btnShare);
        waitlistButton = findViewById(R.id.waitlist_join);
        Button guidelinesButton = findViewById(R.id.btnGuidelines);
        ImageButton backButton = findViewById(R.id.btnBack);

        setLoading(true);
        shareButton.setEnabled(false);
        waitlistButton.setEnabled(false);
        guidelinesBody = getString(R.string.event_details_guidelines_body);

        backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        shareButton.setOnClickListener(v -> shareEvent());
        waitlistButton.setOnClickListener(v -> handleJoinWaitlist());
        guidelinesButton.setOnClickListener(v ->
                GuidelinesDialogFragment.show(
                        getSupportFragmentManager(),
                        getString(R.string.event_details_guidelines_title),
                        guidelinesBody));
    }

    private void observeEvent() {
        eventRegistration = firestore.collection("events")
                .document(eventId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        handleEventError(error);
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        handleMissingEvent();
                        return;
                    }
                    bindEvent(snapshot);
                });
    }

    private void bindEvent(@NonNull DocumentSnapshot snapshot) {
        Event event = snapshot.toObject(Event.class);
        if (event == null) {
            handleMissingEvent();
            return;
        }
        if (TextUtils.isEmpty(event.id)) {
            event.id = snapshot.getId();
        }
        currentEvent = event;

        titleView.setText(!TextUtils.isEmpty(event.getTitle()) ? event.getTitle()
                : getString(R.string.event_details_title_placeholder));

        String remoteGuidelines = snapshot.getString("guidelines");
        if (!TextUtils.isEmpty(remoteGuidelines)) {
            guidelinesBody = remoteGuidelines;
        }

        Long waitlistCountField = snapshot.getLong("waitlistCount");
        if (waitlistCountField != null) {
            updateWaitlistCount(waitlistCountField);
            stopWaitlistCollectionListener();
        } else {
            observeWaitlistCollection();
        }

        if (event.getStartsAtEpochMs() > 0) {
            dateView.setText(DATE_FORMAT.format(new Date(event.getStartsAtEpochMs())));
        } else {
            dateView.setText(R.string.event_details_date_tbd);
        }

        if (!TextUtils.isEmpty(event.getLocation())) {
            locationView.setText(event.getLocation());
        } else {
            locationView.setText(R.string.event_details_location_tbd);
        }

        if (event.getCapacity() > 0) {
            capacityView.setText(getString(R.string.event_details_capacity, event.getCapacity()));
        } else {
            capacityView.setText(R.string.event_details_capacity_unknown);
        }

        if (!TextUtils.isEmpty(event.getNotes())) {
            overviewView.setText(event.getNotes());
        } else {
            overviewView.setText(R.string.event_details_no_overview);
        }

        if (!TextUtils.isEmpty(event.getPosterUrl())) {
            Glide.with(this)
                    .load(event.getPosterUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(posterView);
        } else {
            posterView.setImageResource(R.drawable.ic_launcher_foreground);
        }

        shareButton.setEnabled(true);
        setLoading(false);
    }

    private void shareEvent() {
        if (currentEvent == null) return;
        StringBuilder body = new StringBuilder();
        String title = TextUtils.isEmpty(currentEvent.getTitle())
                ? getString(R.string.event_details_title_placeholder)
                : currentEvent.getTitle();
        body.append(title);
        if (!TextUtils.isEmpty(currentEvent.getLocation())) {
            body.append("\n").append(getString(R.string.event_details_share_location, currentEvent.getLocation()));
        }
        if (currentEvent.getStartsAtEpochMs() > 0) {
            body.append("\n").append(DATE_FORMAT.format(new Date(currentEvent.getStartsAtEpochMs())));
        }
        if (!TextUtils.isEmpty(currentEvent.getNotes())) {
            body.append("\n\n").append(currentEvent.getNotes());
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.event_details_share_prompt)));
    }

    private void handleEventError(@NonNull FirebaseFirestoreException error) {
        if (!isFinishing()) {
            String reason = error.getMessage();
            String message = TextUtils.isEmpty(reason)
                    ? getString(R.string.event_details_generic_error)
                    : getString(R.string.event_details_load_error_with_reason, reason);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        setLoading(false);
    }

    private void handleMissingEvent() {
        if (!isFinishing()) {
            Toast.makeText(this, R.string.event_details_not_found, Toast.LENGTH_LONG).show();
        }
        setLoading(false);
        if (!isFinishing()) {
            finish();
        }
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (contentContainer != null) {
            contentContainer.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void updateWaitlistCount(long count) {
        if (waitlistCountView != null) {
            waitlistCountView.setText(String.valueOf(Math.max(0, count)));
        }
    }

    private void observeWaitlistCollection() {
        if (waitlistRegistration != null) return;
        waitlistRegistration = firestore.collection("events")
                .document(eventId)
                .collection("waitlist")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        if (waitlistCountView != null) {
                            waitlistCountView.setText("--");
                        }
                        return;
                    }
                    int count = snapshot != null ? snapshot.size() : 0;
                    updateWaitlistCount(count);
                });
    }

    private void stopWaitlistCollectionListener() {
        if (waitlistRegistration != null) {
            waitlistRegistration.remove();
            waitlistRegistration = null;
        }
    }

    /**
     * Check if the current user is already in the waitlist
     */
    private void checkWaitlistStatus() {
        if (authManager == null || waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            return;
        }

        String uid = authManager.getUid();
        waitlistRepo.isJoined(eventId, uid)
                .addOnSuccessListener(joined -> {
                    isUserInWaitlist = joined != null && joined;
                    updateWaitlistButtonState();
                })
                .addOnFailureListener(e -> {
                    // If check fails, assume not joined
                    isUserInWaitlist = false;
                    updateWaitlistButtonState();
                });
    }

    /**
     * Handle the join waitlist button click
     */
    private void handleJoinWaitlist() {
        if (isUserInWaitlist) {
            Toast.makeText(this, "You are already in the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }

        if (authManager == null || waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            Toast.makeText(this, "Unable to join waitlist. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent multiple clicks
        waitlistButton.setEnabled(false);

        String uid = authManager.getUid();
        waitlistRepo.join(eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    isUserInWaitlist = true;
                    updateWaitlistButtonState();
                    Toast.makeText(this, "Successfully joined the waitlist!", Toast.LENGTH_SHORT).show();
                    
                    // Show the guidelines dialog after successful join
                    JoinWaitlistDialogFragment.show(getSupportFragmentManager());
                })
                .addOnFailureListener(e -> {
                    waitlistButton.setEnabled(true);
                    String errorMessage = e.getMessage();
                    if (TextUtils.isEmpty(errorMessage)) {
                        errorMessage = "Failed to join waitlist. Please try again.";
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Update the waitlist button text and state based on whether user is already in waitlist
     */
    private void updateWaitlistButtonState() {
        if (waitlistButton == null) {
            return;
        }

        if (isUserInWaitlist) {
            waitlistButton.setText("Already in Waitlist");
            waitlistButton.setEnabled(false);
        } else {
            waitlistButton.setText("Join Waitlist");
            waitlistButton.setEnabled(true);
        }
    }
}
