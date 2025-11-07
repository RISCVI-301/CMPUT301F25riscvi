package com.example.eventease;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.eventease.util.AuthHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
/**
 * Manages the organizer's account screen.
 * <p>
 * This activity displays the organizer's profile information, such as their name and avatar.
 * It fetches this data from the 'users' collection in Firestore. It also provides functionality
 * for the organizer to update their profile picture and navigate to other parts of the app.
 */
public class OrganizerAccountActivity extends AppCompatActivity {

    private static final String TAG = "OrganizerAccount";

    private ImageView ivAvatar;
    private TextView tvFullName;
    private TextView tvUserId;
    private ImageButton btnEditProfile;

    /**
     * Handles the result of the image picker to select a new avatar.
     * On selection, triggers the upload process.
     */
    private final androidx.activity.result.ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadNewAvatar(uri);
            });
    /**
     * Initializes the activity, view components, and loads the organizer's profile data.
     * @param savedInstanceState If the activity is being re-initialized, this Bundle contains the most recent data.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organizer_account);

        ivAvatar = findViewById(R.id.ivAvatar);
        tvFullName = findViewById(R.id.tvFullName);
        tvUserId = findViewById(R.id.tvUserId);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        if (tvFullName == null || ivAvatar == null) {
            Log.e(TAG, "organizer_account.xml must define tvFullName and ivAvatar");
            return;
        }

        tvFullName.setText("Loading...");
        Glide.with(this).load(R.drawable.ic_launcher_foreground).circleCrop().into(ivAvatar);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in to view your account", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        if (tvUserId != null) {
            String organizerId = AuthHelper.getCurrentOrganizerIdOrNull();
            String displayText = "Auth UID: " + user.getUid() + "\nOrganizer ID: " + 
                    (organizerId != null ? organizerId : "N/A");
            tvUserId.setText(displayText);
        }
        
        String organizerId = AuthHelper.getCurrentOrganizerIdOrNull();
        if (organizerId != null) {
            loadProfile(organizerId);
        } else {
            loadProfile(user.getUid());
        }

        ivAvatar.setOnClickListener(v -> pickImage.launch("image/*"));

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(this)
                            .setMessage("Open Edit Profile screen?")
                            .setPositiveButton("Yes", (d, w) -> {
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        }
        
        setupBottomNavigation();
    }
    /**
     * Sets up the listeners for the custom bottom navigation bar.
     */
    private void setupBottomNavigation() {
        LinearLayout btnMyEvents = findViewById(R.id.btnMyEvents);
        if (btnMyEvents != null) {
            btnMyEvents.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, com.example.eventease.ui.organizer.OrganizerMyEventActivity.class));
                finish();
            });
        }
        
        LinearLayout btnAccount = findViewById(R.id.btnAccount);
        if (btnAccount != null) {
            btnAccount.setOnClickListener(v -> recreate());
        }
        
        com.google.android.material.floatingactionbutton.FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, com.example.eventease.ui.organizer.OrganizerCreateEventActivity.class));
            });
        }
        
        com.google.android.material.button.MaterialButton btnSwitchRole = findViewById(R.id.btnSwitchRole);
        if (btnSwitchRole != null) {
            btnSwitchRole.setOnClickListener(v -> {
                Toast.makeText(this, "Switch to entrant view - Coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        
        com.google.android.material.button.MaterialButton btnDeleteProfile = findViewById(R.id.btnDeleteProfile);
        if (btnDeleteProfile != null) {
            btnDeleteProfile.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Profile")
                        .setMessage("Are you sure you want to delete your profile? This action cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> {
                            Toast.makeText(this, "Delete profile - Coming soon", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }
    /**
     * Loads the user's profile data from a specific document in the 'users' collection.
     *
     * @param documentId The ID of the user document to fetch (could be an Auth UID or a custom organizer ID).
     */
    private void loadProfile(String documentId) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(documentId)
                .get()
                .addOnSuccessListener(this::applyProfileFromDoc)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Fetch user failed for document: " + documentId, e);
                    if (!documentId.equals("organizer_test_1")) {
                        loadProfile("organizer_test_1");
                    } else {
                        if (tvFullName != null)
                            tvFullName.setText("Organizer");
                        if (ivAvatar != null)
                            Glide.with(this).load(R.drawable.ic_launcher_foreground).circleCrop().into(ivAvatar);
                    }
                });
    }
    /**
     * Applies the fetched profile data to the UI elements.
     *
     * @param doc The DocumentSnapshot retrieved from Firestore containing the user's data.
     */
    private void applyProfileFromDoc(DocumentSnapshot doc) {
        if (tvFullName == null || ivAvatar == null) return;

        if (doc == null || !doc.exists()) {
            Log.w(TAG, "User document not found");
            tvFullName.setText("Organizer");
            Glide.with(this).load(R.drawable.ic_launcher_foreground).circleCrop().into(ivAvatar);
            return;
        }

        String name = doc.getString("fullName");
        String photoUrl = doc.getString("photoUrl");

        tvFullName.setText((name != null && !name.isEmpty()) ? name : "Organizer");

        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar);
        } else {
            Glide.with(this).load(R.drawable.ic_launcher_foreground).circleCrop().into(ivAvatar);
        }
    }
    /**
     * Uploads a new avatar image to Firebase Storage and updates the URL
     * in the user's Firestore document.
     *
     * @param uri The local URI of the image to be uploaded.
     */
    private void uploadNewAvatar(Uri uri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String organizerId = AuthHelper.getCurrentOrganizerIdOrNull();
        if (organizerId == null) {
            Toast.makeText(this, "Organizer ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("profilePhotos/" + organizerId + ".jpg");

        ref.putFile(uri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(download -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("photoUrl", download.toString());
                    FirebaseFirestore.getInstance().collection("users")
                            .document(organizerId)
                            .set(update, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener(v -> {
                            if (ivAvatar != null)
                                Glide.with(this).load(download).circleCrop().into(ivAvatar);
                            Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Save URL failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}