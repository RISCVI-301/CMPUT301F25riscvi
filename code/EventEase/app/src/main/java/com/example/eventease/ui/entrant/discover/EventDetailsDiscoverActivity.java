package com.example.eventease.ui.entrant.discover;

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

import com.example.eventease.auth.AuthManager;
import com.example.eventease.data.WaitlistRepository;
import com.example.eventease.model.Event;
import com.bumptech.glide.Glide;
import com.example.eventease.App;
import com.example.eventease.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for displaying event details from Discover screen.
 * Shows event information and allows users to join waitlists.
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
        setContentView(R.layout.entrant_activity_event_details_discover);

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
        guidelinesButton.setOnClickListener(v -> showGuidelinesDialog());

        // Wire bottom nav include buttons
        android.widget.LinearLayout navButtonMyEvents = findViewById(R.id.nav_button_my_events);
        android.widget.LinearLayout navButtonDiscover = findViewById(R.id.nav_button_discover);
        android.widget.LinearLayout navButtonAccount = findViewById(R.id.nav_button_account);
        if (navButtonDiscover != null) {
            navButtonDiscover.setOnClickListener(v -> navigateToMain("discover"));
        }
        if (navButtonMyEvents != null) {
            navButtonMyEvents.setOnClickListener(v -> navigateToMain("myEvents"));
        }
        if (navButtonAccount != null) {
            navButtonAccount.setOnClickListener(v -> navigateToMain("account"));
        }
    }

    private void showGuidelinesDialog() {
        // Capture screenshot and blur it
        android.graphics.Bitmap screenshot = captureScreenshot();
        android.graphics.Bitmap blurredBitmap = blurBitmap(screenshot, 25f);

        // Create custom dialog with full screen to show blur background
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.entrant_dialog_guidelines);

        // Set window properties
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            android.view.WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0f; // we use our own blur
            window.setAttributes(lp);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        // Apply blurred background
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurredBitmap != null) {
            blurBackground.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), blurredBitmap));
        }
        blurBackground.setOnClickListener(v -> dialog.dismiss());

        // Card for zoom animation
        androidx.cardview.widget.CardView cardView = dialog.findViewById(R.id.dialogCardView);

        // Content
        TextView tvContent = dialog.findViewById(R.id.tvDialogContent);
        android.widget.Button btnOk = dialog.findViewById(R.id.btnDialogOk);
        if (!TextUtils.isEmpty(guidelinesBody)) {
            tvContent.setText(guidelinesBody);
        }
        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Animations
        android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
        android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);
        blurBackground.startAnimation(fadeIn);
        cardView.startAnimation(zoomIn);
    }

    private void navigateToMain(String target) {
        android.content.Intent intent = new android.content.Intent(this, com.example.eventease.MainActivity.class);
        intent.putExtra("nav_target", target);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private android.graphics.Bitmap captureScreenshot() {
        android.view.View rootView = getWindow().getDecorView().getRootView();
        rootView.setDrawingCacheEnabled(true);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(rootView.getDrawingCache());
        rootView.setDrawingCacheEnabled(false);
        return bitmap;
    }

    private android.graphics.Bitmap blurBitmap(android.graphics.Bitmap bitmap, float radius) {
        if (bitmap == null) return null;
        try {
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            android.graphics.Bitmap inputBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, false);
            android.graphics.Bitmap outputBitmap = android.graphics.Bitmap.createBitmap(inputBitmap);

            android.renderscript.RenderScript rs = android.renderscript.RenderScript.create(this);
            android.renderscript.ScriptIntrinsicBlur blurScript = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
            android.renderscript.Allocation tmpIn = android.renderscript.Allocation.createFromBitmap(rs, inputBitmap);
            android.renderscript.Allocation tmpOut = android.renderscript.Allocation.createFromBitmap(rs, outputBitmap);
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            rs.destroy();
            return android.graphics.Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
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
        
        // Update button state when event data changes
        updateWaitlistButtonState();

        if (event.getRegistrationStart() > 0 && event.getRegistrationEnd() > 0) {
            SimpleDateFormat regDateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
            String regStartStr = regDateFormat.format(new Date(event.getRegistrationStart()));
            String regEndStr = regDateFormat.format(new Date(event.getRegistrationEnd()));
            String dateText = "Registration Period:\n" + regStartStr + "\n" + regEndStr;
            
            // Add deadline if available
            if (event.getDeadlineEpochMs() > 0) {
                String deadlineStr = regDateFormat.format(new Date(event.getDeadlineEpochMs()));
                dateText += "\n\nEvent Deadline: " + deadlineStr;
            }
            
            dateView.setText(dateText);
        }
        
        // Update button state after event is loaded
        checkWaitlistStatus();
        
        if (event.getStartsAtEpochMs() > 0) {
            String dateText = DATE_FORMAT.format(new Date(event.getStartsAtEpochMs()));
            
            // Add deadline if available
            if (event.getDeadlineEpochMs() > 0) {
                SimpleDateFormat regDateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
                String deadlineStr = regDateFormat.format(new Date(event.getDeadlineEpochMs()));
                dateText += "\n\nEvent Deadline: " + deadlineStr;
            }
            
            dateView.setText(dateText);
        } else {
            String dateText = getString(R.string.event_details_date_tbd);
            
            // Add deadline if available
            if (event.getDeadlineEpochMs() > 0) {
                SimpleDateFormat regDateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
                String deadlineStr = regDateFormat.format(new Date(event.getDeadlineEpochMs()));
                dateText += "\n\nEvent Deadline: " + deadlineStr;
            }
            
            dateView.setText(dateText);
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

        String overviewContent = event.getNotes();
        if (TextUtils.isEmpty(overviewContent)) {
            overviewContent = event.getDescription();
        }
        if (!TextUtils.isEmpty(overviewContent)) {
            overviewView.setText(overviewContent);
        } else {
            overviewView.setText(R.string.event_details_no_overview);
        }

        if (!TextUtils.isEmpty(event.getPosterUrl())) {
            Glide.with(this)
                    .load(event.getPosterUrl())
                    .placeholder(R.drawable.entrant_ic_launcher_foreground)
                    .error(R.drawable.entrant_ic_launcher_foreground)
                    .into(posterView);
        } else {
            posterView.setImageResource(R.drawable.entrant_ic_launcher_foreground);
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
        // Update button state when waitlist count changes (affects capacity check)
        if (currentEvent != null) {
            // Update the event's waitlist count for canJoinWaitlist() check
            currentEvent.waitlistCount = (int) count;
            updateWaitlistButtonState();
        }
    }

    private void observeWaitlistCollection() {
        if (waitlistRegistration != null) return;
        waitlistRegistration = firestore.collection("events")
                .document(eventId)
                .collection("WaitlistedEntrants")
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
        android.util.Log.d("EventDetailsDiscover", "handleJoinWaitlist called, isUserInWaitlist=" + isUserInWaitlist);
        
        if (isUserInWaitlist) {
            // User wants to opt out
            handleOptOut();
            return;
        }

        if (authManager == null || waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            android.util.Log.e("EventDetailsDiscover", "Cannot join: authManager=" + authManager + ", waitlistRepo=" + waitlistRepo + ", eventId=" + eventId);
            Toast.makeText(this, "Unable to join waitlist. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if user can join
        if (!canJoinWaitlist()) {
            android.util.Log.d("EventDetailsDiscover", "Cannot join waitlist - validation failed");
            if (currentEvent != null) {
                long currentTime = System.currentTimeMillis();
                if (currentEvent.getRegistrationStart() > 0 && currentTime < currentEvent.getRegistrationStart()) {
                    Toast.makeText(this, "Registration period has not started yet", Toast.LENGTH_LONG).show();
                } else if (currentEvent.getRegistrationEnd() > 0 && currentTime > currentEvent.getRegistrationEnd()) {
                    Toast.makeText(this, "Registration period has ended", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Waitlist is full. Capacity reached.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Cannot join waitlist at this time", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Disable button to prevent multiple clicks
        waitlistButton.setEnabled(false);
        android.util.Log.d("EventDetailsDiscover", "Calling waitlistRepo.join for eventId=" + eventId + ", uid=" + authManager.getUid());

        String uid = authManager.getUid();
        waitlistRepo.join(eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailsDiscover", "Successfully joined waitlist");
                    isUserInWaitlist = true;
                    updateWaitlistButtonState();
                    Toast.makeText(this, "Successfully joined the waitlist!", Toast.LENGTH_SHORT).show();
                    
                    // Show the guidelines dialog after successful join
                    JoinWaitlistDialogFragment.show(getSupportFragmentManager());
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailsDiscover", "Failed to join waitlist", e);
                    waitlistButton.setEnabled(true);
                    String errorMessage = e.getMessage();
                    if (TextUtils.isEmpty(errorMessage)) {
                        errorMessage = "Failed to join waitlist. Please try again.";
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                });
    }
    
    /**
     * Handle opt out from waitlist
     */
    private void handleOptOut() {
        if (authManager == null || waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            Toast.makeText(this, "Unable to leave waitlist. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent multiple clicks
        waitlistButton.setEnabled(false);

        String uid = authManager.getUid();
        waitlistRepo.leave(eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    isUserInWaitlist = false;
                    updateWaitlistButtonState();
                    Toast.makeText(this, "Successfully left the waitlist", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    waitlistButton.setEnabled(true);
                    String errorMessage = e.getMessage();
                    if (TextUtils.isEmpty(errorMessage)) {
                        errorMessage = "Failed to leave waitlist. Please try again.";
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
            waitlistButton.setText("Opt Out");
            waitlistButton.setEnabled(true);
        } else {
            // Check if capacity is full
            boolean isCapacityFull = isCapacityFull();
            if (isCapacityFull) {
                waitlistButton.setText("Capacity Full");
                waitlistButton.setEnabled(false);
            } else {
                // Check if registration period has ended or other restrictions
                boolean canJoin = canJoinWaitlist();
                waitlistButton.setText("Join Waitlist");
                waitlistButton.setEnabled(canJoin);
            }
        }
    }
    
    /**
     * Check if the waitlist capacity is full
     * FIX: This method should ideally check the actual subcollection count,
     * but for UI responsiveness, we use the cached waitlistCount.
     * The actual check happens in FirebaseWaitlistRepository.join() which always verifies the real count.
     */
    private boolean isCapacityFull() {
        if (currentEvent == null) {
            return false;
        }
        
        int capacity = currentEvent.getCapacity();
        if (capacity <= 0) {
            return false; // No capacity limit
        }
        
        // Use the waitlistCount from the event, which should be kept in sync
        // The actual verification happens server-side in FirebaseWaitlistRepository
        int waitlistCount = currentEvent.getWaitlistCount();
        return waitlistCount >= capacity;
    }
    
    /**
     * Check if user can join the waitlist (registration period active and capacity not reached)
     */
    private boolean canJoinWaitlist() {
        if (currentEvent == null) {
            android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: currentEvent is null");
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Check registration period
        if (currentEvent.getRegistrationStart() > 0 && currentEvent.getRegistrationEnd() > 0) {
            if (currentTime < currentEvent.getRegistrationStart()) {
                android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: Registration hasn't started. Start: " + 
                    new java.util.Date(currentEvent.getRegistrationStart()) + ", Current: " + new java.util.Date(currentTime));
                return false; // Registration period hasn't started
            }
            if (currentTime > currentEvent.getRegistrationEnd()) {
                android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: Registration has ended. End: " + 
                    new java.util.Date(currentEvent.getRegistrationEnd()) + ", Current: " + new java.util.Date(currentTime));
                return false; // Registration period has ended
            }
        } else if (currentEvent.getRegistrationStart() > 0) {
            // Only start time is set
            if (currentTime < currentEvent.getRegistrationStart()) {
                android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: Registration hasn't started (start only)");
                return false;
            }
        } else if (currentEvent.getRegistrationEnd() > 0) {
            // Only end time is set
            if (currentTime > currentEvent.getRegistrationEnd()) {
                android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: Registration has ended (end only)");
                return false;
            }
        }
        
        // Check capacity
        int capacity = currentEvent.getCapacity();
        if (capacity > 0) {
            int waitlistCount = currentEvent.getWaitlistCount();
            android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: Capacity check - capacity=" + capacity + ", waitlistCount=" + waitlistCount);
            if (waitlistCount >= capacity) {
                android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: Capacity reached");
                return false; // Capacity reached
            }
        }
        
        android.util.Log.d("EventDetailsDiscover", "canJoinWaitlist: All checks passed, can join");
        return true;
    }
}
