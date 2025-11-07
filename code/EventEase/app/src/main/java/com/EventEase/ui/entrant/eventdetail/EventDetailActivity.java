package com.EventEase.ui.entrant.eventdetail;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.InvitationRepository;
import com.EventEase.data.WaitlistRepository;
import com.EventEase.App;
import com.EventEase.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for displaying event details from My Events screen.
 * Shows event information and allows users to accept or decline invitations.
 */
public class EventDetailActivity extends AppCompatActivity {

    private TextView tvEventName;
    private TextView tvOverview;
    private TextView tvWaitlistCount;
    private Button btnRegister;
    private Button btnDecline;
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
    private com.EventEase.data.EventRepository eventRepo;
    private com.EventEase.data.ListenerRegistration waitlistCountReg;

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

        // Show/hide buttons based on invitation status
        if (hasInvitation) {
            btnRegister.setVisibility(View.VISIBLE);
            btnDecline.setVisibility(View.VISIBLE);
        } else {
            btnRegister.setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
        }

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
        // Display event name
        tvEventName.setText(eventTitle != null ? eventTitle : "Event Name");
        
        // Display event overview/notes
        if (eventNotes != null && !eventNotes.isEmpty()) {
            tvOverview.setText(eventNotes);
        } else {
            tvOverview.setText("No description available for this event.");
        }
        
        // Waitlist count will be updated by the listener (see setupWaitlistCountListener)
        tvWaitlistCount.setText("Loading...");
        
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
        Toast.makeText(this, "Event at " + eventLocation + " on " + dateStr, Toast.LENGTH_SHORT).show();
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
        android.content.Intent intent = new android.content.Intent(this, com.EventEase.MainActivity.class);
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
                    android.util.Log.d("EventDetailActivity", "✅ Invitation accepted successfully in UI");
                    Toast.makeText(this, "Invitation accepted! Event added to upcoming.", Toast.LENGTH_LONG).show();
                    // Hide the buttons since invitation is now accepted
                    btnRegister.setVisibility(View.GONE);
                    btnDecline.setVisibility(View.GONE);
                    // Finish the activity to return to previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "❌ Failed to accept invitation", e);
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
        
        // Decline the invitation (but keep user in waitlist)
        invitationRepo.decline(invitationId, eventId, uid)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("EventDetailActivity", "✅ Invitation declined successfully (user remains in waitlist)");
                    Toast.makeText(this, "Invitation declined. You remain on the waitlist.", Toast.LENGTH_LONG).show();
                    // Hide the buttons since invitation is now declined
                    btnRegister.setVisibility(View.GONE);
                    btnDecline.setVisibility(View.GONE);
                    // Finish the activity to return to previous screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("EventDetailActivity", "❌ Failed to decline invitation", e);
                    Toast.makeText(this, "Failed to decline invitation: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Restore buttons
                    btnRegister.setEnabled(true);
                    btnDecline.setEnabled(true);
                    btnDecline.setText("Decline");
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
