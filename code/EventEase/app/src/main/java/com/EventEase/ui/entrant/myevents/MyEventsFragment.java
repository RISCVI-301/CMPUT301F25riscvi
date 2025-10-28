package com.EventEase.ui.entrant.myevents;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.EventRepository;
import com.EventEase.data.InvitationListener;
import com.EventEase.data.InvitationRepository;
import com.EventEase.data.ListenerRegistration;
import com.EventEase.data.WaitlistRepository;
import com.EventEase.data.firebase.FirebaseDevGraph;
import com.EventEase.model.Event;
import com.EventEase.model.Invitation;
import com.example.eventease.R;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyEventsFragment extends Fragment {

    private static final FirebaseDevGraph GRAPH = new FirebaseDevGraph();

    private EventRepository eventRepo;
    private WaitlistRepository waitlistRepo;
    private InvitationRepository invitationRepo;
    private AuthManager auth;

    private RecyclerView list;
    private TextView emptyView;
    private ProgressBar progress;

    private final MyEventsAdapter adapter = new MyEventsAdapter();
    private ListenerRegistration inviteReg;

    private final Set<String> invitedEventIds = new HashSet<>();
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    public MyEventsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_my_events, container, false);

        list = root.findViewById(R.id.my_events_list);
        emptyView = root.findViewById(R.id.my_events_empty);
        progress = root.findViewById(R.id.my_events_progress);

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        // spacing
        final int spacing = dp(8);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                       @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                outRect.bottom = spacing;
                if (pos == 0) outRect.top = spacing;
            }
        });

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        eventRepo = GRAPH.events;
        waitlistRepo = GRAPH.waitlists;
        invitationRepo = GRAPH.invitations;
        auth = GRAPH.auth;

        setLoading(true);
        loadMyEvents();

        inviteReg = invitationRepo.listenActive(auth.getUid(), new InvitationListener() {
            @Override
            public void onChanged(List<Invitation> activeInvites) {
                invitedEventIds.clear();
                if (activeInvites != null) {
                    for (Invitation inv : activeInvites) invitedEventIds.add(inv.getEventId());
                }
                adapter.setInvitedEventIds(invitedEventIds);
            }
        });
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

            if (events == null || events.isEmpty()) {
                adapter.submit(new ArrayList<>());
                setLoading(false);
                showEmptyIfNeeded();
                return;
            }

            bg.execute(() -> {
                List<Event> mine = new ArrayList<>();
                String uid = auth.getUid();
                for (Event e : events) {
                    try {
                        Boolean joined = Tasks.await(waitlistRepo.isJoined(e.getId(), uid));
                        if (Boolean.TRUE.equals(joined)) {
                            mine.add(e);
                        }
                    } catch (Exception ignored) { }
                }
                if (!isAdded()) return;
                ViewCompat.postOnAnimation(requireView(), () -> {
                    adapter.submit(mine);
                    setLoading(false);
                    showEmptyIfNeeded();
                });
            });

        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
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

    private static class MyEventsAdapter extends RecyclerView.Adapter<MyEventVH> {
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
                    .inflate(R.layout.item_my_event_row, parent, false);
            return new MyEventVH(v);
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
        private final TextView subtitle;   // reusing event_price id as subtitle
        private final TextView invitedBadge;
        private final SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());

        MyEventVH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.event_image);
            name = itemView.findViewById(R.id.event_name);
            subtitle = itemView.findViewById(R.id.event_price);
            invitedBadge = itemView.findViewById(R.id.invited_badge);
        }

        void bind(Event e, boolean invited) {
            // title
            name.setText(e.getTitle() != null ? e.getTitle() : "Untitled");

            // subtitle: date • location (your layout originally said "price")
            String when = (e.getStartAt() != null) ? df.format(e.getStartAt()) : "TBD";
            String where = e.getLocation() != null ? e.getLocation() : "";
            subtitle.setText(where.isEmpty() ? when : (when + " • " + where));

            // image placeholder (leave existing src or set programmatically)
            // image.setImageResource(R.drawable.sample_event);

            // invited badge
            invitedBadge.setVisibility(invited ? View.VISIBLE : View.GONE);
        }
    }
}
