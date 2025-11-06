package com.example.eventease;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
public class OrganizerAccountActivity extends AppCompatActivity {

    private static final String TAG = "OrganizerAccount";
    private static final String CURRENT_USER_ID = "organizer_test_1"; // dev user

    private ImageView ivAvatar;
    private TextView tvFullName;
    private ImageButton btnEditProfile;

    private final androidx.activity.result.ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadNewAvatar(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organizer_account);

        ivAvatar = findViewById(R.id.ivAvatar);
        tvFullName = findViewById(R.id.tvFullName);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        if (tvFullName == null || ivAvatar == null) {
            Log.e(TAG, "organizer_account.xml must define tvFullName and ivAvatar");
            // Avoid crash: create no-op placeholders
            return;
        }

        // Show loading placeholder before Firestore returns
        tvFullName.setText("Loading...");
        Glide.with(this).load(R.drawable.ic_launcher_foreground).circleCrop().into(ivAvatar);

        // Load profile from fixed dev doc
        loadProfile();

        // Allow picking a photo (will work when auth added)
        ivAvatar.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Sign in required to change photo", Toast.LENGTH_SHORT).show();
            } else {
                pickImage.launch("image/*");
            }
        });

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(this)
                            .setMessage("Open Edit Profile screen?")
                            .setPositiveButton("Yes", (d, w) -> {
                                // startActivity(new Intent(this, OrganizerEditProfileActivity.class));
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        }
    }

    /** Load from Firestore dev doc: users/organizer_test_1 */
    private void loadProfile() {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(CURRENT_USER_ID)
                .get()
                .addOnSuccessListener(this::applyProfileFromDoc)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Fetch user failed", e);
                    if (tvFullName != null)
                        tvFullName.setText("Organizer");
                    if (ivAvatar != null)
                        Glide.with(this).load(R.drawable.ic_launcher_foreground).circleCrop().into(ivAvatar);
                });
    }

    private void applyProfileFromDoc(DocumentSnapshot doc) {
        if (tvFullName == null || ivAvatar == null) return;

        if (doc == null || !doc.exists()) {
            Log.w(TAG, "users/" + CURRENT_USER_ID + " not found");
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

    /** Will work after you add Firebase Auth sign-in. */
    private void uploadNewAvatar(Uri uri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("profilePhotos/" + uid + ".jpg");

        ref.putFile(uri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(download -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("photoUrl", download.toString());
                    FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
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