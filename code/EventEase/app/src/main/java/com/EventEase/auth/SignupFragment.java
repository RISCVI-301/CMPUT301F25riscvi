package com.EventEase.auth;


import android.content.Context;
import android.content.SharedPreferences;
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
import com.EventEase.util.ToastUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;


public class SignupFragment extends Fragment {

    private static final String PREFS_NAME = "EventEasePrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_SAVED_UID = "savedUid";

    private com.EventEase.auth.AuthViewModel vm;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_signup, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Store reference to the view for navigation
        final View rootView = view;


        vm = new ViewModelProvider(this).get(com.EventEase.auth.AuthViewModel.class);


        final android.widget.EditText firstName = view.findViewById(R.id.firstName);
        final android.widget.EditText lastName = view.findViewById(R.id.lastName);
        final android.widget.EditText email = view.findViewById(R.id.email);
        final android.widget.EditText password = view.findViewById(R.id.password);
        final android.widget.EditText confirm = view.findViewById(R.id.confirm);
        final android.widget.EditText phoneNumber = view.findViewById(R.id.phoneNumber);
        final android.view.View btnSignup = view.findViewById(R.id.btnSignup);
        final MaterialButton btnToLogin = view.findViewById(R.id.btnToLogin);
        final ProgressBar progress = view.findViewById(R.id.progress);
        
        if (btnSignup == null) {
            ToastUtil.showShort(requireContext(), "Signup button not found");
            return;
        }


// Observe ViewModel state
        vm.getState().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            progress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
            if (s.error != null) {
                ToastUtil.showLong(requireContext(), s.error);
            }
            if (s.success) {
                // Successful sign up → automatically enable Remember Me with UID
                com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                        .putBoolean(KEY_REMEMBER_ME, true)
                        .putString(KEY_SAVED_UID, currentUser.getUid())
                        .apply();
                }
                
                // Navigate to Upload Profile Picture
                try {
                    if (isAdded() && getView() != null) {
                        NavHostFragment.findNavController(SignupFragment.this).navigate(R.id.action_signup_to_upload);
                    }
                } catch (Exception e) {
                    ToastUtil.showLong(requireContext(), "Navigation error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });


        btnSignup.setOnClickListener(v -> {
            String first = firstName.getText() == null ? "" : firstName.getText().toString().trim();
            String last = lastName.getText() == null ? "" : lastName.getText().toString().trim();
            String fullName = (first + " " + last).trim();
            String e = email.getText() == null ? "" : email.getText().toString().trim();
            String p = password.getText() == null ? "" : password.getText().toString();
            String c = confirm.getText() == null ? "" : confirm.getText().toString();
            String phone = phoneNumber.getText() == null ? "" : phoneNumber.getText().toString().trim();
            
            vm.signup(fullName, e, p, c, phone);
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