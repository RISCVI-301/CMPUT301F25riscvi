package com.example.eventease.ui.entrant.discover;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.data.WaitlistRepository;
import com.example.eventease.model.Event;
import com.bumptech.glide.Glide;
import com.example.eventease.App;
import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

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
    private androidx.cardview.widget.CardView waitlistCard;
    private String guidelinesBody;

    private ListenerRegistration eventRegistration;
    private ListenerRegistration waitlistRegistration;
    private FirebaseFirestore firestore;
    
    private WaitlistRepository waitlistRepo;

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
        waitlistCard = findViewById(R.id.waitlistCard);
        Button guidelinesButton = findViewById(R.id.btnGuidelines);
        ImageButton backButton = findViewById(R.id.btnBack);

        setLoading(true);
        shareButton.setEnabled(false);
        waitlistButton.setEnabled(false);
        guidelinesBody = getString(R.string.event_details_guidelines_body);

        backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        shareButton.setOnClickListener(v -> showEventQRDialog());
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
        if (waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            return;
        }

        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
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

        if (waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            android.util.Log.e("EventDetailsDiscover", "Cannot join: waitlistRepo=" + waitlistRepo + ", eventId=" + eventId);
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
        String currentUid = com.example.eventease.auth.AuthHelper.getUid(this);
        android.util.Log.d("EventDetailsDiscover", "Calling waitlistRepo.join for eventId=" + eventId + ", uid=" + currentUid);

        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
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
        if (waitlistRepo == null || TextUtils.isEmpty(eventId)) {
            Toast.makeText(this, "Unable to leave waitlist. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent multiple clicks
        waitlistButton.setEnabled(false);

        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
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
            // Change CardView background to red and text to white for Opt Out
            if (waitlistCard != null) {
                waitlistCard.setCardBackgroundColor(android.graphics.Color.parseColor("#E57373"));
            }
            waitlistButton.setTextColor(android.graphics.Color.WHITE);
        } else {
            // Check if capacity is full
            boolean isCapacityFull = isCapacityFull();
            if (isCapacityFull) {
                waitlistButton.setText("Capacity Full");
                waitlistButton.setEnabled(false);
                // Keep blue color for disabled state
                if (waitlistCard != null) {
                    waitlistCard.setCardBackgroundColor(android.graphics.Color.parseColor("#7FDBDA"));
                }
                waitlistButton.setTextColor(android.graphics.Color.parseColor("#2C4A6E"));
            } else {
                // Check if registration period has ended or other restrictions
                boolean canJoin = canJoinWaitlist();
                waitlistButton.setText("Join Waitlist");
                waitlistButton.setEnabled(canJoin);
                // Change CardView background to blue for Join Waitlist
                if (waitlistCard != null) {
                    waitlistCard.setCardBackgroundColor(android.graphics.Color.parseColor("#7FDBDA"));
                }
                waitlistButton.setTextColor(android.graphics.Color.parseColor("#2C4A6E"));
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

    /**
     * Shows the Event QR code dialog
     */
    private void showEventQRDialog() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get qrPayload from currentEvent if available, otherwise generate it
        final String qrPayload;
        if (currentEvent != null && currentEvent.getQrPayload() != null && !currentEvent.getQrPayload().isEmpty()) {
            qrPayload = currentEvent.getQrPayload();
        } else {
            // Generate QR payload if not stored (use custom scheme format)
            qrPayload = "eventease://event/" + eventId;
        }

        final String eventTitleText = currentEvent != null && !TextUtils.isEmpty(currentEvent.getTitle()) 
            ? currentEvent.getTitle() : "Event";

        // Create dialog
        Dialog dialog = createCardDialog(R.layout.dialog_qr_preview);
        TextView titleView = dialog.findViewById(R.id.tvEventTitle);
        ImageView imgQr = dialog.findViewById(R.id.imgQr);
        MaterialButton btnShare = dialog.findViewById(R.id.btnShare);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSave);
        MaterialButton btnCopyLink = dialog.findViewById(R.id.btnCopyLink);
        MaterialButton btnViewEvents = dialog.findViewById(R.id.btnViewEvents);

        if (titleView != null) {
            titleView.setText(eventTitleText);
        }

        final Bitmap qrBitmap = generateQrBitmap(qrPayload);
        if (imgQr != null) {
            if (qrBitmap != null) {
                imgQr.setImageBitmap(qrBitmap);
            } else {
                imgQr.setImageResource(R.drawable.ic_event_poster_placeholder);
            }
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                if (qrBitmap != null) {
                    shareQrBitmap(qrBitmap, qrPayload);
                } else {
                    shareQrText(qrPayload);
                }
            });
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (qrBitmap != null) {
                    boolean saved = saveQrToGallery(qrBitmap, eventTitleText);
                    if (saved) {
                        Toast.makeText(EventDetailsDiscoverActivity.this, "Saved to gallery", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(EventDetailsDiscoverActivity.this, "QR not ready yet.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCopyLink != null) {
            btnCopyLink.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Event Link", qrPayload);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(EventDetailsDiscoverActivity.this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnViewEvents != null) {
            btnViewEvents.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Creates a card-style dialog
     */
    private Dialog createCardDialog(int layoutRes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(layoutRes);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Generates a QR code bitmap from the given payload
     */
    private Bitmap generateQrBitmap(String payload) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 512, 512);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (WriterException e) {
            android.util.Log.e("EventDetailsDiscover", "QR code generation failed", e);
            return null;
        }
    }

    /**
     * Shares the QR code bitmap
     */
    private void shareQrBitmap(Bitmap bitmap, String payload) {
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "qr");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new java.io.IOException("Unable to create cache directory");
            }
            java.io.File file = new java.io.File(cacheDir, "qr_" + System.currentTimeMillis() + ".png");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Scan this QR to view the event: " + payload);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share QR code"));
        } catch (java.io.IOException e) {
            android.util.Log.e("EventDetailsDiscover", "Failed to share QR bitmap", e);
            Toast.makeText(this, "Unable to share QR. Try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shares the QR code as text
     */
    private void shareQrText(String payload) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(shareIntent, "Share event link"));
    }

    /**
     * Saves QR code to gallery
     */
    private boolean saveQrToGallery(Bitmap bitmap, String title) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "EventEase_QR_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/EventEase");
                android.net.Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            return true;
                        }
                    }
                }
            } else {
                java.io.File picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES);
                java.io.File eventEaseDir = new java.io.File(picturesDir, "EventEase");
                if (!eventEaseDir.exists()) {
                    eventEaseDir.mkdirs();
                }
                java.io.File file = new java.io.File(eventEaseDir, "EventEase_QR_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    // Notify media scanner
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(android.net.Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                    return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("EventDetailsDiscover", "Failed to save QR code", e);
        }
        return false;
    }
}
