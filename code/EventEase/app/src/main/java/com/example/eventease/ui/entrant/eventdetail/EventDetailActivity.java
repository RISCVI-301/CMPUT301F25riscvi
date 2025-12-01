package com.example.eventease.ui.entrant.eventdetail;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.data.InvitationRepository;
import com.example.eventease.data.WaitlistRepository;
import com.example.eventease.App;
import com.example.eventease.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.Window;
import android.view.ViewGroup;
import android.app.Dialog;
import android.provider.MediaStore;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for displaying event details from the My Events screen.
 * 
 * <p>This activity shows comprehensive event information including:
 * <ul>
 *   <li>Event title, description, location, and guidelines</li>
 *   <li>Event start time and deadline</li>
 *   <li>Event capacity and current waitlist count</li>
 *   <li>Event poster image</li>
 * </ul>
 * 
 * <p>Features:
 * <ul>
 *   <li>Accept/decline invitation buttons (shown if user has a pending invitation)</li>
 *   <li>Register button (shown if user is on waitlist)</li>
 *   <li>Guidelines button to view event-specific rules</li>
 *   <li>Real-time waitlist count updates</li>
 *   <li>Share functionality</li>
 * </ul>
 * 
 * <p>The activity listens for invitation and waitlist updates in real-time and
 * updates the UI accordingly.
 */
public class EventDetailActivity extends AppCompatActivity {

    private TextView tvEventName;
    private TextView tvOverview;
    private TextView tvWaitlistCount;
    private Button btnRegister;
    private Button btnDecline;
    private Button btnOptOut;
    private Button btnLeaveWaitlist;
    private Button btnGuidelines;
    private ImageButton btnBack;
    private ImageButton btnShare;
    private ImageView ivEventImage;
    // Custom nav include controls
    // Card views for visibility control
    private android.view.View waitlistCountCard;
    private android.view.View leaveWaitlistCard;
    
    private String eventId;
    private String eventTitle;
    private String eventLocation;
    private long eventStartTime;
    private int eventCapacity;
    private String eventNotes;
    private String eventGuidelines;
    private String eventPosterUrl;
    private int eventWaitlistCount;
    private boolean hasInvitation;
    private String invitationId;
    private String eventQrPayload;
    private boolean isPreviousEvent;  // Flag to indicate if viewing from Previous Events
    private boolean isUpcomingEvent;  // Flag to indicate if viewing from Upcoming Events (already accepted)
    private boolean isWaitlistedEvent;  // Flag to indicate if viewing from Waitlisted Events
    
    private InvitationRepository invitationRepo;
    private WaitlistRepository waitlistRepo;
    private com.example.eventease.data.EventRepository eventRepo;
    private com.example.eventease.data.ListenerRegistration waitlistCountReg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entrant_activity_event_detail);

        // Get event data from Intent
        eventId = getIntent().getStringExtra("eventId");
        eventTitle = getIntent().getStringExtra("eventTitle");
        eventLocation = getIntent().getStringExtra("eventLocation");
        eventStartTime = getIntent().getLongExtra("eventStartTime", 0);
        eventCapacity = getIntent().getIntExtra("eventCapacity", 0);
        eventNotes = getIntent().getStringExtra("eventNotes");
        eventGuidelines = getIntent().getStringExtra("eventGuidelines");
        eventPosterUrl = getIntent().getStringExtra("eventPosterUrl");
        eventWaitlistCount = getIntent().getIntExtra("eventWaitlistCount", 0);
        hasInvitation = getIntent().getBooleanExtra("hasInvitation", false);
        invitationId = getIntent().getStringExtra("invitationId");
        isPreviousEvent = getIntent().getBooleanExtra("isPreviousEvent", false);
        isWaitlistedEvent = getIntent().getBooleanExtra("isWaitlistedEvent", false);
        
        // Check if viewing from Upcoming Events (already accepted - not waitlisted, not previous, no invitation)
        isUpcomingEvent = !hasInvitation && !isPreviousEvent && !isWaitlistedEvent;
        
        android.util.Log.d("EventDetailActivity", "Event flags: isPreviousEvent=" + isPreviousEvent + ", isUpcomingEvent=" + isUpcomingEvent + ", isWaitlistedEvent=" + isWaitlistedEvent + ", hasInvitation=" + hasInvitation);
        
        // Initialize repositories
        invitationRepo = App.graph().invitations;
        waitlistRepo = App.graph().waitlists;
        eventRepo = App.graph().events;

        // Initialize views
        tvEventName = findViewById(R.id.tvEventName);
        tvOverview = findViewById(R.id.tvOverview);
        tvWaitlistCount = findViewById(R.id.tvWaitlistCount);
        btnRegister = findViewById(R.id.btnRegister);
        btnDecline = findViewById(R.id.btnDecline);
        btnOptOut = findViewById(R.id.btnOptOut);
        btnLeaveWaitlist = findViewById(R.id.btnLeaveWaitlist);
        btnGuidelines = findViewById(R.id.btnGuidelines);
        btnBack = findViewById(R.id.btnBack);
        btnShare = findViewById(R.id.btnShare);
        ivEventImage = findViewById(R.id.ivEventImage);
        // Find card views
        waitlistCountCard = findViewById(R.id.waitlistCountCard);
        leaveWaitlistCard = findViewById(R.id.leaveWaitlistCard);

        // CRITICAL: Hide ALL action buttons immediately for Previous/Upcoming events (read-only views)
        // This prevents any flash of buttons before they're hidden
        if (isPreviousEvent || isUpcomingEvent) {
            android.util.Log.d("EventDetailActivity", "📖 READ-ONLY MODE - Hiding all action buttons immediately");
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            if (btnOptOut != null) {
                btnOptOut.setVisibility(View.GONE);
            }
            if (btnLeaveWaitlist != null && leaveWaitlistCard != null) {
                btnLeaveWaitlist.setVisibility(View.GONE);
                leaveWaitlistCard.setVisibility(View.GONE);
            }
        }

        // Set up back button
        btnBack.setOnClickListener(v -> finish());

        // Set up share button
        btnShare.setOnClickListener(v -> showEventQRDialog());

        // Set up guidelines button
        btnGuidelines.setOnClickListener(v -> {
            showGuidelinesDialog();
        });

        // Set up register button (Accept invitation)
        btnRegister.setOnClickListener(v -> {
            android.util.Log.d("EventDetailActivity", "Accept button clicked");
            acceptInvitation();
        });

        // Set up decline button
        btnDecline.setOnClickListener(v -> {
            android.util.Log.d("EventDetailActivity", "Decline button clicked");
            declineInvitation();
        });

        // Set up opt-out button (leave waitlist button removed - using only opt-out)
        if (btnOptOut != null) {
            btnOptOut.setOnClickListener(v -> {
                showOptOutDialog();
            });
        }

        // Hide leave waitlist button - using only opt-out button
        if (btnLeaveWaitlist != null && leaveWaitlistCard != null) {
            btnLeaveWaitlist.setVisibility(View.GONE);
            leaveWaitlistCard.setVisibility(View.GONE);
        }

        // Check for invitation in real-time (in case it wasn't passed in Intent)
        checkForPendingInvitation();

        // Load event data (placeholder for now)
        loadEventData();
        
        // Listen to waitlist count updates from Firebase
        setupWaitlistCountListener();
    }

    private void loadEventData() {
        // If event title is missing, fetch from Firestore
        if (eventId != null && (eventTitle == null || eventTitle.isEmpty())) {
            android.util.Log.d("EventDetailActivity", "Fetching event data from Firestore...");
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("events")
                    .document(eventId)
                    .get()
                    .addOnSuccessListener(eventDoc -> {
                        if (eventDoc.exists()) {
                            // Update all event fields from Firestore
                            eventTitle = eventDoc.getString("title");
                            eventLocation = eventDoc.getString("location");
                            eventNotes = eventDoc.getString("description");
                            eventGuidelines = eventDoc.getString("guidelines");
                            eventPosterUrl = eventDoc.getString("posterUrl");
                            eventQrPayload = eventDoc.getString("qrPayload");
                            
                            Long startsAt = eventDoc.getLong("startsAtEpochMs");
                            if (startsAt != null) eventStartTime = startsAt;
                            
                            Integer capacity = eventDoc.getLong("capacity") != null ? 
                                eventDoc.getLong("capacity").intValue() : 0;
                            eventCapacity = capacity;
                            
                            Integer waitlist = eventDoc.getLong("waitlistCount") != null ?
                                eventDoc.getLong("waitlistCount").intValue() : 0;
                            eventWaitlistCount = waitlist;
                            
                            android.util.Log.d("EventDetailActivity", "✅ Event data loaded from Firestore");
                            android.util.Log.d("EventDetailActivity", "Title: " + eventTitle);
                            
                            // Display the data
                            displayEventData();
                        } else {
                            android.util.Log.e("EventDetailActivity", "Event document not found in Firestore!");
                            displayEventData(); // Show with placeholder data
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("EventDetailActivity", "Failed to fetch event data", e);
                        displayEventData(); // Show with placeholder data
                    });
        } else {
            // Data already provided in Intent
            displayEventData();
        }
    }
    
    private void displayEventData() {
        // Display event name
        tvEventName.setText(eventTitle != null ? eventTitle : "Event Name");
        
        // Display event overview/notes
        if (eventNotes != null && !eventNotes.isEmpty()) {
            tvOverview.setText(eventNotes);
        } else {
            tvOverview.setText("No description available for this event.");
        }
        
        // Waitlist count will be updated by the listener
        tvWaitlistCount.setText(String.valueOf(eventWaitlistCount));
        
        // Load event image using Glide
        if (eventPosterUrl != null && !eventPosterUrl.isEmpty()) {
            Glide.with(this)
                .load(eventPosterUrl)
                .placeholder(R.drawable.entrant_card_image_placeholder)
                .error(R.drawable.entrant_card_image_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(ivEventImage);
        } else {
            ivEventImage.setImageResource(R.drawable.entrant_card_image_placeholder);
        }
        
        // Update button text based on event details
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());
        String dateStr = eventStartTime > 0 ? sdf.format(new Date(eventStartTime)) : "TBD";
        
        // You can show more event details in a toast or update UI as needed
        String detailMsg = "Event at " + (eventLocation != null ? eventLocation : "location TBD") + " on " + dateStr;
        long eventDeadline = getIntent().getLongExtra("eventDeadline", 0);
        if (eventDeadline > 0) {
            detailMsg += " (Deadline: " + sdf.format(new Date(eventDeadline)) + ")";
        }
        Toast.makeText(this, detailMsg, Toast.LENGTH_SHORT).show();
    }
    
    private void setupWaitlistCountListener() {
        if (eventId != null && eventRepo != null) {
            waitlistCountReg = eventRepo.listenWaitlistCount(eventId, count -> {
                if (tvWaitlistCount != null) {
                    tvWaitlistCount.setText(String.valueOf(count));
                    android.util.Log.d("EventDetailActivity", "Waitlist count updated: " + count + " for event: " + eventId);
                }
            });
        }
    }

    private void showGuidelinesDialog() {
        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
        
        // Create custom dialog with full screen to show blur background
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.entrant_dialog_guidelines);
        
        // Set window properties
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // Disable dim since we have our own blur background
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0f;
            window.setAttributes(layoutParams);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Apply blurred background
        android.view.View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurredBitmap != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }
        
        // Make the background clickable to dismiss
        blurBackground.setOnClickListener(v -> dialog.dismiss());
        
        // Get the CardView for animation
        androidx.cardview.widget.CardView cardView = dialog.findViewById(R.id.dialogCardView);
        
        // Set up dialog views
        TextView tvContent = dialog.findViewById(R.id.tvDialogContent);
        android.widget.Button btnOk = dialog.findViewById(R.id.btnDialogOk);
        
        // Set guidelines content
        if (eventGuidelines != null && !eventGuidelines.isEmpty()) {
            tvContent.setText(eventGuidelines);
        } else {
            tvContent.setText("No specific guidelines have been set for this event.");
        }
        
        // Set OK button click listener
        btnOk.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
        
        // Apply animations after dialog is shown
        android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
        android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);
        
        blurBackground.startAnimation(fadeIn);
        cardView.startAnimation(zoomIn);
    }
    
    private void showAcceptDialog() {
        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
        
        // Create custom dialog with full screen to show blur background
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.entrant_dialog_accept_invitation);
        
        // Set window properties
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // Disable dim since we have our own blur background
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0f;
            window.setAttributes(layoutParams);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Apply blurred background
        android.view.View blurBackground = dialog.findViewById(R.id.dialogAcceptBlurBackground);
        if (blurredBitmap != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }
        
        // Make the background clickable to dismiss
        blurBackground.setOnClickListener(v -> dialog.dismiss());
        
        // Get the CardView for animation
        androidx.cardview.widget.CardView cardView = dialog.findViewById(R.id.dialogCard);
        
        // Set up dialog views
        android.widget.Button btnDone = dialog.findViewById(R.id.btnAcceptDone);
        
        // Set Done button click listener - accept the invitation
        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
            acceptInvitation();
        });
        
        dialog.show();
        
        // Apply animations after dialog is shown
        if (blurBackground != null && cardView != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            cardView.startAnimation(zoomIn);
        }
    }
    
    private void showDeclineDialog() {
        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
        
        // Create custom dialog with full screen to show blur background
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.entrant_dialog_decline_invitation);
        
        // Set window properties
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // Disable dim since we have our own blur background
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0f;
            window.setAttributes(layoutParams);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Apply blurred background
        android.view.View blurBackground = dialog.findViewById(R.id.dialogDeclineBlurBackground);
        if (blurredBitmap != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }
        
        // Make the background clickable to dismiss
        blurBackground.setOnClickListener(v -> dialog.dismiss());
        
        // Set up dialog views
        android.widget.Button btnDone = dialog.findViewById(R.id.btnDeclineDone);
        
        // Set Done button click listener - decline the invitation
        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
            declineInvitation();
        });
        
        dialog.show();
    }
    
    private Bitmap captureScreenshot() {
        android.view.View rootView = getWindow().getDecorView().getRootView();
        rootView.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
        rootView.setDrawingCacheEnabled(false);
        return bitmap;
    }
    
    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null) return null;
        
        try {
            // Scale down for better performance
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
            
            // Scale back up
            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private void navigateToMain(String target) {
        android.content.Intent intent = new android.content.Intent(this, com.example.eventease.MainActivity.class);
        intent.putExtra("nav_target", target);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Accept the invitation and move user to upcoming events
     */
    private void acceptInvitation() {
        if (invitationId == null || invitationId.isEmpty()) {
            android.util.Log.e("EventDetailActivity", "Invitation ID is null or empty");
            Toast.makeText(this, "Invitation not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
        android.util.Log.d("EventDetailActivity", "Accepting invitation: " + invitationId + " for event: " + eventId + " by user: " + uid);
        
        // Show loading state
        btnRegister.setEnabled(false);
        btnDecline.setEnabled(false);
        btnRegister.setText("Processing...");
        
        // Accept the invitation
        invitationRepo.accept(invitationId, eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailActivity", "Invitation accepted successfully in UI");
                    Toast.makeText(this, "Invitation accepted! Event added to upcoming.", Toast.LENGTH_LONG).show();
                    // Hide the buttons since invitation is now accepted
                    btnRegister.setVisibility(View.GONE);
                    btnDecline.setVisibility(View.GONE);
                    // Finish the activity to return to previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to accept invitation", e);
                    // Restore button state
                    btnRegister.setEnabled(true);
                    btnDecline.setEnabled(true);
                    btnRegister.setText("Accept");
                    
                    // Show appropriate error message
                    String errorMessage = "Failed to accept invitation";
                    if (e != null && e.getMessage() != null) {
                        if (e.getMessage().contains("full capacity")) {
                            errorMessage = "Event is at full capacity. Please contact the organizer.";
                        } else if (e.getMessage().contains("Event not found")) {
                            errorMessage = "Event not found";
                        } else {
                            errorMessage = e.getMessage();
                        }
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Checks for pending invitations for this event and updates UI accordingly.
     */
    private void checkForPendingInvitation() {
        // Skip invitation check for Previous/Upcoming events (read-only views)
        if (isPreviousEvent || isUpcomingEvent) {
            android.util.Log.d("EventDetailActivity", "Skipping invitation check - read-only view");
            return;
        }
        
        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
        if (uid == null || eventId == null) {
            android.util.Log.w("EventDetailActivity", "Cannot check invitation - uid or eventId is null");
            updateButtonVisibility(false, null);
            return;
        }
        
        android.util.Log.d("EventDetailActivity", "═══ Checking for pending invitation ═══");
        android.util.Log.d("EventDetailActivity", "EventId: " + eventId);
        android.util.Log.d("EventDetailActivity", "UserId: " + uid);
        
        // Query invitations collection for this event + user
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("invitations")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING")
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("EventDetailActivity", "Query completed - documents found: " + 
                        (querySnapshot != null ? querySnapshot.size() : 0));
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        // Found pending invitation
                        com.google.firebase.firestore.DocumentSnapshot invDoc = querySnapshot.getDocuments().get(0);
                        String foundInvitationId = invDoc.getId();
                        android.util.Log.d("EventDetailActivity", "✅ FOUND PENDING INVITATION!");
                        android.util.Log.d("EventDetailActivity", "Invitation ID: " + foundInvitationId);
                        android.util.Log.d("EventDetailActivity", "Status: " + invDoc.getString("status"));
                        android.util.Log.d("EventDetailActivity", "IssuedAt: " + invDoc.getLong("issuedAt"));
                        android.util.Log.d("EventDetailActivity", "ExpiresAt: " + invDoc.getLong("expiresAt"));
                        
                        hasInvitation = true;
                        invitationId = foundInvitationId;
                        updateButtonVisibility(true, foundInvitationId);
                    } else {
                        // No pending invitation
                        android.util.Log.d("EventDetailActivity", "❌ No pending invitation found");
                        android.util.Log.d("EventDetailActivity", "User might not be selected yet, or invitation already processed");
                        hasInvitation = false;
                        invitationId = null;
                        updateButtonVisibility(false, null);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "❌ Failed to check for invitation", e);
                    android.util.Log.e("EventDetailActivity", "Error: " + e.getMessage());
                    // Default to no invitation if check fails
                    updateButtonVisibility(false, null);
                });
    }
    
    /**
     * Updates button visibility based on invitation status.
     */
    private void updateButtonVisibility(boolean hasInvite, String inviteId) {
        android.util.Log.d("EventDetailActivity", "═══ Updating Button Visibility ═══");
        android.util.Log.d("EventDetailActivity", "Has Invite: " + hasInvite);
        android.util.Log.d("EventDetailActivity", "Invite ID: " + inviteId);
        android.util.Log.d("EventDetailActivity", "Is Previous Event: " + isPreviousEvent);
        android.util.Log.d("EventDetailActivity", "Is Upcoming Event: " + isUpcomingEvent);
        android.util.Log.d("EventDetailActivity", "Is Waitlisted Event: " + isWaitlistedEvent);
        
        // CRITICAL: Hide ALL action buttons for Previous and Upcoming events (read-only views)
        if (isPreviousEvent || isUpcomingEvent) {
            android.util.Log.d("EventDetailActivity", "📖 READ-ONLY MODE - Hiding all action buttons");
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            if (btnOptOut != null) {
                btnOptOut.setVisibility(View.GONE);
            }
            if (btnLeaveWaitlist != null && leaveWaitlistCard != null) {
                btnLeaveWaitlist.setVisibility(View.GONE);
                leaveWaitlistCard.setVisibility(View.GONE);
            }
            return;
        }
        
        // For waitlisted events: show accept/decline if invited, show opt-out if not invited
        if (hasInvite && inviteId != null) {
            // Has invitation - show accept/decline, hide opt-out
            android.util.Log.d("EventDetailActivity", "✅ SHOWING ACCEPT/DECLINE BUTTONS");
            btnRegister.setVisibility(View.VISIBLE);
            btnRegister.setText("Accept");
            btnRegister.setEnabled(true);
            
            btnDecline.setVisibility(View.VISIBLE);
            btnDecline.setText("Decline");
            btnDecline.setEnabled(true);
            
            if (btnOptOut != null) {
                btnOptOut.setVisibility(View.GONE);
            }
            // Leave waitlist button is always hidden (removed duplicate)
            if (btnLeaveWaitlist != null && leaveWaitlistCard != null) {
                btnLeaveWaitlist.setVisibility(View.GONE);
                leaveWaitlistCard.setVisibility(View.GONE);
            }
            
            // Update invitation ID for accept/decline actions
            this.invitationId = inviteId;
            this.hasInvitation = true;
            
            android.util.Log.d("EventDetailActivity", "Button states:");
            android.util.Log.d("EventDetailActivity", "  - btnRegister: VISIBLE, text=" + btnRegister.getText());
            android.util.Log.d("EventDetailActivity", "  - btnDecline: VISIBLE, text=" + btnDecline.getText());
        } else {
            // No invitation - show opt-out, hide accept/decline
            android.util.Log.d("EventDetailActivity", "⚠️ SHOWING OPT-OUT BUTTON (no invitation)");
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            if (btnOptOut != null) {
                btnOptOut.setVisibility(View.VISIBLE);
            }
            // Leave waitlist button is always hidden (removed duplicate - using only opt-out)
            if (btnLeaveWaitlist != null && leaveWaitlistCard != null) {
                btnLeaveWaitlist.setVisibility(View.GONE);
                leaveWaitlistCard.setVisibility(View.GONE);
            }
            
            this.invitationId = null;
            this.hasInvitation = false;
            
            android.util.Log.d("EventDetailActivity", "Button states:");
            android.util.Log.d("EventDetailActivity", "  - btnRegister: GONE");
            android.util.Log.d("EventDetailActivity", "  - btnDecline: GONE");
            android.util.Log.d("EventDetailActivity", "  - btnOptOut: VISIBLE");
        }
    }
    
    /**
     * Decline the invitation (keeps user in waitlist for future rerolls)
     */
    private void declineInvitation() {
        if (invitationId == null || invitationId.isEmpty()) {
            android.util.Log.e("EventDetailActivity", "Invitation ID is null or empty");
            Toast.makeText(this, "Invitation not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
        android.util.Log.d("EventDetailActivity", "Declining invitation: " + invitationId + " for event: " + eventId + " by user: " + uid);
        
        // Show loading state
        btnRegister.setEnabled(false);
        btnDecline.setEnabled(false);
        btnDecline.setText("Processing...");
        
        // Decline the invitation (moves user to CancelledEntrants)
        invitationRepo.decline(invitationId, eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailActivity", "✅ Invitation declined successfully!");
                    android.util.Log.d("EventDetailActivity", "User moved to CancelledEntrants");
                    Toast.makeText(this, "Invitation declined. You've been moved to cancelled.", Toast.LENGTH_LONG).show();
                    // Hide the buttons since invitation is now declined
                    btnRegister.setVisibility(View.GONE);
                    btnDecline.setVisibility(View.GONE);
                    // Finish the activity to return to previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "❌ Failed to decline invitation!", e);
                    android.util.Log.e("EventDetailActivity", "Error: " + e.getMessage());
                    Toast.makeText(this, "Failed to decline invitation: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Restore buttons
                    btnRegister.setEnabled(true);
                    btnDecline.setEnabled(true);
                    btnDecline.setText("Decline");
                });
    }

    /**
     * Show dialog to confirm opt-out from waitlist
     */
    private void showOptOutDialog() {
        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
        
        // Create custom dialog
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.entrant_dialog_opt_out, null);
        dialog.setContentView(dialogView);
        
        // Set window properties
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // Disable dim since we have our own blur background
            android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0f;
            window.setAttributes(layoutParams);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Apply blurred background
        android.view.View blurBackground = dialogView.findViewById(R.id.dialogOptOutBlurBackground);
        if (blurredBitmap != null && blurBackground != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }
        
        // Make the background clickable to dismiss
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Get the CardView for animation
        androidx.cardview.widget.CardView cardView = dialogView.findViewById(R.id.dialogCard);
        
        // Set up dialog views
        android.widget.Button btnConfirmOptOut = dialogView.findViewById(R.id.btnConfirmOptOut);
        android.widget.Button btnCancelOptOut = dialogView.findViewById(R.id.btnCancelOptOut);
        
        // Set Cancel button click listener
        if (btnCancelOptOut != null) {
            btnCancelOptOut.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Set Confirm button click listener - opt out of waitlist
        if (btnConfirmOptOut != null) {
            btnConfirmOptOut.setOnClickListener(v -> {
                dialog.dismiss();
                performOptOut();
            });
        }
        
        dialog.show();
        
        // Apply animations after dialog is shown
        if (blurBackground != null && cardView != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            cardView.startAnimation(zoomIn);
        }
    }
    
    /**
     * Opt out from the event waitlist
     */
    private void performOptOut() {
        if (eventId == null || eventId.isEmpty()) {
            android.util.Log.e("EventDetailActivity", "Event ID is null or empty");
            Toast.makeText(this, "Cannot opt out at this time", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String uid = com.example.eventease.auth.AuthHelper.getUid(this);
        android.util.Log.d("EventDetailActivity", "Opting out from event: " + eventId + " by user: " + uid);
        
        // Show loading state
        if (btnOptOut != null) {
            btnOptOut.setEnabled(false);
            btnOptOut.setText("Processing...");
        }
        
        // Opt out from waitlist
        waitlistRepo.leave(eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailActivity", "Successfully opted out from waitlist");
                    Toast.makeText(this, "You have opted out from the waitlist", Toast.LENGTH_LONG).show();
                    // Finish the activity to return to previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to opt out from waitlist", e);
                    Toast.makeText(this, "Failed to opt out: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Restore button
                    if (btnOptOut != null) {
                        btnOptOut.setEnabled(true);
                        btnOptOut.setText("Opt Out");
                    }
                });
    }

    /**
     * Shows the Event QR code dialog
     */
    private void showEventQRDialog() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use stored qrPayload if available, otherwise generate it
        final String qrPayload;
        if (eventQrPayload != null && !eventQrPayload.isEmpty()) {
            qrPayload = eventQrPayload;
        } else {
            // Generate QR payload if not stored (use custom scheme format)
            qrPayload = "eventease://event/" + eventId;
        }

        final String eventTitleText = eventTitle != null && !eventTitle.isEmpty() ? eventTitle : "Event";

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
                        Toast.makeText(EventDetailActivity.this, "Saved to gallery", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(EventDetailActivity.this, "QR not ready yet.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCopyLink != null) {
            btnCopyLink.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Event Link", qrPayload);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(EventDetailActivity.this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show();
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
            android.util.Log.e("EventDetailActivity", "QR code generation failed", e);
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
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Scan this QR to view the event: " + payload);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share QR code"));
        } catch (java.io.IOException e) {
            android.util.Log.e("EventDetailActivity", "Failed to share QR bitmap", e);
            Toast.makeText(this, "Unable to share QR. Try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shares the QR code as text
     */
    private void shareQrText(String payload) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, payload);
        startActivity(android.content.Intent.createChooser(shareIntent, "Share event link"));
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
                    android.content.Intent mediaScanIntent = new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(android.net.Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                    return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("EventDetailActivity", "Failed to save QR code", e);
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waitlistCountReg != null) {
            waitlistCountReg.remove();
            waitlistCountReg = null;
        }
    }
}
