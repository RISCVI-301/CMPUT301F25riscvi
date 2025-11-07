package com.example.eventease.ui.organizer;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class OrganizerViewEntrantsActivity extends AppCompatActivity {

    private ListView listSelected, listNotSelected, listCancelled;
    private FirebaseFirestore db;

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

        loadEntrantsFromFirestore();
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

        db.collection("events").document(eventId).collection("NonSelectedEntrants").get()
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


