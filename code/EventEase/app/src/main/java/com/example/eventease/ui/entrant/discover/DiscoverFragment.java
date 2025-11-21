package com.example.eventease.ui.entrant.discover;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.model.Event;
import com.example.eventease.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Fragment for discovering and browsing available events.
 * 
 * <p>This fragment displays a list of events that are currently open for registration.
 * An event is considered open if the current time falls within its registration period
 * (between registrationStart and registrationEnd).
 * 
 * <p>Features:
 * <ul>
 *   <li>Real-time event updates using Firestore listeners</li>
 *   <li>Event cards showing title, date, location, and poster image</li>
 *   <li>Click to view event details and join waitlist</li>
 *   <li>Empty state message when no events are available</li>
 * </ul>
 * 
 * <p>The fragment automatically updates when events are added, modified, or removed from Firestore.
 */
public class DiscoverFragment extends Fragment {

    private DiscoverAdapter adapter;
    private ListenerRegistration eventsRegistration;
    private ProgressBar progressView;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvDiscover);
        progressView = view.findViewById(R.id.discoverProgress);
        emptyView = view.findViewById(R.id.discoverEmpty);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);

        adapter = new DiscoverAdapter(event -> {
            Intent intent = new Intent(requireContext(), EventDetailsDiscoverActivity.class);
            intent.putExtra(EventDetailsDiscoverActivity.EXTRA_EVENT_TITLE, event.getTitle());
            intent.putExtra(EventDetailsDiscoverActivity.EXTRA_EVENT_ID, event.getId());
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        listenForEvents();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Ensure bottom nav is visible when this fragment is shown (safety check)
        ensureBottomNavVisible();
    }
    
    private void ensureBottomNavVisible() {
        if (getActivity() == null) return;
        
        // Check if user is authenticated - simply check if Firebase Auth has a current user
        // "Remember Me" only affects persistence across app restarts, not current authentication state
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        boolean isAuthenticated = currentUser != null;
        
        if (isAuthenticated) {
            View bottomNav = getActivity().findViewById(R.id.include_bottom);
            View topBar = getActivity().findViewById(R.id.include_top);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.VISIBLE);
            }
            if (topBar != null) {
                topBar.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (eventsRegistration != null) {
            eventsRegistration.remove();
            eventsRegistration = null;
        }
        adapter = null;
        progressView = null;
        emptyView = null;
    }

    private void listenForEvents() {
        setLoading(true);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        // Query all events without orderBy to ensure all events are returned
        // We'll sort them in memory after fetching
        Query query = firestore.collection("events");

        eventsRegistration = query.addSnapshotListener((snapshots, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                setLoading(false);
                handleError(error);
                return;
            }
            setLoading(false);
            handleSnapshot(snapshots);
        });
    }

    private void handleSnapshot(@Nullable QuerySnapshot snapshots) {
        android.util.Log.d("DiscoverFragment", "handleSnapshot docs=" + (snapshots != null ? snapshots.size() : -1));
        if (adapter == null) return;
        if (snapshots == null) {
            adapter.clear();
            showEmptyState(true);
            return;
        }

        List<Event> events = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            android.util.Log.d("DiscoverFragment", "Doc id=" + doc.getId() + " data=" + doc.getData());
            Event event = Event.fromMap(doc.getData());
            if (event == null) continue;
            if (TextUtils.isEmpty(event.id)) {
                event.id = doc.getId();
            }
            events.add(event);
        }
        
        // Sort events by start time in memory (events without startsAtEpochMs will be sorted last)
        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                // Events with startsAtEpochMs = 0 or missing will be sorted to the end
                if (e1.startsAtEpochMs == 0 && e2.startsAtEpochMs == 0) return 0;
                if (e1.startsAtEpochMs == 0) return 1;
                if (e2.startsAtEpochMs == 0) return -1;
                return Long.compare(e1.startsAtEpochMs, e2.startsAtEpochMs);
            }
        });
        
        android.util.Log.d("DiscoverFragment", "Parsed events=" + events.size());
        adapter.submit(events);
        showEmptyState(events.isEmpty());
    }

    private void handleError(@NonNull FirebaseFirestoreException error) {
        if (adapter != null) {
            adapter.clear();
        }
        showEmptyState(true);
        if (getContext() != null) {
            String message = error.getMessage();
            if (TextUtils.isEmpty(message)) {
                message = "Unable to load events right now.";
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean loading) {
        if (progressView != null) {
            progressView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyView != null) {
            emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show && emptyView.getText().length() == 0) {
                emptyView.setText(R.string.discover_empty_state);
            }
        }
    }
}
