package com.example.eventease;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class ViewEntrantsActivity extends AppCompatActivity {

    private ListView listSelected, listNotSelected, listCancelled;
    private Button btnRemoveCancelled;
    private FirebaseFirestore db;

    private List<Entrant> selectedList = new ArrayList<>();
    private List<Entrant> notSelectedList = new ArrayList<>();
    private List<Entrant> cancelledList = new ArrayList<>();

    private EntrantAdapter selectedAdapter;
    private EntrantAdapter notSelectedAdapter;
    private EntrantAdapter cancelledAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewentrants);

        listSelected = findViewById(R.id.recyclerSelected);
        listNotSelected = findViewById(R.id.recyclerNotSelected);
        listCancelled = findViewById(R.id.recyclerCancelled);
        btnRemoveCancelled = findViewById(R.id.btnRemCancelledEntrants);

        db = FirebaseFirestore.getInstance();

        selectedAdapter = new EntrantAdapter(this, selectedList);
        notSelectedAdapter = new EntrantAdapter(this, notSelectedList);
        cancelledAdapter = new EntrantAdapter(this, cancelledList);

        listSelected.setAdapter(selectedAdapter);
        listNotSelected.setAdapter(notSelectedAdapter);
        listCancelled.setAdapter(cancelledAdapter);

        loadEntrantsFromFirestore();

        btnRemoveCancelled.setOnClickListener(v -> removeCancelledEntrants());
    }

    private void loadEntrantsFromFirestore() {
        db.collection("entrants") // Firestore collection
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    selectedList.clear();
                    notSelectedList.clear();
                    cancelledList.clear();

                    List<DocumentSnapshot> documents = queryDocumentSnapshots.getDocuments();

                    for (DocumentSnapshot doc : documents) {
                        Entrant entrant = doc.toObject(Entrant.class);
                        if (entrant != null) {
                            switch (entrant.getStatus()) {
                                case "selected":
                                    selectedList.add(entrant);
                                    break;
                                case "not_selected":
                                    notSelectedList.add(entrant);
                                    break;
                                case "cancelled":
                                    cancelledList.add(entrant);
                                    break;
                            }
                        }
                    }

                    selectedAdapter.notifyDataSetChanged();
                    notSelectedAdapter.notifyDataSetChanged();
                    cancelledAdapter.notifyDataSetChanged();

                })
                .addOnFailureListener(e ->
                        Toast.makeText(ViewEntrantsActivity.this, "Failed to load entrants.", Toast.LENGTH_SHORT).show()
                );
    }

    private void removeCancelledEntrants() {
        db.collection("entrants")
                .whereEqualTo("status", "cancelled")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        db.collection("entrants").document(doc.getId()).delete();
                    }
                    Toast.makeText(ViewEntrantsActivity.this, "Cancelled entrants removed.", Toast.LENGTH_SHORT).show();
                    loadEntrantsFromFirestore(); // refresh lists
                })
                .addOnFailureListener(e -> {});
    }
}
