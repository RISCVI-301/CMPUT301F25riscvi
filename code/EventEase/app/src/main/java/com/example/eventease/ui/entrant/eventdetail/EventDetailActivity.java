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

import com.example.eventease.auth.AuthManager;
import com.example.eventease.data.InvitationRepository;
import com.example.eventease.data.WaitlistRepository;
import com.example.eventease.App;
import com.example.eventease.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

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
    private Button btnGuidelines;
    private ImageButton btnBack;
    private ImageButton btnShare;
    private ImageView ivEventImage;
    // Custom nav include controls
    private android.widget.LinearLayout navButtonMyEvents;
    private android.widget.LinearLayout navButtonDiscover;
    private android.widget.LinearLayout navButtonAccount;
    private BottomNavigationView bottomNavigation;
    
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
    
    private InvitationRepository invitationRepo;
    private WaitlistRepository waitlistRepo;
    private AuthManager authManager;
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
        
        // Initialize repositories
        invitationRepo = App.graph().invitations;
        waitlistRepo = App.graph().waitlists;
        authManager = App.graph().auth;
        eventRepo = App.graph().events;

        // Initialize views
        tvEventName = findViewById(R.id.tvEventName);
        tvOverview = findViewById(R.id.tvOverview);
        tvWaitlistCount = findViewById(R.id.tvWaitlistCount);
        btnRegister = findViewById(R.id.btnRegister);
        btnDecline = findViewById(R.id.btnDecline);
        btnOptOut = findViewById(R.id.btnOptOut);
        btnGuidelines = findViewById(R.id.btnGuidelines);
        btnBack = findViewById(R.id.btnBack);
        btnShare = findViewById(R.id.btnShare);
        ivEventImage = findViewById(R.id.ivEventImage);
        // Find custom nav include buttons
        navButtonMyEvents = findViewById(R.id.nav_button_my_events);
        navButtonDiscover = findViewById(R.id.nav_button_discover);
        navButtonAccount = findViewById(R.id.nav_button_account);

        // Set up back button
        btnBack.setOnClickListener(v -> finish());

        // Set up share button
        btnShare.setOnClickListener(v -> {
            Toast.makeText(this, "Share functionality coming soon", Toast.LENGTH_SHORT).show();
        });

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

        // Set up opt-out button
        if (btnOptOut != null) {
            btnOptOut.setOnClickListener(v -> {
                showOptOutDialog();
            });
        }

        // Check for invitation in real-time (in case it wasn't passed in Intent)
        checkForPendingInvitation();

        // Load event data (placeholder for now)
        loadEventData();
        
        // Listen to waitlist count updates from Firebase
        setupWaitlistCountListener();

        // Wire bottom nav navigation to MainActivity destinations
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

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.myEventsFragment);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.discoverFragment) {
                finish(); // Go back to main activity which will show discover
                return true;
            } else if (itemId == R.id.myEventsFragment) {
                finish(); // Go back to main activity which will show my events
                return true;
            } else if (itemId == R.id.accountFragment) {
                finish(); // Go back to main activity which will show account
                return true;
            }
            return false;
        });
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
        
        String uid = authManager.getUid();
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
        String uid = authManager.getUid();
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
        
        String uid = authManager.getUid();
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
        
        String uid = authManager.getUid();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waitlistCountReg != null) {
            waitlistCountReg.remove();
            waitlistCountReg = null;
        }
    }
}
