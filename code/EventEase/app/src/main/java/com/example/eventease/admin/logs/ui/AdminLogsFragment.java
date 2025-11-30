package com.example.eventease.admin.logs.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.logs.data.Notification;

import java.util.ArrayList;
import java.util.List;

public class AdminLogsFragment extends Fragment {

    private AdminLogsAdapter adapter;

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

        setupLogoutButton(view);
        setupRecyclerView(view);

        // DEMO DATA for now. Later replace with AdminNotificationLogDatabaseController.
        adapter.setItems(createDemoData());
    }

    private void setupLogoutButton(View view) {
        View logoutButton = view.findViewById(R.id.adminLogoutButton);
        if (logoutButton != null
                && getActivity() instanceof com.example.eventease.admin.AdminMainActivity) {
            logoutButton.setOnClickListener(v ->
                    ((com.example.eventease.admin.AdminMainActivity) getActivity()).performLogout()
            );
        }
    }

    private void setupRecyclerView(@NonNull View view) {
        RecyclerView rv = view.findViewById(R.id.rvNotificationLogs);
        adapter = new AdminLogsAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
    }

    private List<Notification> createDemoData() {
        List<Notification> list = new ArrayList<>();

        long now = System.currentTimeMillis();

        list.add(new Notification(
                now - 1 * 60 * 60 * 1000L,           // 1 hour ago
                "Event Reminder",
                "Don’t forget, the Developer Meetup starts in 1 hour.",
                "Developer Meetup 2025",
                "organizer_123"
        ));

        list.add(new Notification(
                now - 2 * 24 * 60 * 60 * 1000L,      // 2 days ago
                "Waitlist Opened",
                "The event is full. You can now join the waitlist.",
                "AI in Education",
                "organizer_456"
        ));

        list.add(new Notification(
                now - 10 * 24 * 60 * 60 * 1000L,     // 10 days ago
                "Registration Closing Soon",
                "Registration ends tonight at 11:59 PM.",
                "Hackathon 2025",
                "organizer_789"
        ));

        return list;
    }
}