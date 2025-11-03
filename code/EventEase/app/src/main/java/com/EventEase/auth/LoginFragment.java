package com.EventEase.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;



import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;


public class LoginFragment extends Fragment {


    private com.EventEase.auth.AuthViewModel vm;
    private boolean navigateOnSuccess = false; // only true for login, not for reset


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        vm = new ViewModelProvider(this).get(com.EventEase.auth.AuthViewModel.class);


        final TextInputEditText email = view.findViewById(R.id.email);
        final TextInputEditText password = view.findViewById(R.id.password);
        final MaterialButton btnLogin = view.findViewById(R.id.btnLogin);
        final MaterialButton btnForgot = view.findViewById(R.id.btnForgot);
        final MaterialButton btnToSignup = view.findViewById(R.id.btnToSignup);
        final ProgressBar progress = view.findViewById(R.id.progress);


// Observe ViewModel state
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            progress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
            if (s.error != null) {
                Toast.makeText(requireContext(), s.error, Toast.LENGTH_LONG).show();
            }
            if (s.success && navigateOnSuccess) {
// Successful login → go to Discover
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
            String e = email.getText() == null ? "" : email.getText().toString().trim();
            String p = password.getText() == null ? "" : password.getText().toString();
            navigateOnSuccess = true;
            vm.login(e, p);
        });


        btnForgot.setOnClickListener(v -> {
            String e = email.getText() == null ? "" : email.getText().toString().trim();
            navigateOnSuccess = false; // do not navigate for reset
            vm.resetPassword(e);
        });


        btnToSignup.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_signup)
        );
    }
}