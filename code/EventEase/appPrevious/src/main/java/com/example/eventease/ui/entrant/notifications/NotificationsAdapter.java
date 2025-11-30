package com.example.eventease.ui.entrant.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.eventease.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationsAdapter extends ListAdapter<NotificationsAdapter.NotificationListItem, RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_NOTIFICATION = 1;

    private static final DiffUtil.ItemCallback<NotificationListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<NotificationListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull NotificationListItem oldItem, @NonNull NotificationListItem newItem) {
                    if (oldItem.isHeader != newItem.isHeader) {
                        return false;
                    }
                    if (oldItem.isHeader) {
                        return oldItem.headerText.equals(newItem.headerText);
                    }
                    return oldItem.notification.id.equals(newItem.notification.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull NotificationListItem oldItem, @NonNull NotificationListItem newItem) {
                    if (oldItem.isHeader != newItem.isHeader) {
                        return false;
                    }
                    if (oldItem.isHeader) {
                        return oldItem.headerText.equals(newItem.headerText);
                    }
                    return oldItem.notification.id.equals(newItem.notification.id) &&
                           oldItem.notification.title.equals(newItem.notification.title) &&
                           oldItem.notification.message.equals(newItem.notification.message);
                }
            };

    public NotificationsAdapter() {
        super(DIFF_CALLBACK);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_NOTIFICATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.entrant_item_notification_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.entrant_item_notification, parent, false);
            return new NotificationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NotificationListItem item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item.headerText);
        } else if (holder instanceof NotificationViewHolder) {
            ((NotificationViewHolder) holder).bind(item.notification);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView headerText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.sectionHeader);
        }

        void bind(String text) {
            if (headerText != null) {
                headerText.setText(text);
            }
        }
    }

    public static class NotificationListItem {
        public boolean isHeader;
        public String headerText;
        public NotificationsActivity.NotificationItem notification;

        public static NotificationListItem createHeader(String text) {
            NotificationListItem item = new NotificationListItem();
            item.isHeader = true;
            item.headerText = text;
            return item;
        }

        public static NotificationListItem createNotification(NotificationsActivity.NotificationItem notification) {
            NotificationListItem item = new NotificationListItem();
            item.isHeader = false;
            item.notification = notification;
            return item;
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final CardView cardView;
        private final TextView titleText;
        private final TextView messageText;
        private final TextView timeText;
        private final TextView eventTitleText;
        private final SimpleDateFormat dateFormat;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.notificationCard);
            titleText = itemView.findViewById(R.id.notificationTitle);
            messageText = itemView.findViewById(R.id.notificationMessage);
            timeText = itemView.findViewById(R.id.notificationTime);
            eventTitleText = itemView.findViewById(R.id.notificationEventTitle);
            dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        }

        void bind(NotificationsActivity.NotificationItem item) {
            if (titleText != null) {
                titleText.setText(item.title != null ? item.title : "Notification");
            }
            if (messageText != null) {
                messageText.setText(item.message != null ? item.message : "");
            }
            if (timeText != null) {
                timeText.setText(dateFormat.format(new Date(item.createdAt)));
            }
            if (eventTitleText != null) {
                if (item.eventTitle != null && !item.eventTitle.isEmpty()) {
                    eventTitleText.setText(item.eventTitle);
                    eventTitleText.setVisibility(View.VISIBLE);
                } else {
                    eventTitleText.setVisibility(View.GONE);
                }
            }
        }
    }
}

