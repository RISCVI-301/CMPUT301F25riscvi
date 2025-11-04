package com.EventEase.auth;

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

public class LoginFragment extends Fragment {

    private static final String PREFS_NAME = "EventEasePrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_SAVED_EMAIL = "savedEmail";
    private static final String KEY_SAVED_PASSWORD = "savedPassword";

    private com.EventEase.auth.AuthViewModel vm;
    private boolean navigateOnSuccess = false; // only true for login, not for reset
    private boolean isPasswordVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm = new ViewModelProvider(this).get(com.EventEase.auth.AuthViewModel.class);

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
                btnTogglePassword.setImageResource(R.drawable.ic_eye_off);
                isPasswordVisible = false;
            } else {
                // Show password
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePassword.setImageResource(R.drawable.ic_eye_on);
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
                Toast.makeText(requireContext(), s.error, Toast.LENGTH_LONG).show();
            }
            if (s.success && navigateOnSuccess) {
                // Successful login → save Remember Me preference and navigate
                SharedPreferences.Editor editor = prefs.edit();
                String emailText = email.getText().toString().trim();
                String passwordText = password.getText().toString();
                
                if (checkboxRememberMe.isChecked()) {
                    editor.putBoolean(KEY_REMEMBER_ME, true);
                    editor.putString(KEY_SAVED_EMAIL, emailText);
                    editor.putString(KEY_SAVED_PASSWORD, passwordText);
                } else {
                    editor.putBoolean(KEY_REMEMBER_ME, false);
                    editor.remove(KEY_SAVED_EMAIL);
                    editor.remove(KEY_SAVED_PASSWORD);
                }
                editor.apply();

                try {
                    if (isAdded() && getView() != null) {
                        NavHostFragment.findNavController(LoginFragment.this).navigate(R.id.action_login_to_discover);
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Navigation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
                navigateOnSuccess = false; // reset
            } else if (s.success && !navigateOnSuccess) {
                // Success for reset password path
                Toast.makeText(requireContext(), "Reset email sent", Toast.LENGTH_LONG).show();
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