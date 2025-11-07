package com.example.eventease.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.eventease.R;
import com.example.eventease.util.ToastUtil;

/**
 * Fragment for user login with email and password.
 * Handles authentication, remember me functionality, and password visibility toggle.
 */
public class LoginFragment extends Fragment {

    private static final String PREFS_NAME = "EventEasePrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_SAVED_UID = "savedUid";
    private static final String KEY_SAVED_EMAIL = "savedEmail"; //Auto fill
    private static final String KEY_SAVED_PASSWORD = "savedPassword"; // Auto fill

    private com.example.eventease.auth.AuthViewModel vm;
    private boolean navigateOnSuccess = false; // only true for login
    private boolean isPasswordVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm = new ViewModelProvider(this).get(com.example.eventease.auth.AuthViewModel.class);

        final EditText email = view.findViewById(R.id.email);
        final EditText password = view.findViewById(R.id.password);
        final Button btnLogin = view.findViewById(R.id.btnLogin);
        final TextView btnForgot = view.findViewById(R.id.btnForgot);
        final CheckBox checkboxRememberMe = view.findViewById(R.id.checkboxRememberMe);
        final ImageButton btnTogglePassword = view.findViewById(R.id.btnTogglePassword);
        final ProgressBar progress = view.findViewById(R.id.progress);

        // Load saved credentials if Remember Me was checked
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false);
        if (rememberMe) {
            String savedEmail = prefs.getString(KEY_SAVED_EMAIL, "");
            String savedPassword = prefs.getString(KEY_SAVED_PASSWORD, "");
            email.setText(savedEmail);
            password.setText(savedPassword);
            checkboxRememberMe.setChecked(true);
        }

        // Password visibility toggle
        btnTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                // Hide password
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.entrant_ic_eye_off);
                isPasswordVisible = false;
            } else {
                // Show password
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.entrant_ic_eye_on);
                isPasswordVisible = true;
            }
            // Move cursor to end of text
            password.setSelection(password.getText().length());
        });

        // Observe ViewModel state
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            progress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
            if (s.error != null) {
                ToastUtil.showLong(requireContext(), s.error);
            }
            if (s.success && navigateOnSuccess) {
                // Successful login → save Remember Me preference and check role
                SharedPreferences.Editor editor = prefs.edit();
                String emailText = email.getText().toString().trim();
                String passwordText = password.getText().toString();
                
                // Save UID for Remember Me (this persists even if email/password changes)
                com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (checkboxRememberMe.isChecked() && currentUser != null) {
                    editor.putBoolean(KEY_REMEMBER_ME, true);
                    editor.putString(KEY_SAVED_UID, currentUser.getUid());
                    // Still save email for auto-fill convenience, but UID is what matters for auth persistence
                    editor.putString(KEY_SAVED_EMAIL, emailText);
                    editor.putString(KEY_SAVED_PASSWORD, passwordText);
                } else {
                    editor.putBoolean(KEY_REMEMBER_ME, false);
                    editor.remove(KEY_SAVED_UID);
                    editor.remove(KEY_SAVED_EMAIL);
                    editor.remove(KEY_SAVED_PASSWORD);
                }
                editor.apply();

                // Check if user is admin and navigate accordingly
                if (currentUser != null) {
                    UserRoleChecker.isAdmin().addOnCompleteListener(task -> {
                        if (isAdded() && getView() != null) {
                            try {
                                if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                                    // User is admin - navigate to admin flow
                                    android.content.Intent adminIntent = new android.content.Intent(requireContext(), com.example.eventease.admin.AdminMainActivity.class);
                                    startActivity(adminIntent);
                                    if (getActivity() != null) {
                                        getActivity().finish();
                                    }
                                } else {
                                    // User is not admin - navigate to entrant flow
                                    NavHostFragment.findNavController(LoginFragment.this).navigate(R.id.action_login_to_discover);
                                }
                            } catch (Exception e) {
                                ToastUtil.showLong(requireContext(), "Navigation error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        navigateOnSuccess = false; // reset
                    });
                } else {
                    navigateOnSuccess = false; // reset
                }
            } else if (s.success && !navigateOnSuccess) {
                // Success for reset password path
                ToastUtil.showLong(requireContext(), "Reset email sent");
            }
        });

        btnLogin.setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();
            navigateOnSuccess = true;
            vm.login(e, p);
        });

        btnForgot.setOnClickListener(v -> {
            // Navigate to forgot password fragment
            try {
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_forgotPassword);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Navigation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}