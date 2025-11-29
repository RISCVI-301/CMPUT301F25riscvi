package com.example.eventease.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.eventease.R;

/**
 * Welcome screen fragment that provides navigation to login or signup.
 */
public class WelcomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_welcome, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnSignup = view.findViewById(R.id.btnWelcomeSignup);
        Button btnLogin = view.findViewById(R.id.btnWelcomeLogin);

        btnSignup.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_welcome_to_signup);
        });

        btnLogin.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_welcome_to_login);
        });
    }
}

