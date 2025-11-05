package com.example.eventease;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;

public class AccountFragment extends Fragment {
    private TextView fullNameText;
    private ShapeableImageView profileImage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.entrant_fragment_account, container, false);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        fullNameText = view.findViewById(R.id.fullNameText);
        profileImage = view.findViewById(R.id.profileImage);
        
        // Load user data
        loadUserData();
        
        return view;
    }
    
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            // First check if this UID exists in our users collection
            DocumentReference userRef = db.collection("users").document(uid);
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Get user name from Firebase - exactly as stored in Firestore
                    String name = documentSnapshot.getString("name");
                    if (name != null && !name.isEmpty()) {
                        fullNameText.setText(name);
                    }
                    
                    // Get profile photo URL from Firebase - exactly as stored in Firestore
                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        // Load image using Glide
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.entrant_icon)
                            .error(R.drawable.entrant_icon)
                            .into(profileImage);
                    }
                }
            }).addOnFailureListener(e -> {
                // Handle any errors here
                e.printStackTrace();
            });
        }
    }
}