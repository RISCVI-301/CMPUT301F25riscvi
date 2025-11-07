package com.example.eventease.ui.organizer;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.eventease.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for organizers to view and manage entrants for an event.
 * 
 * <p>This activity displays entrants in three categories:
 * <ul>
 *   <li>Selected Entrants - Users who have been selected by the organizer</li>
 *   <li>Not Selected Entrants - Users who were not selected</li>
 *   <li>Cancelled Entrants - Users who have cancelled their participation</li>
 * </ul>
 * 
 * <p>Features:
 * <ul>
 *   <li>View all entrants organized by selection status</li>
 *   <li>Send invitations to selected entrants</li>
 *   <li>View final entrant list (admitted entrants) in a dialog</li>
 * </ul>
 * 
 * <p>The activity loads entrants from Firestore subcollections and allows organizers to
 * manage the selection process for their events.
 */
public class OrganizerViewEntrantsActivity extends AppCompatActivity {
    private static final String TAG = "OrganizerViewEntrants";

    private ListView listSelected, listNotSelected, listCancelled;
    private FirebaseFirestore db;
    private String eventId;
    private String eventTitle;

    private final List<String> selectedList = new ArrayList<>();
    private final List<String> notSelectedList = new ArrayList<>();
    private final List<String> cancelledList = new ArrayList<>();

    private ArrayAdapter<String> selectedAdapter;
    private ArrayAdapter<String> notSelectedAdapter;
    private ArrayAdapter<String> cancelledAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewentrants);

        ImageView back = findViewById(R.id.back_button);
        if (back != null) back.setOnClickListener(v -> finish());

        ImageButton btnMailSelected = findViewById(R.id.btnMailSelected);
        if (btnMailSelected != null) {
            btnMailSelected.setOnClickListener(v -> showSendInvitationsConfirmation());
        }

        listSelected = findViewById(R.id.recyclerSelected);
        listNotSelected = findViewById(R.id.recyclerNotSelected);
        listCancelled = findViewById(R.id.recyclerCancelled);

        db = FirebaseFirestore.getInstance();

        selectedAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, selectedList);
        notSelectedAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, notSelectedList);
        cancelledAdapter = new ArrayAdapter<>(this, R.layout.item_waitlist_name, android.R.id.text1, cancelledList);

        listSelected.setAdapter(selectedAdapter);
        listNotSelected.setAdapter(notSelectedAdapter);
        listCancelled.setAdapter(cancelledAdapter);

        eventId = getIntent().getStringExtra("eventId");
        if (eventId != null && !eventId.isEmpty()) {
            checkAndProcessSelection(eventId);
            loadEventTitle();
        }
        
        loadEntrantsFromFirestore();
        
        // Set up Final Entrants button
        Button btnFinalEntrants = findViewById(R.id.finalentrant_button);
        if (btnFinalEntrants != null) {
            btnFinalEntrants.setOnClickListener(v -> showFinalEntrantListDialog());
        }
    }
    
    private void loadEventTitle() {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        eventTitle = doc.getString("title");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load event title", e);
                });
    }
    
    private void showSendInvitationsConfirmation() {
        if (selectedList.isEmpty()) {
            Toast.makeText(this, "No selected entrants to send invitations to", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int count = selectedList.size();
        String message = "Send invitations to " + count + " selected entrant" + (count > 1 ? "s" : "") + "? They will receive push notifications about their selection.";
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Invitations")
                .setMessage(message)
                .setPositiveButton("Send", (dialog, which) -> sendInvitations())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void sendInvitations() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Sending invitations...", Toast.LENGTH_SHORT).show();
        
        InvitationHelper invitationHelper = new InvitationHelper();
        invitationHelper.sendInvitationsToSelectedEntrants(eventId, eventTitle, new InvitationHelper.InvitationCallback() {
            @Override
            public void onComplete(int sentCount) {
                Toast.makeText(OrganizerViewEntrantsActivity.this, 
                        "Successfully sent " + sentCount + " invitation" + (sentCount > 1 ? "s" : ""), 
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Sent " + sentCount + " invitations");
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(OrganizerViewEntrantsActivity.this, 
                        "Failed to send invitations: " + error, 
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to send invitations: " + error);
            }
        });
    }
    
    private void checkAndProcessSelection(String eventId) {
        EventSelectionHelper selectionHelper = new EventSelectionHelper();
        selectionHelper.checkAndProcessEventSelection(eventId, new EventSelectionHelper.SelectionCallback() {
            @Override
            public void onComplete(int selectedCount) {
                if (selectedCount > 0) {
                    loadEntrantsFromFirestore();
                }
            }
            
            @Override
            public void onError(String error) {
            }
        });
    }

    private void loadEntrantsFromFirestore() {
        String eventId = getIntent().getStringExtra("eventId");
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Missing eventId", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedList.clear();
        notSelectedList.clear();
        cancelledList.clear();

        db.collection("events").document(eventId).collection("SelectedEntrants").get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        selectedList.add(safeName(doc));
                    }
                    selectedAdapter.notifyDataSetChanged();
                });

        db.collection("events").document(eventId).collection("WaitlistedEntrants").get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        notSelectedList.add(safeName(doc));
                    }
                    notSelectedAdapter.notifyDataSetChanged();
                });

        db.collection("events").document(eventId).collection("CancelledEntrants").get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        cancelledList.add(safeName(doc));
                    }
                    cancelledAdapter.notifyDataSetChanged();
                });
    }

    private String safeName(DocumentSnapshot doc) {
        String name = doc.getString("name");
        if (name == null || name.trim().isEmpty()) {
            String full = doc.getString("fullName");
            if (full != null && !full.trim().isEmpty()) return full;
            String first = doc.getString("firstName");
            String last = doc.getString("lastName");
            if ((first != null && !first.isEmpty()) || (last != null && !last.isEmpty())) {
                return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            }
            name = "(unknown)";
        }
        return name;
    }
    
    private void showFinalEntrantListDialog() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Capture screenshot and blur it
        Bitmap screenshot = captureScreenshot();
        Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
        
        // Create custom dialog with full screen to show blur background
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.organizer_dialog_final_entrants);
        
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
        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurredBitmap != null && blurBackground != null) {
            blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
        }
        
        // Make the background clickable to dismiss
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Get the CardView for animation
        CardView cardView = dialog.findViewById(R.id.dialogCardView);
        
        // Get the container for entrant names
        LinearLayout entrantListContainer = dialog.findViewById(R.id.entrantListContainer);
        
        // Load admitted entrants
        loadAdmittedEntrants(eventId, entrantListContainer);
        
        // Set up Close button
        Button btnClose = dialog.findViewById(R.id.btnDialogOk);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
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
    
    private void loadAdmittedEntrants(String eventId, LinearLayout container) {
        if (container == null || eventId == null || eventId.isEmpty()) {
            return;
        }
        
        // Clear container first
        container.removeAllViews();
        
        // Show loading message
        TextView loadingText = new TextView(this);
        loadingText.setText("Loading final entrants...");
        loadingText.setTextSize(16);
        loadingText.setTextColor(0xFF223C65);
        loadingText.setPadding(16, 16, 16, 16);
        container.addView(loadingText);
        
        db.collection("events").document(eventId).collection("AdmittedEntrants")
                .get()
                .addOnSuccessListener((QuerySnapshot querySnapshot) -> {
                    container.removeAllViews();
                    
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        TextView emptyText = new TextView(this);
                        emptyText.setText("No final entrants yet. Entrants will appear here after they accept their invitations.");
                        emptyText.setTextSize(16);
                        emptyText.setTextColor(0xFF223C65);
                        emptyText.setPadding(16, 16, 16, 16);
                        emptyText.setGravity(android.view.Gravity.CENTER);
                        container.addView(emptyText);
                        return;
                    }
                    
                    List<String> admittedNames = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String name = safeName(doc);
                        admittedNames.add(name);
                    }
                    
                    // Sort names alphabetically
                    admittedNames.sort(String.CASE_INSENSITIVE_ORDER);
                    
                    // Add each name as a TextView
                    for (int i = 0; i < admittedNames.size(); i++) {
                        TextView nameText = new TextView(this);
                        nameText.setText((i + 1) + ". " + admittedNames.get(i));
                        nameText.setTextSize(18);
                        nameText.setTextColor(0xFF223C65);
                        nameText.setPadding(16, 12, 16, 12);
                        nameText.setTypeface(android.graphics.Typeface.DEFAULT);
                        
                        // Add divider except for last item
                        if (i < admittedNames.size() - 1) {
                            View divider = new View(this);
                            divider.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1
                            ));
                            divider.setBackgroundColor(0xFFE0E0E0);
                            container.addView(nameText);
                            container.addView(divider);
                        } else {
                            container.addView(nameText);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load admitted entrants", e);
                    container.removeAllViews();
                    TextView errorText = new TextView(this);
                    errorText.setText("Failed to load final entrants. Please try again.");
                    errorText.setTextSize(16);
                    errorText.setTextColor(0xFFFF0000);
                    errorText.setPadding(16, 16, 16, 16);
                    container.addView(errorText);
                });
    }
    
    private Bitmap captureScreenshot() {
        try {
            android.view.View rootView = getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screenshot", e);
            return null;
        }
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
            Log.e(TAG, "Failed to blur bitmap", e);
            return bitmap;
        }
    }
}


