package com.EventEase.ui.entrant.discover;

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

import com.EventEase.model.Event;
import com.EventEase.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for discovering and browsing available events.
 * Displays a list of open events and allows users to join waitlists.
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
        
        // Check if user is authenticated
        FirebaseAuth auth = FirebaseAuth.getInstance();
        android.content.SharedPreferences prefs = getActivity().getSharedPreferences("EventEasePrefs", android.content.Context.MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        String savedUid = prefs.getString("savedUid", null);
        FirebaseUser currentUser = auth.getCurrentUser();
        boolean isAuthenticated = rememberMe && savedUid != null && currentUser != null && savedUid.equals(currentUser.getUid());
        
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
        registerListener(firestore, true);
    }

    private void registerListener(@NonNull FirebaseFirestore firestore, boolean ordered) {
        Query query = firestore.collection("events");
        if (ordered) {
            query = query.orderBy("startsAtEpochMs", Query.Direction.ASCENDING);
        }

        eventsRegistration = query.addSnapshotListener((snapshots, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                if (ordered && error.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    if (eventsRegistration != null) {
                        eventsRegistration.remove();
                        eventsRegistration = null;
                    }
                    registerListener(firestore, false);
                    return;
                }
                setLoading(false);
                handleError(error);
                return;
            }
            setLoading(false);
            handleSnapshot(snapshots);
        });
    }

    private void handleSnapshot(@Nullable QuerySnapshot snapshots) {
        if (adapter == null) return;
        if (snapshots == null) {
            adapter.clear();
            showEmptyState(true);
            return;
        }

        List<Event> events = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Event event = doc.toObject(Event.class);
            if (event == null) continue;
            if (TextUtils.isEmpty(event.id)) {
                event.id = doc.getId();
            }
            events.add(event);
        }
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
