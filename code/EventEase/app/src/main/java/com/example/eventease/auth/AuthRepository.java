package com.example.eventease.auth;


import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository for authentication operations.
 * Handles user login, signup, and password reset with Firebase Authentication.
 */
public class AuthRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();


    public Task<AuthResult> login(String email, String password) {
        return auth.signInWithEmailAndPassword(email.trim(), password);
    }


    public Task<Void> signup(String name, String email, String password, String phoneNumber) {
        return auth.createUserWithEmailAndPassword(email.trim(), password).onSuccessTask((SuccessContinuation<AuthResult, Void>) result -> {
                    if (auth.getCurrentUser() == null) {
                        throw new IllegalStateException("No user after sign up");
                    }
                    String uid = auth.getCurrentUser().getUid();
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("uid", uid);
                    profile.put("email", email.trim());
                    profile.put("name", name.trim());
                    if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                        profile.put("phoneNumber", phoneNumber.trim());
                    }
                    profile.put("createdAt", System.currentTimeMillis());
                    profile.put("roles", Arrays.asList("entrant"));
                    return db.collection("users").document(uid).set(profile);
                });
    }


    public Task<Void> sendPasswordReset(String email) {
        return auth.sendPasswordResetEmail(email.trim());
    }


    public FirebaseAuth getAuth() { return auth; }
}