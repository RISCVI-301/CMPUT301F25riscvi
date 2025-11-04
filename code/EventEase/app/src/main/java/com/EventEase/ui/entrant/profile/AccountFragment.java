package com.EventEase.ui.entrant.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.eventease.R;

public class AccountFragment extends Fragment {
    private TextView fullNameText;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    public AccountFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_account, container, false);
        
        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        fullNameText = root.findViewById(R.id.fullNameText);
        profileImage = root.findViewById(R.id.profileImage);

        // Set up click listeners
        root.findViewById(R.id.notificationsCard).setOnClickListener(v -> {
            // Handle notifications click
        });

        root.findViewById(R.id.applyOrganizerCard).setOnClickListener(v -> {
            // Handle apply for organizer click
        });

        root.findViewById(R.id.deleteProfileButton).setOnClickListener(v -> {
            // Handle delete profile click
        });

        root.findViewById(R.id.settingsButton).setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_accountFragment_to_editProfileFragment));
        
        // Load user data with real-time updates
        loadUserData();
        
        return root;
    }
    
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Get the current user's document in the users collection
            DocumentReference userRef = db.collection("users").document(currentUser.getUid());
            
            // Listen for real-time updates
            userRef.addSnapshotListener((documentSnapshot, e) -> {
                if (e != null) {
                    // Handle any errors
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    // Get and set the user's name
                    String name = documentSnapshot.getString("name");
                    if (name != null && !name.isEmpty()) {
                        fullNameText.setText(name);
                    }
                    
                    // Get and load the profile photo
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
                        // Use Glide to load and cache the image
                        Glide.with(getContext())
                            .load(photoUrl)
                            .placeholder(R.drawable.icon)
                            .error(R.drawable.icon)
                            .into(profileImage);
                    }
                }
            });
        }
    }
}
