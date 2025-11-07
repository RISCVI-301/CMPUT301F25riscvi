package com.EventEase.ui.entrant.myevents;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.ui.entrant.eventdetail.EventDetailActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import androidx.navigation.Navigation;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.EventRepository;
import com.EventEase.data.InvitationListener;
import com.EventEase.data.InvitationRepository;
import com.EventEase.data.ListenerRegistration;
import com.EventEase.data.WaitlistRepository;
import com.EventEase.model.Event;
import com.EventEase.model.Invitation;
import com.EventEase.App;                // ✅ use shared DevGraph
import com.EventEase.R;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment for displaying user's events.
 * Shows events the user is admitted to or has invitations for.
 */
public class MyEventsFragment extends Fragment {

    private EventRepository eventRepo;
    private WaitlistRepository waitlistRepo;
    private InvitationRepository invitationRepo;
    private AuthManager auth;
    private com.EventEase.data.AdmittedRepository admittedRepo;

    private RecyclerView list;
    private TextView emptyView;
    private ProgressBar progress;

    private final MyEventsAdapter adapter = new MyEventsAdapter();
    private ListenerRegistration inviteReg;

    private final Set<String> invitedEventIds = new HashSet<>();
    private final Map<String, String> eventIdToInvitationId = new HashMap<>();
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    public MyEventsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.entrant_fragment_my_events, container, false);

        // Set status bar color to match top bar
        if (getActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.ee_topbar_bg));
        }

        list = root.findViewById(R.id.my_events_list);
        emptyView = root.findViewById(R.id.my_events_empty);
        progress = root.findViewById(R.id.my_events_progress);

        // Set up back button
        View btnBack = root.findViewById(R.id.btnBack);
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

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();

        eventRepo      = App.graph().events;
        waitlistRepo   = App.graph().waitlists;
        invitationRepo = App.graph().invitations;
        auth           = App.graph().auth;
        admittedRepo   = App.graph().admitted;

        setLoading(true);
        loadMyEvents();

        inviteReg = invitationRepo.listenActive(auth.getUid(), new InvitationListener() {
            @Override
            public void onChanged(List<Invitation> activeInvites) {
                invitedEventIds.clear();
                eventIdToInvitationId.clear();
                if (activeInvites != null) {
                    for (Invitation inv : activeInvites) {
                        invitedEventIds.add(inv.getEventId());
                        eventIdToInvitationId.put(inv.getEventId(), inv.getId());
                    }
                }
                adapter.setInvitedEventIds(invitedEventIds);
            }
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh event list when returning to fragment (e.g., after accepting invitation)
        if (eventRepo != null && waitlistRepo != null && auth != null) {
            setLoading(true);
            loadMyEvents();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (inviteReg != null) {
            inviteReg.remove();
            inviteReg = null;
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (list != null) list.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);
    }

    private void showEmptyIfNeeded() {
        if (emptyView != null) {
            emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void loadMyEvents() {
        Task<List<Event>> openTask = eventRepo.getOpenEvents(new Date());
        openTask.addOnSuccessListener(events -> {
            if (!isAdded()) return;

            android.util.Log.d("MyEventsFragment", "Loaded events: " + (events != null ? events.size() : 0));
            if (events == null || events.isEmpty()) {
                android.util.Log.d("MyEventsFragment", "No open events found in Firebase");
                adapter.submit(new ArrayList<>());
                setLoading(false);
                showEmptyIfNeeded();
                return;
            }

            bg.execute(() -> {
                List<Event> mine = new ArrayList<>();
                String uid = auth.getUid();
                android.util.Log.d("MyEventsFragment", "Checking waitlists for user: " + uid);
                for (Event e : events) {
                    try {
                        Boolean joined = Tasks.await(waitlistRepo.isJoined(e.getId(), uid));
                        // Also check if user has already been admitted/accepted
                        Boolean admitted = Tasks.await(admittedRepo.isAdmitted(e.getId(), uid));
                        android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " - Joined: " + joined + ", Admitted: " + admitted);
                        
                        // Only show if in waitlist AND NOT admitted
                        if (Boolean.TRUE.equals(joined) && !Boolean.TRUE.equals(admitted)) {
                            mine.add(e);
                        }
                    } catch (Exception ex) {
                        android.util.Log.e("MyEventsFragment", "Error checking waitlist for event " + e.getId(), ex);
                    }
                }
                android.util.Log.d("MyEventsFragment", "Found " + mine.size() + " waitlisted events (excluding admitted) for user");
                if (!isAdded()) return;
                ViewCompat.postOnAnimation(requireView(), () -> {
                    adapter.submit(mine);
                    setLoading(false);
                    showEmptyIfNeeded();
                });
            });

        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            android.util.Log.e("MyEventsFragment", "Failed to load events", e);
            adapter.submit(new ArrayList<>());
            setLoading(false);
            showEmptyIfNeeded();
        });
    }

    private int dp(int dps) {
        Resources r = getResources();
        return Math.round(dps * r.getDisplayMetrics().density);
    }

    // ===== Adapter / ViewHolder using your IDs =====

    private class MyEventsAdapter extends RecyclerView.Adapter<MyEventVH> {
        private final List<Event> data = new ArrayList<>();
        private final Set<String> invited = new HashSet<>();

        void submit(List<Event> items) {
            data.clear();
            if (items != null) data.addAll(items);
            notifyDataSetChanged();
        }

        void setInvitedEventIds(Set<String> ids) {
            invited.clear();
            if (ids != null) invited.addAll(ids);
            notifyDataSetChanged();
        }

        @NonNull @Override public MyEventVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.entrant_item_event_card, parent, false);
            return new MyEventVH(v, event -> {
                // Launch EventDetailActivity when an event is clicked
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
                // Pass invitation status and ID
                boolean hasInvite = invited.contains(event.getId());
                intent.putExtra("hasInvitation", hasInvite);
                if (hasInvite && eventIdToInvitationId.containsKey(event.getId())) {
                    intent.putExtra("invitationId", eventIdToInvitationId.get(event.getId()));
                }
                startActivity(intent);
            });
        }

        @Override public void onBindViewHolder(@NonNull MyEventVH h, int pos) {
            Event e = data.get(pos);
            h.bind(e, invited.contains(e.getId()));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    private static class MyEventVH extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView name;
        private final TextView subtitle;
        private final View accentDot;
        private final SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());
        private Event currentEvent;

        MyEventVH(@NonNull View itemView, EventClickListener listener) {
            super(itemView);
            image = itemView.findViewById(R.id.ivPoster);
            name = itemView.findViewById(R.id.tvTitle);
            subtitle = itemView.findViewById(R.id.tvMeta);
            accentDot = itemView.findViewById(R.id.eventAccent);

            itemView.setOnClickListener(v -> {
                if (currentEvent != null && listener != null) {
                    listener.onEventClick(currentEvent);
                }
            });
        }

        void bind(Event e, boolean invited) {
            this.currentEvent = e;
            name.setText(e.getTitle() != null ? e.getTitle() : "Untitled");

            // Use getStartsAtEpochMs() instead of getStartAt()
            String when = (e.getStartsAtEpochMs() > 0) ? df.format(new Date(e.getStartsAtEpochMs())) : "TBD";
            String where = e.getLocation() != null ? e.getLocation() : "";
            subtitle.setText(where.isEmpty() ? when : (when + " • " + where));

            // Show accent dot ONLY if user has been invited (teal color with drop shadow)
            if (accentDot != null) {
                if (invited) {
                    accentDot.setVisibility(View.VISIBLE);
                    accentDot.setElevation(8f); // Add drop shadow
                    android.graphics.drawable.Drawable bg = accentDot.getBackground();
                    if (bg != null) {
                        // Teal/cyan color for invited events
                        androidx.core.graphics.drawable.DrawableCompat.setTint(bg.mutate(), android.graphics.Color.parseColor("#7FE8F5"));
                    }
                } else {
                    // Hide dot for events without invitation
                    accentDot.setVisibility(View.GONE);
                }
            }
            
            // Load event image using Glide
            if (e.getPosterUrl() != null && !e.getPosterUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(e.getPosterUrl())
                    .placeholder(R.drawable.entrant_image_placeholder_event)
                    .error(R.drawable.entrant_image_placeholder_event)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(image);
            } else {
                image.setImageResource(R.drawable.entrant_image_placeholder_event);
            }
        }
    }

    interface EventClickListener {
        void onEventClick(Event event);
    }
}
