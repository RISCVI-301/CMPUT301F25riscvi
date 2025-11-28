package com.example.eventease.ui.entrant.myevents;

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

import com.example.eventease.ui.entrant.eventdetail.EventDetailActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import androidx.navigation.Navigation;

import com.example.eventease.data.EventRepository;
import com.example.eventease.data.InvitationListener;
import com.example.eventease.data.InvitationRepository;
import com.example.eventease.data.ListenerRegistration;
import com.example.eventease.data.WaitlistRepository;
import com.example.eventease.model.Event;
import com.example.eventease.model.Invitation;
import com.example.eventease.App;                // ✅ use shared DevGraph
import com.example.eventease.R;
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
 * Fragment for displaying a user's events.
 * 
 * <p>This fragment displays events that are relevant to the current user, including:
 * <ul>
 *   <li>Upcoming admitted events - Events where the user is in the AdmittedEntrants collection
 *       and the deadline (or start time) has not yet passed</li>
 *   <li>Waitlisted/Selected events - Events where the user is on the waitlist or has been selected,
 *       but is not yet admitted (excluding events already admitted to)</li>
 *   <li>Previous events - Events where the user was admitted but the deadline has passed</li>
 * </ul>
 * 
 * <p>The fragment listens for invitation updates in real-time and displays invitations with
 * accept/decline buttons. It also shows a teal notification dot for events with pending invitations.
 * 
 * <p>Events are loaded asynchronously from multiple sources and combined to provide a unified view.
 */
public class MyEventsFragment extends Fragment {

    private EventRepository eventRepo;
    private WaitlistRepository waitlistRepo;
    private InvitationRepository invitationRepo;
    private com.example.eventease.data.AdmittedRepository admittedRepo;

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
        admittedRepo   = App.graph().admitted;

        setLoading(true);
        loadMyEvents();

        String uid = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        inviteReg = invitationRepo.listenActive(uid, new InvitationListener() {
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
        if (eventRepo != null && waitlistRepo != null) {
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
        String uid = com.example.eventease.auth.AuthHelper.getUid(requireContext());
        if (uid == null || uid.isEmpty()) {
            android.util.Log.e("MyEventsFragment", "User UID is null or empty");
            adapter.submit(new ArrayList<>());
            setLoading(false);
            showEmptyIfNeeded();
            return;
        }

        // Load admitted upcoming events first
        Task<List<Event>> admittedEventsTask = admittedRepo.getUpcomingEvents(uid);
        
        // Load previous events (deadline passed)
        Task<List<Event>> previousEventsTask = admittedRepo.getPreviousEvents(uid);
        
        // Load open events for waitlisted/selected filtering
        Task<List<Event>> openEventsTask = eventRepo.getOpenEvents(new Date());
        
        // Combine all tasks
        Tasks.whenAllComplete(admittedEventsTask, previousEventsTask, openEventsTask)
                .addOnSuccessListener(tasks -> {
                    if (!isAdded()) return;
                    
                    // Extract results in UI thread
                    List<Event> admittedEvents = new ArrayList<>();
                    List<Event> previousEvents = new ArrayList<>();
                    List<Event> openEvents = new ArrayList<>();
                    
                    // Process admitted upcoming events
                    try {
                        @SuppressWarnings("unchecked")
                        Task<List<Event>> admittedTask = (Task<List<Event>>) tasks.get(0);
                        if (admittedTask.isSuccessful() && admittedTask.getResult() != null) {
                            admittedEvents = admittedTask.getResult();
                            android.util.Log.d("MyEventsFragment", "Loaded " + admittedEvents.size() + " admitted upcoming events");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MyEventsFragment", "Error processing admitted events", e);
                    }
                    
                    // Process previous events
                    try {
                        @SuppressWarnings("unchecked")
                        Task<List<Event>> previousTask = (Task<List<Event>>) tasks.get(1);
                        if (previousTask.isSuccessful() && previousTask.getResult() != null) {
                            previousEvents = previousTask.getResult();
                            android.util.Log.d("MyEventsFragment", "Loaded " + previousEvents.size() + " previous events");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MyEventsFragment", "Error processing previous events", e);
                    }
                    
                    // Process open events
                    try {
                        @SuppressWarnings("unchecked")
                        Task<List<Event>> openTask = (Task<List<Event>>) tasks.get(2);
                        if (openTask.isSuccessful() && openTask.getResult() != null) {
                            openEvents = openTask.getResult();
                            android.util.Log.d("MyEventsFragment", "Loaded " + openEvents.size() + " open events, checking for waitlisted/selected");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MyEventsFragment", "Error processing open events", e);
                    }
                    
                    // Process in background thread to avoid blocking UI
                    final List<Event> finalAdmittedEvents = admittedEvents;
                    final List<Event> finalPreviousEvents = previousEvents;
                    final List<Event> finalOpenEvents = openEvents;
                    
                    bg.execute(() -> {
                        List<Event> allMyEvents = new ArrayList<>();
                        Set<String> eventIds = new HashSet<>();
                        
                        // Add admitted upcoming events first
                        for (Event event : finalAdmittedEvents) {
                            if (event != null && event.getId() != null && !eventIds.contains(event.getId())) {
                                allMyEvents.add(event);
                                eventIds.add(event.getId());
                                android.util.Log.d("MyEventsFragment", "Added admitted upcoming event: " + event.getTitle() + " (id: " + event.getId() + ")");
                            }
                        }
                        
                        int admittedCount = allMyEvents.size();
                        
                        // Process waitlisted/selected events
                        // Show events if user is in waitlist OR selected OR not selected (until invitations sent)
                        // CRITICAL: After event starts, these events should only show in previous events
                        long currentTime = System.currentTimeMillis();
                        long oneMinuteInMs = 60 * 1000; // 1 minute in milliseconds
                        
                        List<Event> waitlistedSelected = new ArrayList<>();
                        List<Event> acceptedNotYetShown = new ArrayList<>();
                        
                        for (Event e : finalOpenEvents) {
                            try {
                                // Skip if already added as admitted event
                                if (eventIds.contains(e.getId())) {
                                    continue;
                                }
                                
                                // CRITICAL: Check if event has started - if so, skip from waitlisted/selected
                                // Events that have started should only appear in previous events
                                long eventStart = e.getStartsAtEpochMs();
                                if (eventStart > 0 && currentTime >= eventStart) {
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " has already started (startTime: " + eventStart + ", currentTime: " + currentTime + ") - skipping from waitlisted/selected (will show in previous events)");
                                    continue;
                                }
                                
                                Boolean joined = Tasks.await(waitlistRepo.isJoined(e.getId(), uid));
                                Boolean inSelected = Tasks.await(isInSelectedEntrants(e.getId(), uid));
                                Boolean inNonSelected = Tasks.await(isInNonSelectedEntrants(e.getId(), uid));
                                Boolean inCancelled = Tasks.await(isInCancelledEntrants(e.getId(), uid));
                                Boolean admitted = Tasks.await(admittedRepo.isAdmitted(e.getId(), uid));
                                Boolean hasAcceptedInvitation = Tasks.await(hasAcceptedInvitation(e.getId(), uid));
                                Boolean hasDeclinedInvitation = Tasks.await(hasDeclinedInvitation(e.getId(), uid));
                                
                                android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " - Joined: " + joined + ", Selected: " + inSelected + ", NonSelected: " + inNonSelected + ", Cancelled: " + inCancelled + ", Admitted: " + admitted + ", Accepted: " + hasAcceptedInvitation + ", Declined: " + hasDeclinedInvitation);
                                
                                // CRITICAL: Show in upcoming only if user is in BOTH SelectedEntrants AND AdmittedEntrants
                                // AND event hasn't started yet
                                if (Boolean.TRUE.equals(inSelected) && Boolean.TRUE.equals(admitted)) {
                                    acceptedNotYetShown.add(e);
                                    eventIds.add(e.getId());
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " - User is in both Selected and Admitted, showing in upcoming");
                                }
                                // If user declined/rejected or in cancelled, skip (will show in PreviousEventsFragment)
                                else if (Boolean.TRUE.equals(hasDeclinedInvitation) || Boolean.TRUE.equals(inCancelled)) {
                                    android.util.Log.d("MyEventsFragment", "User declined/cancelled event " + e.getTitle() + " - skipping (will show in previous events)");
                                    // Skip - declined/cancelled events should show in PreviousEventsFragment
                                }
                                // CRITICAL: Show in waitlist if user is in selected OR not selected (until event start - 1 minute)
                                // For not selected: if current time >= event start - 1 minute, they should be in cancelled (handled by cloud function)
                                else if (Boolean.TRUE.equals(inSelected) || Boolean.TRUE.equals(inNonSelected) || Boolean.TRUE.equals(joined)) {
                                    // Check if not selected and should be moved to cancelled (event start - 1 minute has passed)
                                    if (Boolean.TRUE.equals(inNonSelected)) {
                                        if (eventStart > 0 && currentTime >= (eventStart - oneMinuteInMs)) {
                                            // Event start - 1 minute has passed, not selected should be in cancelled
                                            // Skip showing in waitlist (will show in previous)
                                            android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " - Not selected and event start - 1 minute has passed, skipping (should be in cancelled)");
                                            continue;
                                        }
                                    }
                                    // Show in waitlist section - these are events user is waiting on (and event hasn't started)
                                    waitlistedSelected.add(e);
                                    eventIds.add(e.getId());
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " - Showing in waitlist (selected: " + inSelected + ", not selected: " + inNonSelected + ", joined: " + joined + ")");
                                }
                            } catch (Exception ex) {
                                android.util.Log.e("MyEventsFragment", "Error checking waitlist for event " + e.getId(), ex);
                            }
                        }
                        
                        // Add accepted events to upcoming section (after admitted events)
                        allMyEvents.addAll(acceptedNotYetShown);
                        
                        // Add waitlisted/selected events
                        allMyEvents.addAll(waitlistedSelected);
                        
                        // Add previous events at the end (they have deadline passed)
                        for (Event event : finalPreviousEvents) {
                            if (event != null && event.getId() != null && !eventIds.contains(event.getId())) {
                                allMyEvents.add(event);
                                eventIds.add(event.getId());
                                android.util.Log.d("MyEventsFragment", "Added previous event: " + event.getTitle() + " (id: " + event.getId() + ")");
                            }
                        }
                        
                        android.util.Log.d("MyEventsFragment", "Total events for user: " + allMyEvents.size() + 
                                " (upcoming admitted: " + admittedCount + 
                                ", accepted invitations: " + acceptedNotYetShown.size() + 
                                ", waitlisted/selected: " + waitlistedSelected.size() + 
                                ", previous: " + finalPreviousEvents.size() + ")");
                        
                        if (!isAdded()) return;
                        
                        // Update UI on main thread
                        ViewCompat.postOnAnimation(requireView(), () -> {
                            adapter.submit(allMyEvents);
                            setLoading(false);
                            showEmptyIfNeeded();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    android.util.Log.e("MyEventsFragment", "Failed to load events", e);
                    adapter.submit(new ArrayList<>());
                    setLoading(false);
                    showEmptyIfNeeded();
                });
    }

    private Task<Boolean> isInSelectedEntrants(String eventId, String uid) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        return db.collection("events")
                .document(eventId)
                .collection("SelectedEntrants")
                .document(uid)
                .get()
                .continueWith(task -> {
                    return task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                });
    }
    
    private Task<Boolean> hasAcceptedInvitation(String eventId, String uid) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        return db.collection("invitations")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "ACCEPTED")
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        return !task.getResult().isEmpty();
                    }
                    return false;
                });
    }
    
    private Task<Boolean> hasDeclinedInvitation(String eventId, String uid) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        return db.collection("invitations")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "DECLINED")
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        return !task.getResult().isEmpty();
                    }
                    return false;
                });
    }
    
    private Task<Boolean> isInNonSelectedEntrants(String eventId, String uid) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        return db.collection("events")
                .document(eventId)
                .collection("NonSelectedEntrants")
                .document(uid)
                .get()
                .continueWith(task -> {
                    return task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                });
    }
    
    private Task<Boolean> isInCancelledEntrants(String eventId, String uid) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        return db.collection("events")
                .document(eventId)
                .collection("CancelledEntrants")
                .document(uid)
                .get()
                .continueWith(task -> {
                    return task.isSuccessful() && task.getResult() != null && task.getResult().exists();
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
                intent.putExtra("eventDeadline", event.getDeadlineEpochMs());
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
