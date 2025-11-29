package com.example.eventease.ui.entrant;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.eventease.App;
import com.example.eventease.R;
import com.example.eventease.data.AdmittedRepository;
import com.example.eventease.model.Event;
import com.example.eventease.ui.entrant.eventdetail.EventDetailActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for displaying past events.
 * Shows ALL events the user participated in (waitlisted, selected, non-selected, cancelled, or admitted)
 * where the deadline or start date has already passed.
 */
public class PreviousEventsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private PreviousEventsAdapter adapter;
    private AdmittedRepository admittedRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_previous_events, container, false);

        // Set status bar color to match top bar
        if (getActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.ee_topbar_bg));
        }

        // Initialize repositories
        admittedRepo = App.graph().admitted;

        // Set up back button
        View btnBack = root.findViewById(R.id.btnBackPrevious);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(v).navigateUp();
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                }
            });
        }

        // Set up views
        recyclerView = root.findViewById(R.id.previous_events_list);
        progressBar = root.findViewById(R.id.previous_events_progress);
        emptyView = root.findViewById(R.id.previous_events_empty);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PreviousEventsAdapter();
        recyclerView.setAdapter(adapter);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Initialize repositories if needed
        if (admittedRepo == null) {
            admittedRepo = App.graph().admitted;
        }
        loadPreviousEvents();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh events when fragment becomes visible
        if (admittedRepo != null) {
            loadPreviousEvents();
        }
    }

    private void loadPreviousEvents() {
        if (admittedRepo == null) {
            android.util.Log.w("PreviousEventsFragment", "Repositories not initialized - admittedRepo is null");
            return;
        }
        
        setLoading(true);
        String uid = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (uid == null || uid.isEmpty()) {
            android.util.Log.e("PreviousEventsFragment", "User UID is null or empty");
            setLoading(false);
            adapter.submitEvents(new ArrayList<>());
            updateEmptyState(true);
            return;
        }
        
        android.util.Log.d("PreviousEventsFragment", "Loading previous events for uid: " + uid);
        
        admittedRepo.getPreviousEvents(uid)
                .addOnSuccessListener(events -> {
                    if (!isAdded()) return;
                    android.util.Log.d("PreviousEventsFragment", "Loaded " + (events != null ? events.size() : 0) + " previous events");
                    if (events != null && !events.isEmpty()) {
                        for (Event event : events) {
                            android.util.Log.d("PreviousEventsFragment", "  - Previous Event: " + event.getTitle() + " (id: " + event.getId() + ")");
                        }
                    }
                    setLoading(false);
                    adapter.submitEvents(events != null ? events : new ArrayList<>());
                    updateEmptyState(events == null || events.isEmpty());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    android.util.Log.e("PreviousEventsFragment", "Failed to load previous events", e);
                    setLoading(false);
                    updateEmptyState(true);
                    adapter.submitEvents(new ArrayList<>());
                });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    // Adapter for previous events
    private class PreviousEventsAdapter extends RecyclerView.Adapter<PreviousEventsAdapter.VH> {
        private final List<Event> events = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());

        private final int[] ACCENT_COLORS = {
                Color.parseColor("#7FE8F5"),
                Color.parseColor("#F8B3FF"),
                Color.parseColor("#FFD66B"),
                Color.parseColor("#9BE7FF"),
                Color.parseColor("#FF9E9D")
        };

        void submitEvents(List<Event> newEvents) {
            events.clear();
            if (newEvents != null) {
                events.addAll(newEvents);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.entrant_item_event_card, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Event event = events.get(position);
            holder.bind(event);
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final ImageView poster;
            final View accentDot;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvTitle);
                meta = itemView.findViewById(R.id.tvMeta);
                poster = itemView.findViewById(R.id.ivPoster);
                accentDot = itemView.findViewById(R.id.eventAccent);

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        Event event = events.get(pos);
                        Intent intent = new Intent(itemView.getContext(), EventDetailActivity.class);
                        intent.putExtra("eventId", event.getId());
                        intent.putExtra("eventTitle", event.getTitle());
                        intent.putExtra("eventLocation", event.getLocation());
                        intent.putExtra("eventStartTime", event.getStartsAtEpochMs());
                        intent.putExtra("eventDeadline", event.getDeadlineEpochMs());
                        intent.putExtra("eventCapacity", event.getCapacity());
                        intent.putExtra("eventNotes", event.getNotes());
                        intent.putExtra("eventGuidelines", event.getGuidelines());
                        intent.putExtra("eventPosterUrl", event.getPosterUrl());
                        intent.putExtra("eventWaitlistCount", event.getWaitlistCount());
                        intent.putExtra("hasInvitation", false); // Previous event
                        intent.putExtra("isPreviousEvent", true); // Mark as previous event - no buttons should be shown
                        itemView.getContext().startActivity(intent);
                    }
                });
            }

            void bind(Event event) {
                String titleText = event.getTitle();
                if (TextUtils.isEmpty(titleText)) {
                    titleText = getString(R.string.event_details_title_placeholder);
                }
                title.setText(titleText);

                boolean hasLocation = !TextUtils.isEmpty(event.getLocation());
                String locationText = hasLocation ? event.getLocation() : getString(R.string.event_details_location_tbd);

                boolean hasDate = event.getStartsAtEpochMs() > 0;
                String dateText = hasDate ? dateFormat.format(new Date(event.getStartsAtEpochMs())) : getString(R.string.event_details_date_tbd);

                String metaText;
                if (hasDate && hasLocation) {
                    metaText = dateText + " • " + event.getLocation();
                } else if (hasDate) {
                    metaText = dateText;
                } else if (hasLocation) {
                    metaText = event.getLocation();
                } else {
                    metaText = getString(R.string.discover_event_meta_placeholder);
                }
                meta.setText(metaText);

                Glide.with(poster.getContext())
                        .load(event.getPosterUrl())
                        .placeholder(R.drawable.entrant_image_placeholder_event)
                        .error(R.drawable.entrant_image_placeholder_event)
                        .centerCrop()
                        .into(poster);

                if (accentDot != null) {
                    Drawable bg = accentDot.getBackground();
                    if (bg != null) {
                        int colorIndex = Math.abs((event.getTitle() != null ? event.getTitle() : event.getId() != null ? event.getId() : "")
                                .hashCode()) % ACCENT_COLORS.length;
                        DrawableCompat.setTint(bg.mutate(), ACCENT_COLORS[colorIndex]);
                    }
                }
            }
        }
    }
}
