package com.example.eventease.admin.logs.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.logs.data.AdminNotificationLogDatabaseController;
import com.example.eventease.admin.logs.data.Notification;
import android.util.Log;

import android.content.Intent;
import android.widget.Button;

import com.example.eventease.MainActivity;

import java.util.List;

public class AdminLogsFragment extends Fragment {

    private AdminLogsAdapter adapter;
    private AdminNotificationLogDatabaseController controller;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.admin_logs_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button switchButton = view.findViewById(R.id.btnSwitchToEntrantView);
        if (switchButton != null) {
            switchButton.setOnClickListener(v -> {
                android.util.Log.d("AdminToEntrant", "Admin to Entrant Clicked");
                Intent intent = new Intent(requireContext(), com.example.eventease.MainActivity.class);
                intent.putExtra("force_entrant", true);
                startActivity(intent);
                requireActivity().finish();
            });
        }

        setupRecyclerView(view);

        controller = new AdminNotificationLogDatabaseController();
        loadNotifications();

    }

    private void setupRecyclerView(@NonNull View view) {
        RecyclerView rv = view.findViewById(R.id.rvNotificationLogs);
        adapter = new AdminLogsAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
    }

    private void loadNotifications() {
        controller.fetchNotifications(new AdminNotificationLogDatabaseController.NotificationsCallback() {
            @Override
            public void onLoaded(@NonNull List<Notification> notifications) {
                if (!isAdded()) return; // Fragment might be detached
                adapter.setItems(notifications);
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to load notification logs",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}