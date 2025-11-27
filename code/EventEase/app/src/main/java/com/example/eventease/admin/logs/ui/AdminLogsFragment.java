package com.example.eventease.admin.logs.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventease.R;

public class AdminLogsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.admin_logs_management, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup logout button
        setupLogoutButton(view);

        TextView tvMessage = view.findViewById(R.id.tvNotImplemented);
        if (tvMessage != null) {
            tvMessage.setText("Logs feature is not yet implemented. This will show notification logs when the feature is ready.");
        }
    }

    private void setupLogoutButton(View view) {
        View logoutButton = view.findViewById(R.id.adminLogoutButton);
        if (logoutButton != null && getActivity() instanceof com.example.eventease.admin.AdminMainActivity) {
            logoutButton.setOnClickListener(v -> {
                ((com.example.eventease.admin.AdminMainActivity) getActivity()).performLogout();
            });
        }
    }
}

