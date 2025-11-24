package com.example.eventease.admin.event.ui;

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
import com.example.eventease.admin.event.data.AdminEventDatabaseController;
import com.example.eventease.admin.event.data.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for admin users to view and manage all events in the system.
 * 
 * <p>This fragment displays a list of all events from the Firestore database and provides
 * administrative functionality to view and delete events. Admins can see all events regardless
 * of which organizer created them.
 * 
 * <p>Features:
 * <ul>
 *   <li>View all events in a scrollable list</li>
 *   <li>Delete events with confirmation dialog</li>
 *   <li>Refresh event list after deletion</li>
 * </ul>
 * 
 * <p>The fragment uses AdminEventDatabaseController to fetch events and perform deletions.
 * Only users with admin role can access this functionality.
 */
public class AdminEventsFragment extends Fragment {

    private final AdminEventDatabaseController AEDC = new AdminEventDatabaseController();
    private RecyclerView rv;
    private EventAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.admin_event_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup logout button
        setupLogoutButton(view);

        rv = view.findViewById(R.id.rvEvents);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new EventAdapter(requireContext(), new ArrayList<>(), this::deleteEventAndRefresh);
            rv.setAdapter(adapter);
        }

        // Async load; update UI when data is received
        AEDC.fetchEvents(new AdminEventDatabaseController.EventsCallback() {
            @Override
            public void onLoaded(@NonNull List<Event> data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Replace adapter to avoid relying on internal mutation APIs
                        adapter = new EventAdapter(requireContext(), data, AdminEventsFragment.this::deleteEventAndRefresh);
                        if (rv != null) rv.setAdapter(adapter);
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                // TODO: show error state/snackbar if desired
            }
        });
    }

    private void deleteEventAndRefresh(@NonNull Event e) {
        AEDC.deleteEvent(e);
        // Re-fetch to refresh the list after delete
        AEDC.fetchEvents(new AdminEventDatabaseController.EventsCallback() {
            @Override
            public void onLoaded(@NonNull List<Event> data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter = new EventAdapter(requireContext(), data, AdminEventsFragment.this::deleteEventAndRefresh);
                        if (rv != null) rv.setAdapter(adapter);
                    });
                }
            }
            @Override public void onError(@NonNull Exception ex) { /* optionally report */ }
        });
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

