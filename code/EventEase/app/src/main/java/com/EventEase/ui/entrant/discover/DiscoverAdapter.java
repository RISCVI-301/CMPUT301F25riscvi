package com.EventEase.ui.entrant.discover;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.eventease.R;
import java.util.List;

public class DiscoverAdapter extends RecyclerView.Adapter<DiscoverAdapter.VH> {

    public static class EventUi {
        public final String title;
        public EventUi(String title) { this.title = title; }
    }

    private final List<EventUi> items;
    private final View.OnClickListener onClick;

    public DiscoverAdapter(List<EventUi> items, View.OnClickListener onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
        }
        void bind(EventUi item, View.OnClickListener onClick) {
            tvTitle.setText(item.title);
            itemView.setOnClickListener(onClick);
            itemView.setTag(item);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), onClick);
    }

    @Override
    public int getItemCount() { return items.size(); }
}
