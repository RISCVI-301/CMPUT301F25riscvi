package com.EventEase.ui.entrant.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.ProfileRepository;
import com.EventEase.model.Profile;
import com.example.eventease.App;        // ✅ shared DevGraph
import com.example.eventease.R;
import com.google.android.gms.tasks.Task;

public class ProfileActivity extends AppCompatActivity {

    private ProfileRepository repo;
    private AuthManager auth;

    private EditText nameField;
    private EditText emailField;
    private EditText phoneField;
    private Button saveBtn;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // ✅ use the app-wide DevGraph
        repo = App.graph().profiles;
        auth = App.graph().auth;

        nameField = findViewById(R.id.profile_name);
        emailField = findViewById(R.id.profile_email);
        phoneField = findViewById(R.id.profile_phone);
        saveBtn   = findViewById(R.id.profile_save);
        progress  = findViewById(R.id.profile_progress);

        loadProfile();
        saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveBtn.setEnabled(!loading);
    }

    private void loadProfile() {
        setLoading(true);
        final String uid = auth.getUid();
        repo.get(uid)
                .addOnSuccessListener(p -> {
                    if (p != null) {
                        nameField.setText(safe(p.getDisplayName()));
                        emailField.setText(safe(p.getEmail()));
                        phoneField.setText(safe(p.getPhoneNumber()));
                    }
                    setLoading(false);
                })
                .addOnFailureListener(e -> {
                    // fine if not found yet
                    setLoading(false);
                });
    }

    private void saveProfile() {
        final String uid = auth.getUid();
        final String name = trim(nameField.getText());
        final String email = trim(emailField.getText());
        final String phone = trim(phoneField.getText());

        if (TextUtils.isEmpty(name)) {
            nameField.setError("Name required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            emailField.setError("Email required");
            return;
        }

        setLoading(true);

        Profile p = new Profile();
        p.setUid(uid);
        p.setDisplayName(name);
        p.setEmail(email);
        p.setPhoneNumber(phone);

        Task<Void> t = repo.upsert(uid, p);
        t.addOnSuccessListener(v -> {
                    setLoading(false);
                    Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                    finish(); // optional
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String trim(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
}
