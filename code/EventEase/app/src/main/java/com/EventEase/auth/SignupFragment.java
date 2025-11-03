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
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;


import com.example.eventease.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;


public class SignupFragment extends Fragment {


    private com.EventEase.auth.AuthViewModel vm;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_signup, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Store reference to the view for navigation
        final View rootView = view;


        vm = new ViewModelProvider(this).get(com.EventEase.auth.AuthViewModel.class);


        final TextInputEditText name = view.findViewById(R.id.displayName);
        final TextInputEditText email = view.findViewById(R.id.email);
        final TextInputEditText password = view.findViewById(R.id.password);
        final TextInputEditText confirm = view.findViewById(R.id.confirm);
        final MaterialButton btnSignup = view.findViewById(R.id.btnSignup);
        final MaterialButton btnToLogin = view.findViewById(R.id.btnToLogin);
        final ProgressBar progress = view.findViewById(R.id.progress);
        
        if (btnToLogin == null) {
            Toast.makeText(requireContext(), "Button not found", Toast.LENGTH_SHORT).show();
            return;
        }


// Observe ViewModel state
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            progress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
            if (s.error != null) {
                Toast.makeText(requireContext(), s.error, Toast.LENGTH_LONG).show();
            }
            if (s.success) {
// Successful sign up → go to Discover
                try {
                    if (isAdded() && getView() != null) {
                        NavHostFragment.findNavController(SignupFragment.this).navigate(R.id.action_signup_to_discover);
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Navigation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });


        btnSignup.setOnClickListener(v -> {
            String n = name.getText() == null ? "" : name.getText().toString().trim();
            String e = email.getText() == null ? "" : email.getText().toString().trim();
            String p = password.getText() == null ? "" : password.getText().toString();
            String c = confirm.getText() == null ? "" : confirm.getText().toString();
            vm.signup(n, e, p, c);
        });


        btnToLogin.setOnClickListener(v -> {
            try {
                // Use Navigation.findNavController with the root view
                Navigation.findNavController(rootView).navigate(R.id.action_signup_to_login);
            } catch (IllegalArgumentException e) {
                // If that fails, try with the fragment
                try {
                    NavHostFragment.findNavController(this).navigate(R.id.action_signup_to_login);
                } catch (Exception e2) {
                    Toast.makeText(requireContext(), "Navigation failed: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                    e2.printStackTrace();
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });
    }
}