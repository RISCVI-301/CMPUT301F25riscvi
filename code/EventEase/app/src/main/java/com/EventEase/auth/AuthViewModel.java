package com.EventEase.auth;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel for authentication operations.
 * Manages login, signup, and password reset state.
 */
public class AuthViewModel extends ViewModel {
    public static class State {
        public boolean loading;
        public String error;
        public boolean success;
    }


    private final AuthRepository repo = new AuthRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>(new State());


    public LiveData<State> getState() { return state; }


    private void post(boolean loading, String error, boolean success) {
        State s = new State();
        s.loading = loading; s.error = error; s.success = success; state.postValue(s);
    }


    public void login(String email, String password) {
        if (!email.contains("@")) { post(false, "Enter a valid email", false); return; }
        if (password.length() < 6) { post(false, "Password must be ≥ 6 chars", false); return; }
        post(true, null, false);
        repo.login(email, password).addOnCompleteListener(t -> {
            if (t.isSuccessful()) post(false, null, true); else post(false, message(t.getException()), false);
        });
    }


    public void signup(String name, String email, String password, String confirm, String phoneNumber) {
        if (name == null || name.trim().isEmpty()) { post(false, "Please enter your name", false); return; }
        if (!email.contains("@")) { post(false, "Enter a valid email", false); return; }
        if (password.length() < 6) { post(false, "Password must be ≥ 6 chars", false); return; }
        if (!password.equals(confirm)) { post(false, "Passwords do not match", false); return; }
        post(true, null, false);
        repo.signup(name, email, password, phoneNumber).addOnCompleteListener(t -> {
            if (t.isSuccessful()) post(false, null, true); else post(false, message(t.getException()), false);
        });
    }


    public void resetPassword(String email) {
        if (!email.contains("@")) { post(false, "Enter a valid email", false); return; }
        post(true, null, false);
        repo.sendPasswordReset(email).addOnCompleteListener(t -> {
            if (t.isSuccessful()) post(false, null, true); else post(false, message(t.getException()), false);
        });
    }


    private String message(Exception e) {
        if (e == null || e.getMessage() == null) return "Something went wrong";
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("already in use")) return "Email already in use";
        if (msg.contains("badly formatted")) return "Invalid email format";
        if (msg.contains("password is invalid")) return "Incorrect password";
        if (msg.contains("no user record")) return "No account found for this email";
        if (msg.contains("configuration_not_found")) return "Firebase configuration error. Please ensure Email/Password authentication is enabled in Firebase Console and SHA fingerprints are added.";
        if (msg.contains("recaptcha")) return "Authentication setup incomplete. Please check Firebase Console configuration.";
        return e.getMessage();
    }
}