package com.example.eventease.ui.organizer;

import android.content.Intent;
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
import com.example.eventease.R;
import com.example.eventease.ui.entrant.profile.ProfileDeletionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
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
    private String organizerId;
    private boolean isResolvingOrganizerId;
    private String userEmail;

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

        organizerId = getIntent().getStringExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID);

        if (tvFullName == null || ivAvatar == null) {
            Log.e(TAG, "organizer_account.xml must define tvFullName and ivAvatar");
            return;
        }

        tvFullName.setText("Loading...");
        Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in to view your account", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        updateUserIdLabel(user.getUid());
        loadProfile(user.getUid());
        resolveOrganizerId(() -> updateUserIdLabel(user.getUid()));

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

    private void updateUserIdLabel(String authUid) {
        if (tvUserId == null) {
            return;
        }
        String organizerLabel = (organizerId != null && !organizerId.trim().isEmpty())
                ? organizerId
                : "Loading…";
        StringBuilder sb = new StringBuilder();
        if (userEmail != null && !userEmail.isEmpty()) {
            sb.append("Email: ").append(userEmail).append('\n');
        }
        sb.append("Auth UID: ").append(authUid).append('\n');
        sb.append("Organizer ID: ").append(organizerLabel);
        tvUserId.setText(sb.toString());
    }

    private void resolveOrganizerId(@Nullable Runnable onReady) {
        if (organizerId != null && !organizerId.trim().isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            updateUserIdLabel(user != null ? user.getUid() : "");
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (isResolvingOrganizerId) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_LONG).show();
            return;
        }
        isResolvingOrganizerId = true;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    organizerId = doc != null ? doc.getString("organizerId") : null;
                    if (organizerId == null || organizerId.trim().isEmpty()) {
                        organizerId = doc != null ? doc.getId() : user.getUid();
                    }
                    isResolvingOrganizerId = false;
                    updateUserIdLabel(user.getUid());
                    if (organizerId == null || organizerId.trim().isEmpty()) {
                        Toast.makeText(this, "Organizer profile not configured yet.", Toast.LENGTH_LONG).show();
                    } else if (onReady != null) {
                        onReady.run();
                    }
                })
                .addOnFailureListener(e -> {
                    isResolvingOrganizerId = false;
                    Toast.makeText(this, "Failed to load organizer profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
    /**
     * Sets up the listeners for the custom bottom navigation bar.
     */
    private void setupBottomNavigation() {
        LinearLayout btnMyEvents = findViewById(R.id.btnMyEvents);
        if (btnMyEvents != null) {
            btnMyEvents.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, com.example.eventease.ui.organizer.OrganizerMyEventActivity.class);
                if (organizerId != null && !organizerId.trim().isEmpty()) {
                    intent.putExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID, organizerId);
                }
                startActivity(intent);
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
                android.content.Intent intent = new android.content.Intent(this, com.example.eventease.ui.organizer.OrganizerCreateEventActivity.class);
                if (organizerId != null && !organizerId.trim().isEmpty()) {
                    intent.putExtra(OrganizerMyEventActivity.EXTRA_ORGANIZER_ID, organizerId);
                }
                startActivity(intent);
            });
        }
        
        com.google.android.material.button.MaterialButton btnSwitchRole = findViewById(R.id.btnSwitchRole);
        if (btnSwitchRole != null) {
            btnSwitchRole.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.eventease.MainActivity.class);
                intent.putExtra("nav_target", "account");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
        
        com.google.android.material.button.MaterialButton btnDeleteProfile = findViewById(R.id.btnDeleteProfile);
        if (btnDeleteProfile != null) {
            btnDeleteProfile.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Profile")
                        .setMessage("Are you sure you want to delete your profile? This action cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> {
                            deleteProfile();
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
        if (documentId == null || documentId.trim().isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            documentId = user != null ? user.getUid() : "";
        }
        final String docId = documentId;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(docId)
                .get()
                .addOnSuccessListener(this::applyProfileFromDoc)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Fetch user failed for document: " + docId, e);
                    if (tvFullName != null)
                        tvFullName.setText("Organizer");
                    if (ivAvatar != null)
                        Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
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
            Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
            return;
        }

        String name = doc.getString("fullName");
        if (name == null || name.trim().isEmpty()) {
            name = doc.getString("name");
        }

        String photoUrl = doc.getString("photoUrl");
        userEmail = doc.getString("email");

        tvFullName.setText((name != null && !name.isEmpty()) ? name : "Organizer");

        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar);
        } else {
            Glide.with(this).load(R.drawable.entrant_icon).circleCrop().into(ivAvatar);
        }

        FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
        if (authUser != null) {
            updateUserIdLabel(authUser.getUid());
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

        if (organizerId == null || organizerId.trim().isEmpty()) {
            Toast.makeText(this, "Organizer profile not ready", Toast.LENGTH_SHORT).show();
            resolveOrganizerId(() -> uploadNewAvatar(uri));
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

    private void deleteProfile() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = currentUser.getUid();
        
        Toast.makeText(this, "Deleting profile...", Toast.LENGTH_SHORT).show();

        ProfileDeletionHelper deletionHelper = new ProfileDeletionHelper(this);
        deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
            @Override
            public void onDeletionComplete() {
                deleteUserDocumentAndAuth(uid);
            }

            @Override
            public void onDeletionFailure(String error) {
                Log.e(TAG, "Failed to delete user references: " + error);
                deleteUserDocumentAndAuth(uid);
            }
        });
    }

    private void deleteUserDocumentAndAuth(String uid) {
        DocumentReference userRef = FirebaseFirestore.getInstance().collection("users").document(uid);
        userRef.delete()
            .addOnSuccessListener(aVoid -> {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    currentUser.delete()
                        .addOnSuccessListener(aVoid1 -> {
                            FirebaseAuth.getInstance().signOut();
                            Toast.makeText(this, "Profile deleted successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to delete auth account", e);
                            FirebaseAuth.getInstance().signOut();
                            Toast.makeText(this, "Profile deleted (some cleanup may be pending)", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                } else {
                    Toast.makeText(this, "Profile deleted successfully", Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to delete user document", e);
                Toast.makeText(this, "Failed to delete profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }
}