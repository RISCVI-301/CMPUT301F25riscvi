package com.example.eventease.ui.organizer;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

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
}


