package com.example.eventease.ui.entrant.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.eventease.R;

/**
 * Fragment for editing user profile information.
 * Allows updating name, phone number, email, password, and profile picture.
 */
public class EditProfileFragment extends Fragment {
    private EditText nameField;
    private EditText phoneField;
    private EditText emailField;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    
    private ProfileImageHelper imageHelper;
    private ProfileUpdateHelper updateHelper;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize helpers early (before onCreateView) for activity result registration
        // ProfileImageHelper will be fully initialized in onCreateView after views are created
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_edit_profile, container, false);
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        nameField = root.findViewById(R.id.editNameField);
        emailField = root.findViewById(R.id.editEmailField);
        phoneField = root.findViewById(R.id.editPhoneField);
        profileImage = root.findViewById(R.id.profileImageEdit);
        
        // Initialize helpers (after views are created)
        imageHelper = new ProfileImageHelper(this, profileImage, uri -> {
            // Image selected callback - uri is already set in helper
        });
        updateHelper = new ProfileUpdateHelper(requireContext());
        
        // Load current user data
        loadUserData();
        
        // Set up click listeners
        root.findViewById(R.id.backButton).setOnClickListener(v -> {
            // Ensure Remember Me is set before navigating back to keep user logged in
            SessionManager.ensureRememberMeSet(requireContext());
            navigateBackSafely();
        });
        
        root.findViewById(R.id.editProfilePicture).setOnClickListener(v -> 
            new ImageSourceDialog(requireActivity(), imageHelper));
        
        root.findViewById(R.id.saveButton).setOnClickListener(v -> saveChanges());
        
        // Device auth - email/password changes not needed
        View changeEmailBtn = root.findViewById(R.id.changeEmailButton);
        View changePasswordBtn = root.findViewById(R.id.changePasswordButton);
        if (changeEmailBtn != null) changeEmailBtn.setVisibility(View.GONE);
        if (changePasswordBtn != null) changePasswordBtn.setVisibility(View.GONE);
        
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Device auth - no email sync needed
    }
    
    private void loadUserData() {
        String uid = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (uid != null && !uid.isEmpty()) {
            DocumentReference userRef = db.collection("users").document(uid);
            
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Set the hints with current data
                    nameField.setHint(documentSnapshot.getString("name"));
                    phoneField.setHint(documentSnapshot.getString("phoneNumber"));
                    if (emailField != null) {
                        emailField.setHint(documentSnapshot.getString("email"));
                    }
                    
                    // Load profile image
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
                        Glide.with(getContext())
                            .load(photoUrl)
                            .placeholder(R.drawable.entrant_icon)
                            .error(R.drawable.entrant_icon)
                            .into(profileImage);
                    }
                }
            });
        }
    }
    
    private void saveChanges() {
        String newName = nameField.getText().toString();
        String newPhone = phoneField.getText().toString();
        String newEmail = emailField != null ? emailField.getText().toString() : "";
        Uri imageUri = imageHelper.getSelectedImageUri();
        
        updateHelper.saveChanges(newName, newPhone, newEmail, imageUri, new ProfileUpdateHelper.UpdateCallback() {
            @Override
            public void onUpdateSuccess() {
                navigateBackSafely();
            }
            
            @Override
            public void onUpdateFailure(String error) {
                // Error already shown via ToastUtil in helper
            }
        });
    }
    
    private void navigateBackSafely() {
        View view = getView();
        if (view != null) {
            try {
                Navigation.findNavController(view).navigateUp();
            } catch (Exception e) {
                // Fallback: try to navigate back using the activity
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }
        }
    }
}
