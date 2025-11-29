package com.example.eventease.ui.entrant.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.eventease.R;
import com.example.eventease.auth.DeviceAuthManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import android.content.SharedPreferences;
import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NotificationsActivity extends AppCompatActivity {
    private static final String TAG = "NotificationsActivity";
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyView;
    private NotificationsAdapter adapter;
    private FirebaseFirestore db;
    private DeviceAuthManager authManager;
    private ListenerRegistration notificationListener;
    private Set<String> loadedNotificationIds = new HashSet<>();
    private boolean isFirstLoad = true;
    private boolean initialLoadComplete = false;
    private long lastSeenTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entrant_activity_notifications);

        db = FirebaseFirestore.getInstance();
        authManager = new DeviceAuthManager(this);
        
        SharedPreferences prefs = getSharedPreferences("EventEasePrefs", Context.MODE_PRIVATE);
        lastSeenTime = prefs.getLong("lastNotificationSeenTime", 0);
        
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong("lastNotificationSeenTime", currentTime).apply();

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        recyclerView = findViewById(R.id.notificationsRecyclerView);
        progressBar = findViewById(R.id.notificationsProgressBar);
        emptyView = findViewById(R.id.notificationsEmptyView);

        adapter = new NotificationsAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFirstLoad) {
            initialLoadComplete = false;
            loadedNotificationIds.clear();
            loadNotifications();
        } else {
            isFirstLoad = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }

    private void setupRealtimeListener() {
        if (notificationListener != null) {
            return;
        }

        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            return;
        }

        notificationListener = db.collection("notificationRequests")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (!initialLoadComplete || e != null || querySnapshot == null) {
                        return;
                    }

                    List<NotificationItem> newItems = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String docId = doc.getId();
                        
                        if (loadedNotificationIds.contains(docId)) {
                            continue;
                        }

                        Object userIdsObj = doc.get("userIds");
                        List<String> userIds = null;
                        if (userIdsObj instanceof List) {
                            userIds = (List<String>) userIdsObj;
                        } else if (userIdsObj != null) {
                            android.util.Log.w(TAG, "Real-time: userIds is not a List for doc " + docId + ", type: " + userIdsObj.getClass().getName());
                            continue;
                        }

                        if (userIds == null || !userIds.contains(uid)) {
                            android.util.Log.d(TAG, "Real-time: Document " + docId + " does not contain user " + uid);
                            continue;
                        }

                        NotificationItem item = new NotificationItem();
                        item.id = docId;
                        item.title = doc.getString("title");
                        item.message = doc.getString("message");
                        item.eventId = doc.getString("eventId");
                        item.eventTitle = doc.getString("eventTitle");
                        Long createdAt = doc.getLong("createdAt");
                        item.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
                        item.groupType = doc.getString("groupType");
                        
                        newItems.add(item);
                        loadedNotificationIds.add(item.id);
                    }

                    if (!newItems.isEmpty()) {
                        List<NotificationItem> allNotifications = new ArrayList<>();
                        for (NotificationsAdapter.NotificationListItem item : adapter.getCurrentList()) {
                            if (!item.isHeader) {
                                allNotifications.add(item.notification);
                            }
                        }
                        allNotifications.addAll(newItems);
                        allNotifications.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                        
                        List<NotificationsAdapter.NotificationListItem> listItems = createSectionedList(allNotifications);
                        adapter.submitList(listItems);
                        showList();
                    }
                });
    }

    private void loadNotifications() {
        String uid = authManager.getUid();
        if (uid == null || uid.isEmpty()) {
            showEmpty();
            return;
        }

        setLoading(true);

        loadNotificationRequests(uid, new ArrayList<>());
    }

    private void loadNotificationRequests(String uid, List<NotificationItem> existingNotifications) {
        android.util.Log.d(TAG, "Loading notification requests for user: " + uid);
        android.util.Log.d(TAG, "Current existingNotifications size: " + existingNotifications.size());
        
        db.collection("notificationRequests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d(TAG, "Query success! Found " + querySnapshot.size() + " total notificationRequests documents");
                    
                    int matchedCount = 0;
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String docId = doc.getId();
                        Object userIdsObj = doc.get("userIds");
                        List<String> userIds = null;
                        if (userIdsObj instanceof List) {
                            userIds = (List<String>) userIdsObj;
                        } else if (userIdsObj != null) {
                            android.util.Log.w(TAG, "userIds is not a List for doc " + docId);
                            continue;
                        }
                        
                        if (userIds == null || !userIds.contains(uid)) {
                            continue;
                        }
                        
                        matchedCount++;
                        NotificationItem item = new NotificationItem();
                        item.id = docId;
                        item.title = doc.getString("title");
                        item.message = doc.getString("message");
                        item.eventId = doc.getString("eventId");
                        item.eventTitle = doc.getString("eventTitle");
                        Long createdAt = doc.getLong("createdAt");
                        item.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
                        item.groupType = doc.getString("groupType");
                        existingNotifications.add(item);
                        loadedNotificationIds.add(item.id);
                        
                        android.util.Log.d(TAG, "Added notification: " + item.title + " (created: " + item.createdAt + ")");
                    }
                    
                    android.util.Log.d(TAG, "Matched " + matchedCount + " notifications for user " + uid);
                    android.util.Log.d(TAG, "Total existingNotifications after matching: " + existingNotifications.size());
                    
                    loadInvitationsAsNotifications(uid, existingNotifications);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Failed to load notifications with orderBy", e);
                    if (e.getMessage() != null && e.getMessage().contains("index")) {
                        android.util.Log.d(TAG, "Index error detected, falling back to query without orderBy");
                        loadNotificationsWithoutOrderBy(uid, existingNotifications);
                    } else {
                        loadInvitationsAsNotifications(uid, existingNotifications);
                    }
                });
    }

    private void loadNotificationsWithoutOrderBy(String uid, List<NotificationItem> existingNotifications) {
        android.util.Log.d(TAG, "Loading notifications without orderBy (fallback) for user: " + uid);
        db.collection("notificationRequests")
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d(TAG, "Alternative query returned " + querySnapshot.size() + " notification requests");
                    android.util.Log.d(TAG, "Checking each document for user " + uid);
                    
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String docId = doc.getId();
                        Object userIdsObj = doc.get("userIds");
                        List<String> userIds = null;
                        if (userIdsObj instanceof List) {
                            userIds = (List<String>) userIdsObj;
                        } else if (userIdsObj != null) {
                            android.util.Log.w(TAG, "userIds is not a List for doc " + docId + ", type: " + userIdsObj.getClass().getName());
                            continue;
                        }
                        
                        if (userIds == null || !userIds.contains(uid)) {
                            android.util.Log.d(TAG, "Document " + docId + " does not contain user " + uid);
                            continue;
                        }
                        
                        NotificationItem item = new NotificationItem();
                        item.id = docId;
                        item.title = doc.getString("title");
                        item.message = doc.getString("message");
                        item.eventId = doc.getString("eventId");
                        item.eventTitle = doc.getString("eventTitle");
                        Long createdAt = doc.getLong("createdAt");
                        item.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
                        item.groupType = doc.getString("groupType");
                        existingNotifications.add(item);
                        loadedNotificationIds.add(item.id);
                    }
                    
                    existingNotifications.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                    loadInvitationsAsNotifications(uid, existingNotifications);
                })
                .addOnFailureListener(e -> {
                    loadInvitationsAsNotifications(uid, existingNotifications);
                });
    }

    private void loadInvitationsAsNotifications(String uid, List<NotificationItem> existingNotifications) {
        db.collection("invitations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(invitationSnapshot -> {
                    if (invitationSnapshot.isEmpty()) {
                        List<NotificationItem> finalList = new ArrayList<>(existingNotifications);
                        finalList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                        List<NotificationsAdapter.NotificationListItem> listItems = createSectionedList(finalList);
                        adapter.submitList(listItems);
                        setLoading(false);
                        if (finalList.isEmpty()) {
                            showEmpty();
                        } else {
                            showList();
                        }
                        
                        if (!initialLoadComplete) {
                            initialLoadComplete = true;
                            setupRealtimeListener();
                        }
                        return;
                    }
                    
                    List<com.google.android.gms.tasks.Task<DocumentSnapshot>> eventTasks = new ArrayList<>();
                    List<QueryDocumentSnapshot> invitationDocs = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot invitationDoc : invitationSnapshot) {
                        String eventId = invitationDoc.getString("eventId");
                        if (eventId != null && !eventId.isEmpty()) {
                            invitationDocs.add(invitationDoc);
                            eventTasks.add(db.collection("events").document(eventId).get());
                        }
                    }
                    
                    if (eventTasks.isEmpty()) {
                        android.util.Log.d(TAG, "No valid event IDs found in invitations. Total notifications: " + existingNotifications.size());
                        List<NotificationItem> finalList = new ArrayList<>(existingNotifications);
                        finalList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                        android.util.Log.d(TAG, "Submitting " + finalList.size() + " notifications to adapter");
                        List<NotificationsAdapter.NotificationListItem> listItems = createSectionedList(finalList);
                        adapter.submitList(listItems);
                        setLoading(false);
                        if (finalList.isEmpty()) {
                            showEmpty();
                        } else {
                            showList();
                        }
                        
                        if (!initialLoadComplete) {
                            android.util.Log.d(TAG, "Initial load complete - setting up real-time listener");
                            initialLoadComplete = true;
                            setupRealtimeListener();
                        }
                        return;
                    }
                    
                    android.util.Log.d(TAG, "Waiting for " + eventTasks.size() + " event details to load...");
                    com.google.android.gms.tasks.Tasks.whenAllComplete(eventTasks)
                            .addOnSuccessListener(tasks -> {
                                android.util.Log.d(TAG, "All event tasks completed. Processing results...");
                                int successCount = 0;
                                
                                for (int i = 0; i < tasks.size() && i < invitationDocs.size(); i++) {
                                    com.google.android.gms.tasks.Task<?> task = tasks.get(i);
                                    QueryDocumentSnapshot invitationDoc = invitationDocs.get(i);
                                    
                                    if (task.isSuccessful()) {
                                        Object result = task.getResult();
                                        if (result instanceof DocumentSnapshot) {
                                            DocumentSnapshot eventDoc = (DocumentSnapshot) result;
                                            
                                            if (eventDoc != null && eventDoc.exists()) {
                                                String eventTitle = eventDoc.getString("title");
                                                Long issuedAt = invitationDoc.getLong("issuedAt");
                                                
                                                NotificationItem item = new NotificationItem();
                                                item.id = "invitation_" + invitationDoc.getId();
                                                item.title = "You've been invited!";
                                                item.message = "You've been selected for \"" + (eventTitle != null ? eventTitle : "an event") + "\". Tap to view details and accept your invitation.";
                                                item.eventId = invitationDoc.getString("eventId");
                                                item.eventTitle = eventTitle;
                                                item.createdAt = issuedAt != null ? issuedAt : System.currentTimeMillis();
                                                item.groupType = "invitation";
                                                
                                                existingNotifications.add(item);
                                                successCount++;
                                                android.util.Log.d(TAG, "Added invitation notification for event: " + eventTitle);
                                            } else {
                                                android.util.Log.w(TAG, "Event document does not exist for invitation: " + invitationDoc.getId());
                                            }
                                        } else {
                                            android.util.Log.w(TAG, "Task result is not a DocumentSnapshot: " + (result != null ? result.getClass().getName() : "null"));
                                        }
                                    } else {
                                        android.util.Log.w(TAG, "Failed to load event for invitation: " + invitationDoc.getId(), task.getException());
                                    }
                                }
                                
                                android.util.Log.d(TAG, "Successfully processed " + successCount + " invitations. Total notifications: " + existingNotifications.size());
                                List<NotificationItem> finalList = new ArrayList<>(existingNotifications);
                                finalList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                                android.util.Log.d(TAG, "Submitting " + finalList.size() + " notifications to adapter");
                                List<NotificationsAdapter.NotificationListItem> listItems = createSectionedList(finalList);
                                adapter.submitList(listItems);
                                setLoading(false);
                                if (finalList.isEmpty()) {
                                    showEmpty();
                                } else {
                                    showList();
                                }
                                
                                if (!initialLoadComplete) {
                                    android.util.Log.d(TAG, "Initial load complete - setting up real-time listener");
                                    initialLoadComplete = true;
                                    setupRealtimeListener();
                                }
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.e(TAG, "Failed to load events for invitations", e);
                                List<NotificationItem> finalList = new ArrayList<>(existingNotifications);
                                finalList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                                android.util.Log.d(TAG, "Submitting " + finalList.size() + " notifications to adapter (after error)");
                                List<NotificationsAdapter.NotificationListItem> listItems = createSectionedList(finalList);
                                adapter.submitList(listItems);
                                setLoading(false);
                                if (finalList.isEmpty()) {
                                    showEmpty();
                                } else {
                                    showList();
                                }
                                
                                if (!initialLoadComplete) {
                                    android.util.Log.d(TAG, "Initial load complete (after error) - setting up real-time listener");
                                    initialLoadComplete = true;
                                    setupRealtimeListener();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Failed to load invitations", e);
                    List<NotificationItem> finalList = new ArrayList<>(existingNotifications);
                    finalList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                    android.util.Log.d(TAG, "Submitting " + finalList.size() + " notifications to adapter (after error)");
                    List<NotificationsAdapter.NotificationListItem> listItems = createSectionedList(finalList);
                    adapter.submitList(listItems);
                    setLoading(false);
                    if (finalList.isEmpty()) {
                        showEmpty();
                    } else {
                        showList();
                    }
                    
                    if (!initialLoadComplete) {
                        android.util.Log.d(TAG, "Initial load complete (after error) - setting up real-time listener");
                        initialLoadComplete = true;
                        setupRealtimeListener();
                    }
                });
    }

    private List<NotificationsAdapter.NotificationListItem> createSectionedList(List<NotificationItem> notifications) {
        List<NotificationsAdapter.NotificationListItem> sectionedList = new ArrayList<>();
        
        List<NotificationItem> newNotifications = new ArrayList<>();
        List<NotificationItem> seenNotifications = new ArrayList<>();
        
        for (NotificationItem item : notifications) {
            if (item.createdAt > lastSeenTime) {
                newNotifications.add(item);
            } else {
                seenNotifications.add(item);
            }
        }
        
        if (!newNotifications.isEmpty()) {
            sectionedList.add(NotificationsAdapter.NotificationListItem.createHeader("New"));
            for (NotificationItem item : newNotifications) {
                sectionedList.add(NotificationsAdapter.NotificationListItem.createNotification(item));
            }
        }
        
        if (!seenNotifications.isEmpty()) {
            sectionedList.add(NotificationsAdapter.NotificationListItem.createHeader("Earlier"));
            for (NotificationItem item : seenNotifications) {
                sectionedList.add(NotificationsAdapter.NotificationListItem.createNotification(item));
            }
        }
        
        android.util.Log.d(TAG, "Created sectioned list: " + newNotifications.size() + " new, " + seenNotifications.size() + " earlier");
        return sectionedList;
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(loading ? View.GONE : View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showEmpty() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    private void showList() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
    }

    public static class NotificationItem {
        public String id;
        public String title;
        public String message;
        public String eventId;
        public String eventTitle;
        public long createdAt;
        public String groupType;
    }
}

