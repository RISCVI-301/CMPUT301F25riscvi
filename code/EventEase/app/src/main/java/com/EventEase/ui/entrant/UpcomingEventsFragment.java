package com.EventEase.ui.entrant;

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

import com.EventEase.auth.AuthManager;
import com.EventEase.data.AdmittedRepository;
import com.EventEase.model.Event;
import com.EventEase.ui.entrant.eventdetail.EventDetailActivity;
import com.bumptech.glide.Glide;
import com.EventEase.App;
import com.EventEase.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for displaying upcoming events.
 * Shows events the user is admitted to that haven't started yet.
 */
public class UpcomingEventsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private UpcomingEventsAdapter adapter;
    private AdmittedRepository admittedRepo;
    private AuthManager authManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_upcoming_events, container, false);

        // Set status bar color to match top bar
        if (getActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.ee_topbar_bg));
        }

        // Initialize repositories
        admittedRepo = App.graph().admitted;
        authManager = App.graph().auth;

        // Set up back button
        View btnBack = root.findViewById(R.id.btnBackUpcoming);
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
        recyclerView = root.findViewById(R.id.upcoming_events_list);
        progressBar = root.findViewById(R.id.upcoming_events_progress);
        emptyView = root.findViewById(R.id.upcoming_events_empty);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UpcomingEventsAdapter();
        recyclerView.setAdapter(adapter);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadUpcomingEvents();
    }

    private void loadUpcomingEvents() {
        setLoading(true);
        String uid = authManager.getUid();
        
        admittedRepo.getUpcomingEvents(uid)
                .addOnSuccessListener(events -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    adapter.submitEvents(events);
                    updateEmptyState(events.isEmpty());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    updateEmptyState(true);
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

    // Adapter for upcoming events
    private class UpcomingEventsAdapter extends RecyclerView.Adapter<UpcomingEventsAdapter.VH> {
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
                        Intent intent = new Intent(requireContext(), EventDetailActivity.class);
                        intent.putExtra("eventId", event.getId());
                        intent.putExtra("eventTitle", event.getTitle());
                        intent.putExtra("eventLocation", event.getLocation());
                        intent.putExtra("eventStartTime", event.getStartsAtEpochMs());
                        intent.putExtra("eventCapacity", event.getCapacity());
                        intent.putExtra("eventNotes", event.getNotes());
                        intent.putExtra("eventGuidelines", event.getGuidelines());
                        intent.putExtra("eventPosterUrl", event.getPosterUrl());
                        intent.putExtra("eventWaitlistCount", event.getWaitlistCount());
                        intent.putExtra("hasInvitation", false); // Already accepted
                        startActivity(intent);
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

