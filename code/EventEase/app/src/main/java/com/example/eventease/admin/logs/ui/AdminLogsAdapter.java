package com.example.eventease.admin.logs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.logs.data.Notification;

import java.util.ArrayList;
import java.util.List;

public class AdminLogsAdapter extends RecyclerView.Adapter<AdminLogsAdapter.LogViewHolder> {

    private final List<Notification> items = new ArrayList<>();

    public void setItems(@NonNull List<Notification> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_notification_log, parent, false);
        return new LogViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        Notification notification = items.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvDate;
        private final TextView tvTitle;
        private final TextView tvMessage;
        private final TextView tvEventTitle;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvLogDate);
            tvTitle = itemView.findViewById(R.id.tvLogTitle);
            tvMessage = itemView.findViewById(R.id.tvLogMessage);
            tvEventTitle = itemView.findViewById(R.id.tvLogEventTitle);
        }

        void bind(@NonNull Notification n) {
            // Uses your existing getters / formatting in Notification.java
            tvDate.setText(n.getCreatedAt());                  // formatted "yyyy-MM-dd HH:mm"
            tvTitle.setText(n.getNotificationTitle());
            tvMessage.setText(n.getNotificationMessage());
            tvEventTitle.setText(n.getEventTitle());
        }
    }
}