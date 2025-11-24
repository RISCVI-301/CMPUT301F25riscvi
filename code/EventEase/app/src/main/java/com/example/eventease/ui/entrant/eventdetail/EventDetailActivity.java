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
    private Button btnGuidelines;
    private Button btnLeaveWaitlist;
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
    private boolean isUserInWaitlist = false;
    private boolean isUserAdmitted = false;
    private boolean isUserInSelectedEntrants = false;
    private boolean isPreviousEvent = false;
    
    private InvitationRepository invitationRepo;
    private WaitlistRepository waitlistRepo;
    private AuthManager authManager;
    private com.example.eventease.data.EventRepository eventRepo;
    private com.example.eventease.data.AdmittedRepository admittedRepo;
    private com.example.eventease.data.ListenerRegistration waitlistCountReg;
    private com.example.eventease.data.ListenerRegistration invitationReg;

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
        
        // Initialize repositories
        invitationRepo = App.graph().invitations;
        waitlistRepo = App.graph().waitlists;
        authManager = App.graph().auth;
        eventRepo = App.graph().events;
        admittedRepo = App.graph().admitted;

        // Initialize views
        tvEventName = findViewById(R.id.tvEventName);
        tvOverview = findViewById(R.id.tvOverview);
        tvWaitlistCount = findViewById(R.id.tvWaitlistCount);
        btnRegister = findViewById(R.id.btnRegister);
        btnDecline = findViewById(R.id.btnDecline);
        btnGuidelines = findViewById(R.id.btnGuidelines);
        btnLeaveWaitlist = findViewById(R.id.btnLeaveWaitlist);
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

        // Set up register button
        btnRegister.setOnClickListener(v -> {
            showAcceptDialog();
            // TODO: Update invitation status to ACCEPTED
        });

        // Set up decline button
        btnDecline.setOnClickListener(v -> {
            showDeclineDialog();
            // TODO: Update invitation status to DECLINED
        });

        // Set up leave waitlist button
        btnLeaveWaitlist.setOnClickListener(v -> {
            handleLeaveWaitlist();
        });

        // Initially hide leave waitlist button - will be shown if user is on waitlist
        btnLeaveWaitlist.setVisibility(View.GONE);
        android.view.View leaveWaitlistCard = findViewById(R.id.leaveWaitlistCard);
        if (leaveWaitlistCard != null) {
            leaveWaitlistCard.setVisibility(View.GONE);
        }

        // If this is a previous event, hide all buttons, waitlist count, and skip status checks
        if (isPreviousEvent) {
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            btnLeaveWaitlist.setVisibility(View.GONE);
            if (leaveWaitlistCard != null) {
                leaveWaitlistCard.setVisibility(View.GONE);
            }
            // Hide waitlist count section for previous events
            android.view.View waitlistCountCard = findViewById(R.id.waitlistCountCard);
            if (waitlistCountCard != null) {
                waitlistCountCard.setVisibility(View.GONE);
            }
            android.util.Log.d("EventDetailActivity", "Previous event - hiding all buttons and waitlist count");
        } else {
            // Show/hide buttons based on invitation status
            if (hasInvitation) {
                btnRegister.setVisibility(View.VISIBLE);
                btnRegister.setText("Accept");
                btnDecline.setVisibility(View.VISIBLE);
            } else {
                btnRegister.setVisibility(View.GONE);
                btnDecline.setVisibility(View.GONE);
            }

            // Listen to waitlist count updates from Firebase
            setupWaitlistCountListener();
            
            // Check if user is on waitlist and if user is admitted
            checkWaitlistStatus();
            checkAdmittedStatus();
            // Check if user is in SelectedEntrants (means they were invited)
            checkSelectedEntrantsStatus();
            // Also check for invitations dynamically (in case hasInvitation from intent is incorrect)
            // This will do an immediate check and set up a listener for real-time updates
            checkInvitationStatus();
        }

        // Load event data (placeholder for now)
        loadEventData();

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
        // Display event name
        tvEventName.setText(eventTitle != null ? eventTitle : "Event Name");
        
        // Display event overview/notes
        if (eventNotes != null && !eventNotes.isEmpty()) {
            tvOverview.setText(eventNotes);
        } else {
            tvOverview.setText("No description available for this event.");
        }
        
        // Waitlist count will be updated by the listener (only for non-previous events)
        if (!isPreviousEvent && tvWaitlistCount != null) {
            tvWaitlistCount.setText("Loading...");
        }
        
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
        String detailMsg = "Event at " + eventLocation + " on " + dateStr;
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
        String uid = authManager.getUid();
        
        // If invitationId is not set but user is in SelectedEntrants, try to find the invitation
        if ((invitationId == null || invitationId.isEmpty()) && isUserInSelectedEntrants) {
            android.util.Log.d("EventDetailActivity", "Invitation ID not set but user is in SelectedEntrants, finding invitation...");
            findInvitationForSelectedEntrant(uid, true); // true = accept after finding
            return;
        }
        
        if (invitationId == null || invitationId.isEmpty()) {
            android.util.Log.e("EventDetailActivity", "Invitation ID is null or empty and user not in SelectedEntrants");
            Toast.makeText(this, "Invitation not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
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
                    // Update status - user is now admitted, not in waitlist anymore
                    hasInvitation = false;
                    isUserInWaitlist = false;
                    isUserAdmitted = true;
                    isUserInSelectedEntrants = false; // No longer in SelectedEntrants after accepting
                    updateLeaveWaitlistButtonVisibility();
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
     * Decline the invitation (keeps user in waitlist for future rerolls)
     */
    private void declineInvitation() {
        String uid = authManager.getUid();
        
        // If invitationId is not set but user is in SelectedEntrants, try to find the invitation
        if ((invitationId == null || invitationId.isEmpty()) && isUserInSelectedEntrants) {
            android.util.Log.d("EventDetailActivity", "Invitation ID not set but user is in SelectedEntrants, finding invitation...");
            findInvitationForSelectedEntrant(uid, false); // false = decline after finding
            return;
        }
        
        if (invitationId == null || invitationId.isEmpty()) {
            android.util.Log.e("EventDetailActivity", "Invitation ID is null or empty and user not in SelectedEntrants");
            Toast.makeText(this, "Invitation not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("EventDetailActivity", "Declining invitation: " + invitationId + " for event: " + eventId + " by user: " + uid);
        
        // Show loading state
        btnRegister.setEnabled(false);
        btnDecline.setEnabled(false);
        btnDecline.setText("Processing...");
        
        // Decline the invitation (but keep user in waitlist)
        invitationRepo.decline(invitationId, eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailActivity", "Invitation declined successfully (user remains in waitlist)");
                    Toast.makeText(this, "Invitation declined. You remain on the waitlist.", Toast.LENGTH_LONG).show();
                    // Hide the buttons since invitation is now declined
                    btnRegister.setVisibility(View.GONE);
                    btnDecline.setVisibility(View.GONE);
                    hasInvitation = false;
                    // User remains in SelectedEntrants but invitation is declined
                    updateButtonVisibility();
                    // Finish the activity to return to previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to decline invitation", e);
                    Toast.makeText(this, "Failed to decline invitation: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Restore buttons
                    btnRegister.setEnabled(true);
                    btnDecline.setEnabled(true);
                    btnDecline.setText("Decline");
                });
    }
    
    /**
     * Finds the invitation for a user who is in SelectedEntrants.
     * This is used when the user is in SelectedEntrants but invitationId is not set yet.
     * 
     * @param uid The user ID
     * @param acceptAfterFinding If true, accept the invitation after finding it; if false, decline it
     */
    private void findInvitationForSelectedEntrant(String uid, boolean acceptAfterFinding) {
        if (invitationRepo == null || eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Unable to process invitation", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading state
        btnRegister.setEnabled(false);
        btnDecline.setEnabled(false);
        if (acceptAfterFinding) {
            btnRegister.setText("Processing...");
        } else {
            btnDecline.setText("Processing...");
        }
        
        // Query for the invitation
        // Use same pattern as elsewhere: query by uid and status, filter by eventId in memory to avoid index requirements
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        // Filter by eventId in memory
                        com.google.firebase.firestore.QueryDocumentSnapshot foundDoc = null;
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                            String docEventId = doc.getString("eventId");
                            if (eventId.equals(docEventId)) {
                                foundDoc = doc;
                                break;
                            }
                        }
                        
                        if (foundDoc != null) {
                            String foundInvitationId = foundDoc.getString("id");
                            if (foundInvitationId == null || foundInvitationId.isEmpty()) {
                                foundInvitationId = foundDoc.getId();
                            }
                            
                            invitationId = foundInvitationId;
                            android.util.Log.d("EventDetailActivity", "Found invitation ID: " + invitationId + " for user in SelectedEntrants");
                            
                            // Now accept or decline
                            if (acceptAfterFinding) {
                                acceptInvitation();
                            } else {
                                declineInvitation();
                            }
                        } else {
                            // No invitation found for this event - this shouldn't happen if user is in SelectedEntrants
                            android.util.Log.e("EventDetailActivity", "User is in SelectedEntrants but no PENDING invitation found for event " + eventId);
                            Toast.makeText(this, "Invitation not found. Please contact the organizer.", Toast.LENGTH_LONG).show();
                            // Restore buttons
                            btnRegister.setEnabled(true);
                            btnDecline.setEnabled(true);
                            if (acceptAfterFinding) {
                                btnRegister.setText("Accept");
                            } else {
                                btnDecline.setText("Decline");
                            }
                        }
                    } else {
                        // No invitation found - this shouldn't happen if user is in SelectedEntrants
                        android.util.Log.e("EventDetailActivity", "User is in SelectedEntrants but no PENDING invitation found");
                        Toast.makeText(this, "Invitation not found. Please contact the organizer.", Toast.LENGTH_LONG).show();
                        // Restore buttons
                        btnRegister.setEnabled(true);
                        btnDecline.setEnabled(true);
                        if (acceptAfterFinding) {
                            btnRegister.setText("Accept");
                        } else {
                            btnDecline.setText("Decline");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to find invitation for SelectedEntrant", e);
                    Toast.makeText(this, "Failed to find invitation: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Restore buttons
                    btnRegister.setEnabled(true);
                    btnDecline.setEnabled(true);
                    if (acceptAfterFinding) {
                        btnRegister.setText("Accept");
                    } else {
                        btnDecline.setText("Decline");
                    }
                });
    }

    /**
     * Check if the current user has a pending invitation for this event
     */
    private void checkInvitationStatus() {
        if (authManager == null || invitationRepo == null || eventId == null || eventId.isEmpty()) {
            return;
        }

        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            return;
        }

        // First, do an immediate check for invitations to catch any that were just created
        // This ensures buttons show up immediately even if the listener hasn't fired yet
        checkInvitationStatusImmediate(uid);

        // Then set up the listener for real-time updates
        invitationReg = invitationRepo.listenActive(uid, new com.example.eventease.data.InvitationListener() {
            @Override
            public void onChanged(java.util.List<com.example.eventease.model.Invitation> activeInvites) {
                updateInvitationButtons(activeInvites);
            }
        });
    }
    
    /**
     * Immediately checks for invitations without waiting for listener updates.
     * This is useful when the activity is first opened or when we want to force a refresh.
     * Uses the same query pattern as the listener (uid + status) to avoid index requirements,
     * then filters by eventId in memory.
     */
    private void checkInvitationStatusImmediate(String uid) {
        if (invitationRepo == null || eventId == null || eventId.isEmpty()) {
            return;
        }
        
        // Query invitations directly from Firestore for immediate check
        // Use same query pattern as listener (uid + status) to avoid index requirements
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    boolean hasActiveInvite = false;
                    String activeInvitationId = null;
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        java.util.Date now = new java.util.Date();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                            try {
                                // Filter by eventId in memory (same as listener does)
                                String invEventId = doc.getString("eventId");
                                if (!eventId.equals(invEventId)) {
                                    continue; // Not for this event
                                }
                                
                                // Check expiration
                                Long expiresAtMs = doc.getLong("expiresAt");
                                if (expiresAtMs != null && expiresAtMs > 0) {
                                    java.util.Date expiresAt = new java.util.Date(expiresAtMs);
                                    if (expiresAt.before(now)) {
                                        // Invitation expired, skip it
                                        continue;
                                    }
                                }
                                
                                hasActiveInvite = true;
                                activeInvitationId = doc.getString("id");
                                if (activeInvitationId == null || activeInvitationId.isEmpty()) {
                                    activeInvitationId = doc.getId();
                                }
                                android.util.Log.d("EventDetailActivity", "Immediate check: Found PENDING invitation for event: " + eventId + ", invitationId: " + activeInvitationId);
                                break;
                            } catch (Exception e) {
                                android.util.Log.e("EventDetailActivity", "Error parsing invitation document", e);
                            }
                        }
                    }
                    
                    if (hasActiveInvite) {
                        hasInvitation = true;
                        invitationId = activeInvitationId;
                        updateButtonVisibility();
                    } else {
                        // Also update buttons if no invitation found (to hide them if invitation was declined/expired)
                        hasInvitation = false;
                        invitationId = null;
                        updateButtonVisibility();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to check invitation status immediately", e);
                });
    }
    
    /**
     * Updates invitation buttons based on the list of active invitations.
     */
    private void updateInvitationButtons(java.util.List<com.example.eventease.model.Invitation> activeInvites) {
        boolean hasActiveInvite = false;
        String activeInvitationId = null;
        
        if (activeInvites != null) {
            for (com.example.eventease.model.Invitation inv : activeInvites) {
                // Check if this invitation is for the current event and is PENDING
                if (eventId.equals(inv.getEventId()) && 
                    inv.getStatus() == com.example.eventease.model.Invitation.Status.PENDING) {
                    hasActiveInvite = true;
                    activeInvitationId = inv.getId();
                    android.util.Log.d("EventDetailActivity", "Found PENDING invitation for event: " + eventId + ", invitationId: " + activeInvitationId);
                    break;
                }
            }
        }
        
        android.util.Log.d("EventDetailActivity", "updateInvitationButtons: hasActiveInvite=" + hasActiveInvite + " for eventId=" + eventId);
        
        // Update hasInvitation and invitationId
        hasInvitation = hasActiveInvite;
        if (hasActiveInvite && activeInvitationId != null) {
            invitationId = activeInvitationId;
        }
        
        updateButtonVisibility();
    }
    
    /**
     * Updates button visibility based on current invitation status.
     * Shows accept/decline buttons if:
     * 1. User has a PENDING invitation, OR
     * 2. User is in SelectedEntrants (which means they were invited)
     * 
     * For previous events, all buttons are hidden.
     */
    private void updateButtonVisibility() {
        // If this is a previous event, hide all buttons
        if (isPreviousEvent) {
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            btnLeaveWaitlist.setVisibility(View.GONE);
            android.view.View leaveWaitlistCard = findViewById(R.id.leaveWaitlistCard);
            if (leaveWaitlistCard != null) {
                leaveWaitlistCard.setVisibility(View.GONE);
            }
            android.util.Log.d("EventDetailActivity", "Previous event - hiding all buttons");
            return;
        }
        
        // Show accept/decline buttons if user has invitation OR is in SelectedEntrants
        // Being in SelectedEntrants means they were invited, even if invitation check hasn't completed yet
        boolean shouldShowInviteButtons = hasInvitation || isUserInSelectedEntrants;
        
        // Update button visibility
        if (shouldShowInviteButtons) {
            btnRegister.setVisibility(View.VISIBLE);
            btnRegister.setText("Accept");
            btnDecline.setVisibility(View.VISIBLE);
            android.util.Log.d("EventDetailActivity", "Showing accept/decline buttons - hasInvitation: " + hasInvitation + ", isUserInSelectedEntrants: " + isUserInSelectedEntrants);
        } else {
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            android.util.Log.d("EventDetailActivity", "Hiding accept/decline buttons - hasInvitation: " + hasInvitation + ", isUserInSelectedEntrants: " + isUserInSelectedEntrants);
        }
        
        // Also update leave waitlist button visibility
        updateLeaveWaitlistButtonVisibility();
    }

    /**
     * Check if the current user is on the waitlist for this event
     */
    private void checkWaitlistStatus() {
        if (authManager == null || waitlistRepo == null || eventId == null || eventId.isEmpty()) {
            return;
        }

        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            return;
        }

        waitlistRepo.isJoined(eventId, uid)
                .addOnSuccessListener(joined -> {
                    isUserInWaitlist = joined != null && joined;
                    updateLeaveWaitlistButtonVisibility();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to check waitlist status", e);
                    isUserInWaitlist = false;
                    updateLeaveWaitlistButtonVisibility();
                });
    }

    /**
     * Check if the current user is admitted to this event
     */
    private void checkAdmittedStatus() {
        if (authManager == null || admittedRepo == null || eventId == null || eventId.isEmpty()) {
            return;
        }

        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            return;
        }

        admittedRepo.isAdmitted(eventId, uid)
                .addOnSuccessListener(admitted -> {
                    isUserAdmitted = admitted != null && admitted;
                    updateLeaveWaitlistButtonVisibility();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to check admitted status", e);
                    isUserAdmitted = false;
                    updateLeaveWaitlistButtonVisibility();
                });
    }

    /**
     * Check if the current user is in SelectedEntrants for this event.
     * Being in SelectedEntrants means they were selected and should have an invitation.
     */
    private void checkSelectedEntrantsStatus() {
        if (authManager == null || eventId == null || eventId.isEmpty()) {
            return;
        }

        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            return;
        }

        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("events")
                .document(eventId)
                .collection("SelectedEntrants")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean inSelected = documentSnapshot != null && documentSnapshot.exists();
                    isUserInSelectedEntrants = inSelected;
                    android.util.Log.d("EventDetailActivity", "User " + uid + " in SelectedEntrants for event " + eventId + ": " + inSelected);
                    
                    // If user is in SelectedEntrants, ensure buttons are shown
                    // Also try to find/create invitation if not found yet
                    if (inSelected && !hasInvitation) {
                        android.util.Log.d("EventDetailActivity", "User is in SelectedEntrants but no invitation found yet, checking again...");
                        // Update buttons immediately (they should show based on SelectedEntrants status)
                        updateButtonVisibility();
                        // Also try to find the invitation (might be a timing issue)
                        if (invitationRepo != null) {
                            checkInvitationStatusImmediate(uid);
                        }
                    } else {
                        updateButtonVisibility();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to check SelectedEntrants status", e);
                    isUserInSelectedEntrants = false;
                    updateButtonVisibility();
                });
    }

    /**
     * Update the visibility of the leave waitlist button based on waitlist status
     */
    private void updateLeaveWaitlistButtonVisibility() {
        // Only show leave waitlist button if:
        // 1. User is on waitlist
        // 2. User doesn't have an invitation AND is not in SelectedEntrants
        //    (if in SelectedEntrants, they were invited, so don't show leave waitlist)
        // 3. User is NOT admitted (admitted users shouldn't see leave waitlist button)
        boolean shouldShow = isUserInWaitlist && !hasInvitation && !isUserInSelectedEntrants && !isUserAdmitted;
        
        if (btnLeaveWaitlist != null) {
            btnLeaveWaitlist.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
        
        android.view.View leaveWaitlistCard = findViewById(R.id.leaveWaitlistCard);
        if (leaveWaitlistCard != null) {
            leaveWaitlistCard.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Handle leaving the waitlist
     */
    private void handleLeaveWaitlist() {
        if (authManager == null || waitlistRepo == null || eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Unable to leave waitlist. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "Unable to leave waitlist. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent multiple clicks
        btnLeaveWaitlist.setEnabled(false);
        btnLeaveWaitlist.setText("Leaving...");

        android.util.Log.d("EventDetailActivity", "Leaving waitlist for event: " + eventId + " by user: " + uid);

        waitlistRepo.leave(eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailActivity", "Successfully left waitlist");
                    isUserInWaitlist = false;
                    updateLeaveWaitlistButtonVisibility();
                    Toast.makeText(this, "Successfully left the waitlist", Toast.LENGTH_SHORT).show();
                    
                    // Navigate back to waitlisted events list (My Events page)
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "Failed to leave waitlist", e);
                    btnLeaveWaitlist.setEnabled(true);
                    btnLeaveWaitlist.setText("Leave Waitlist");
                    
                    String errorMessage = "Failed to leave waitlist";
                    if (e != null && e.getMessage() != null && !e.getMessage().isEmpty()) {
                        errorMessage = e.getMessage();
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh status when activity resumes (e.g., user navigates back)
        // This ensures buttons are shown if status changed while the activity was in background
        if (authManager != null && eventId != null && !eventId.isEmpty()) {
            String uid = authManager.getUid();
            if (uid != null && !uid.isEmpty()) {
                // Refresh SelectedEntrants status
                checkSelectedEntrantsStatus();
                // Refresh invitation status
                if (invitationRepo != null) {
                    checkInvitationStatusImmediate(uid);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waitlistCountReg != null) {
            waitlistCountReg.remove();
            waitlistCountReg = null;
        }
        if (invitationReg != null) {
            invitationReg.remove();
            invitationReg = null;
        }
    }
}
