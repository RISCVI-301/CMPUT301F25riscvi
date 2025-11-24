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

import com.example.eventease.auth.AuthManager;
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
    private AuthManager auth;
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
        auth           = App.graph().auth;
        admittedRepo   = App.graph().admitted;

        setLoading(true);
        loadMyEvents();

        inviteReg = invitationRepo.listenActive(auth.getUid(), new InvitationListener() {
            @Override
            public void onChanged(List<Invitation> activeInvites) {
                android.util.Log.d("MyEventsFragment", "Invitation listener callback: " + (activeInvites != null ? activeInvites.size() : 0) + " invitations");
                invitedEventIds.clear();
                eventIdToInvitationId.clear();
                if (activeInvites != null) {
                    for (Invitation inv : activeInvites) {
                        // Only show teal dot for PENDING invitations
                        if (inv.getStatus() == com.example.eventease.model.Invitation.Status.PENDING) {
                            invitedEventIds.add(inv.getEventId());
                            eventIdToInvitationId.put(inv.getEventId(), inv.getId());
                            android.util.Log.d("MyEventsFragment", "Added invitation for event: " + inv.getEventId() + " (invitationId: " + inv.getId() + ")");
                        }
                    }
                }
                android.util.Log.d("MyEventsFragment", "Setting invitedEventIds: " + invitedEventIds.size() + " events");
                adapter.setInvitedEventIds(invitedEventIds);
                
                // Reload events list when invitations change (e.g., after accepting/declining)
                // This ensures admitted events are removed from waitlisted section
                if (eventRepo != null && waitlistRepo != null && admittedRepo != null) {
                    android.util.Log.d("MyEventsFragment", "Reloading events list due to invitation change");
                    loadMyEvents();
                }
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
        String uid = auth.getUid();
        if (uid == null || uid.isEmpty()) {
            android.util.Log.e("MyEventsFragment", "User UID is null or empty");
            adapter.submit(new ArrayList<>());
            setLoading(false);
            showEmptyIfNeeded();
            return;
        }

        android.util.Log.d("MyEventsFragment", "Loading events for user: " + uid);
        android.util.Log.d("MyEventsFragment", "eventRepo: " + eventRepo + ", waitlistRepo: " + waitlistRepo + ", admittedRepo: " + admittedRepo);

        // Load admitted upcoming events first
        Task<List<Event>> admittedEventsTask = admittedRepo.getUpcomingEvents(uid);
        admittedEventsTask.addOnFailureListener(e -> {
            android.util.Log.e("MyEventsFragment", "Failed to load admitted upcoming events", e);
        });
        
        // Load previous events (deadline passed)
        Task<List<Event>> previousEventsTask = admittedRepo.getPreviousEvents(uid);
        previousEventsTask.addOnFailureListener(e -> {
            android.util.Log.e("MyEventsFragment", "Failed to load previous events", e);
        });
        
        // Load open events for waitlisted/selected filtering
        Task<List<Event>> openEventsTask = eventRepo.getOpenEvents(new Date());
        openEventsTask.addOnFailureListener(e -> {
            android.util.Log.e("MyEventsFragment", "Failed to load open events", e);
        });
        
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
                        
                        // Track admitted event IDs to exclude them from waitlisted section
                        // This fragment is ONLY for waitlisted/selected events, NOT admitted events
                        // Admitted events should appear in UpcomingEventsFragment instead
                        Set<String> admittedEventIds = new HashSet<>();
                        for (Event event : finalAdmittedEvents) {
                            if (event != null && event.getId() != null) {
                                admittedEventIds.add(event.getId());
                                android.util.Log.d("MyEventsFragment", "User is admitted to event: " + event.getTitle() + " (id: " + event.getId() + "), will EXCLUDE from waitlisted section");
                            }
                        }
                        
                        android.util.Log.d("MyEventsFragment", "MyEventsFragment (Waitlisted Events) will exclude " + admittedEventIds.size() + " admitted events");
                        
                        // Process waitlisted/selected events ONLY
                        // Waitlisted events area shows:
                        // 1. Events where user has PENDING invitation (from invitations collection) - with teal dot
                        // 2. Events where user is in WaitlistedEntrants subcollection
                        // BUT NEVER shows events where user is already admitted
                        long currentTime = System.currentTimeMillis();
                        
                        List<Event> waitlistedSelected = new ArrayList<>();
                        
                        // Get events with PENDING invitations (these should show with teal dot)
                        Set<String> eventsWithPendingInvitations = new HashSet<>();
                        if (invitedEventIds != null) {
                            eventsWithPendingInvitations.addAll(invitedEventIds);
                        }
                        
                        for (Event e : finalOpenEvents) {
                            try {
                                // Skip events without valid IDs
                                if (e == null || e.getId() == null || e.getId().isEmpty()) {
                                    android.util.Log.w("MyEventsFragment", "Skipping event with null/empty ID");
                                    continue;
                                }
                                
                                String eventId = e.getId();
                                
                                // Skip if user is admitted to this event
                                // This fragment should ONLY show waitlisted/selected events, NOT admitted events
                                if (admittedEventIds.contains(eventId)) {
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " (id: " + eventId + ") - User is admitted, skipping from waitlisted section");
                                    continue;
                                }
                                
                                // CRITICAL: Check admitted status FIRST before any other checks
                                // This is the source of truth - if user is in AdmittedEntrants, they should NOT be in waitlisted
                                // Use Source.SERVER to bypass cache and get fresh data
                                Boolean admitted = null;
                                try {
                                    com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                                    com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> admittedCheck = db.collection("events")
                                            .document(eventId)
                                            .collection("AdmittedEntrants")
                                            .document(uid)
                                            .get(com.google.firebase.firestore.Source.SERVER);
                                    
                                    com.google.firebase.firestore.DocumentSnapshot admittedDoc = Tasks.await(admittedCheck);
                                    admitted = admittedDoc != null && admittedDoc.exists();
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " (id: " + eventId + ") - Admitted check result (SERVER): " + admitted + " for uid: " + uid);
                                } catch (Exception ex) {
                                    android.util.Log.e("MyEventsFragment", "Error checking admitted status for event " + eventId + ", uid: " + uid, ex);
                                    admitted = false;
                                }
                                
                                // If user is admitted, skip entirely (don't show in waitlisted, regardless of other statuses)
                                if (admitted != null && admitted == true) {
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " (id: " + eventId + ") - User is ADMITTED, skipping from waitlisted section");
                                    continue;
                                }
                                
                                // Only check other statuses if user is NOT admitted
                                Boolean joined = null;
                                Boolean hasPendingInvitation = false;
                                
                                // Verify invitation actually exists and is PENDING before using cached set
                                // This prevents stale data from invitation listener causing events to show incorrectly
                                if (eventsWithPendingInvitations.contains(eventId)) {
                                    // Double-check that invitation actually exists and is PENDING
                                    // This is important because the invitation listener might have stale data
                                    // (e.g., invitation was deleted when user accepted, but listener hasn't updated yet)
                                    // Use same query pattern as elsewhere: query by uid and status, filter by eventId in memory to avoid index requirements
                                    try {
                                        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                                        // Use Source.SERVER to bypass cache and get fresh data from server
                                        // This ensures we don't see stale invitations that were deleted after acceptance
                                        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> invitationCheck = db.collection("invitations")
                                                .whereEqualTo("uid", uid)
                                                .whereEqualTo("status", "PENDING")
                                                .get(com.google.firebase.firestore.Source.SERVER);
                                        
                                        com.google.firebase.firestore.QuerySnapshot invitationSnapshot = Tasks.await(invitationCheck);
                                        if (invitationSnapshot != null && !invitationSnapshot.isEmpty()) {
                                            // Filter by eventId in memory
                                            com.google.firebase.firestore.QueryDocumentSnapshot matchingInv = null;
                                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : invitationSnapshot) {
                                                String invEventId = doc.getString("eventId");
                                                if (eventId.equals(invEventId)) {
                                                    matchingInv = doc;
                                                    break;
                                                }
                                            }
                                            
                                            if (matchingInv != null) {
                                                // Verify invitation hasn't expired
                                                Long expiresAtMs = matchingInv.getLong("expiresAt");
                                                if (expiresAtMs != null && expiresAtMs > 0) {
                                                    long expiresAt = expiresAtMs;
                                                    if (expiresAt > currentTime) {
                                                        hasPendingInvitation = true;
                                                        android.util.Log.d("MyEventsFragment", "Verified PENDING invitation exists for event " + eventId);
                                                    } else {
                                                        android.util.Log.d("MyEventsFragment", "Invitation for event " + eventId + " has expired, ignoring");
                                                    }
                                                } else {
                                                    hasPendingInvitation = true;
                                                    android.util.Log.d("MyEventsFragment", "Verified PENDING invitation exists for event " + eventId + " (no expiration)");
                                                }
                                            } else {
                                                android.util.Log.d("MyEventsFragment", "Cached invitation set has event " + eventId + " but no PENDING invitation found in Firestore (likely deleted after acceptance)");
                                            }
                                        } else {
                                            android.util.Log.d("MyEventsFragment", "Cached invitation set has event " + eventId + " but no PENDING invitations found in Firestore (likely deleted after acceptance)");
                                        }
                                    } catch (Exception ex) {
                                        android.util.Log.e("MyEventsFragment", "Error verifying invitation for event " + eventId, ex);
                                        // If verification fails, don't trust the cached set - assume no invitation
                                        hasPendingInvitation = false;
                                    }
                                }
                                
                                try {
                                    joined = Tasks.await(waitlistRepo.isJoined(eventId, uid));
                                } catch (Exception ex) {
                                    android.util.Log.e("MyEventsFragment", "Error checking waitlist status for event " + eventId, ex);
                                    joined = false;
                                }
                                
                                android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " (id: " + eventId + ") - Joined: " + joined + ", HasPendingInvitation: " + hasPendingInvitation + ", Admitted: " + admitted);
                                
                                // Show if:
                                // 1. User has PENDING invitation (verified to exist in Firestore), OR
                                // 2. User is in WaitlistedEntrants subcollection
                                // (Admitted check already done above - if admitted, we would have continued)
                                if (hasPendingInvitation || Boolean.TRUE.equals(joined)) {
                                    // Double-check admitted status one more time before adding (defensive check)
                                    // This prevents race conditions where status might have changed
                                    // Use Source.SERVER to bypass cache
                                    try {
                                        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                                        com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> finalAdmittedCheck = db.collection("events")
                                                .document(eventId)
                                                .collection("AdmittedEntrants")
                                                .document(uid)
                                                .get(com.google.firebase.firestore.Source.SERVER);
                                        
                                        com.google.firebase.firestore.DocumentSnapshot finalAdmittedDoc = Tasks.await(finalAdmittedCheck);
                                        Boolean isFinalAdmitted = finalAdmittedDoc != null && finalAdmittedDoc.exists();
                                        if (isFinalAdmitted != null && isFinalAdmitted == true) {
                                            android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " (id: " + eventId + ") - Final admitted check (SERVER): TRUE, skipping from waitlisted");
                                            continue;
                                        }
                                    } catch (Exception ex) {
                                        android.util.Log.e("MyEventsFragment", "Error in final admitted check for event " + eventId, ex);
                                        // Continue with adding if check fails (better to show than hide)
                                    }
                                    
                                    android.util.Log.d("MyEventsFragment", "Adding event " + e.getTitle() + " (id: " + eventId + ") to waitlisted section");
                                    waitlistedSelected.add(e);
                                    eventIds.add(eventId);
                                } else {
                                    android.util.Log.d("MyEventsFragment", "Event " + e.getTitle() + " (id: " + eventId + ") - Not in waitlist and no pending invitation, skipping");
                                }
                            } catch (Exception ex) {
                                android.util.Log.e("MyEventsFragment", "Error checking waitlist for event " + (e != null && e.getId() != null ? e.getId() : "null"), ex);
                            }
                        }
                        
                        // Add waitlisted/selected events ONLY (admitted events should NOT be here)
                        allMyEvents.addAll(waitlistedSelected);
                        
                        // FINAL SAFETY CHECK: Remove any admitted events that might have slipped through
                        // This is a defensive check to ensure NO admitted events appear in this fragment
                        // This fragment is ONLY for waitlisted/selected events
                        android.util.Log.d("MyEventsFragment", "Final safety check: Checking " + allMyEvents.size() + " waitlisted events, will remove any that are admitted");
                        
                        List<Event> eventsToRemove = new ArrayList<>();
                        int checkedCount = 0;
                        for (Event event : allMyEvents) {
                            if (event != null && event.getId() != null) {
                                String eventId = event.getId();
                                
                                // Skip if we already know it's admitted
                                if (admittedEventIds.contains(eventId)) {
                                    android.util.Log.w("MyEventsFragment", "*** FOUND ADMITTED EVENT IN WAITLISTED LIST ***");
                                    android.util.Log.w("MyEventsFragment", "Event: " + event.getTitle() + " (id: " + eventId + ")");
                                    android.util.Log.w("MyEventsFragment", "User: " + uid);
                                    android.util.Log.w("MyEventsFragment", "Removing from waitlisted section (should appear in UpcomingEventsFragment instead)");
                                    eventsToRemove.add(event);
                                    continue;
                                }
                                
                                // For all other events, verify user is NOT admitted
                                // Use Source.SERVER to bypass cache
                                checkedCount++;
                                try {
                                    com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                                    com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> finalCheck = db.collection("events")
                                            .document(eventId)
                                            .collection("AdmittedEntrants")
                                            .document(uid)
                                            .get(com.google.firebase.firestore.Source.SERVER);
                                    
                                    com.google.firebase.firestore.DocumentSnapshot finalDoc = Tasks.await(finalCheck);
                                    Boolean isAdmitted = finalDoc != null && finalDoc.exists();
                                    android.util.Log.d("MyEventsFragment", "Final check for event " + event.getTitle() + " (id: " + eventId + "): admitted=" + isAdmitted + " (SERVER)");
                                    if (isAdmitted != null && isAdmitted == true) {
                                        android.util.Log.w("MyEventsFragment", "*** FOUND ADMITTED EVENT IN WAITLISTED SECTION ***");
                                        android.util.Log.w("MyEventsFragment", "Event: " + event.getTitle() + " (id: " + eventId + ")");
                                        android.util.Log.w("MyEventsFragment", "User: " + uid);
                                        android.util.Log.w("MyEventsFragment", "Removing from waitlisted section (should appear in UpcomingEventsFragment instead)");
                                        eventsToRemove.add(event);
                                    }
                                } catch (Exception ex) {
                                    android.util.Log.e("MyEventsFragment", "Error in final admitted check for event " + eventId + ", uid: " + uid, ex);
                                    android.util.Log.e("MyEventsFragment", "Exception details: " + ex.getMessage());
                                    if (ex.getCause() != null) {
                                        android.util.Log.e("MyEventsFragment", "Cause: " + ex.getCause().getMessage());
                                    }
                                }
                            }
                        }
                        
                        android.util.Log.d("MyEventsFragment", "Final safety check: Checked " + checkedCount + " events, found " + eventsToRemove.size() + " admitted events to remove");
                        
                        // Remove admitted events from waitlisted section
                        // They should appear in UpcomingEventsFragment instead
                        if (!eventsToRemove.isEmpty()) {
                            android.util.Log.w("MyEventsFragment", "Removing " + eventsToRemove.size() + " admitted events from waitlisted section");
                            allMyEvents.removeAll(eventsToRemove);
                        }
                        
                        android.util.Log.d("MyEventsFragment", "Total waitlisted/selected events for user: " + allMyEvents.size() + " (excluded " + admittedEventIds.size() + " admitted events)");
                        
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
