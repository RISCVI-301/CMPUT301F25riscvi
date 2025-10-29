package com.EventEase.ui.entrant.discover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.data.EventRepository;
import com.EventEase.model.Event;
import com.example.eventease.App;
import com.example.eventease.R;

import com.google.android.gms.tasks.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiscoverFragment extends Fragment {

    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    private final EventsAdapter adapter = new EventsAdapter();
    private EventRepository events;

    public DiscoverFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_discover, container, false);
        list = root.findViewById(R.id.discover_list);
        progress = root.findViewById(R.id.discover_progress);
        empty = root.findViewById(R.id.discover_empty);

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        events = App.graph().events; // shared dev graph

        setLoading(true);
        Task<List<Event>> t = events.getOpenEvents(new Date());
        t.addOnSuccessListener(items -> {
            if (!isAdded()) return;
            adapter.submit(items == null ? new ArrayList<>() : items);
            setLoading(false);
            empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            adapter.submit(new ArrayList<>());
            setLoading(false);
            empty.setVisibility(View.VISIBLE);
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        list.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        empty.setVisibility(View.GONE);
    }

    // --- simple adapter ---

    private static class EventsAdapter extends RecyclerView.Adapter<EventVH> {
        private final List<Event> data = new ArrayList<>();
        void submit(List<Event> items) {
            data.clear();
            if (items != null) data.addAll(items);
            notifyDataSetChanged();
        }
        @NonNull @Override public EventVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_discover_event_row, parent, false);
            return new EventVH(v);
        }
        @Override public void onBindViewHolder(@NonNull EventVH h, int pos) { h.bind(data.get(pos)); }
        @Override public int getItemCount() { return data.size(); }
    }

    private static class EventVH extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView title;
        private final TextView subtitle;
        private final SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());

        EventVH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.event_image);
            title = itemView.findViewById(R.id.event_name);
            subtitle = itemView.findViewById(R.id.event_subtitle);
        }

        void bind(Event e) {
            title.setText(e.getTitle() == null ? "Untitled" : e.getTitle());
            String when = e.getStartAt() == null ? "TBD" : df.format(e.getStartAt());
            String where = e.getLocation() == null ? "" : e.getLocation();
            subtitle.setText(where.isEmpty() ? when : (when + " • " + where));

            // placeholder image for now; replace when you have real URLs
            image.setImageResource(R.drawable.card_image_placeholder);
        }
    }
}
