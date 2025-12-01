package com.example.eventease.admin.profile.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.eventease.ui.entrant.profile.ProfileDeletionHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.ArrayList;
import java.util.List;

public class AdminProfileDatabaseController {

    private static final String TAG = "AdminProfileDB";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface ProfilesCallback {
        void onLoaded(@NonNull List<UserProfile> profiles);
        void onError(@NonNull Exception e);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(@NonNull Exception e);
    }

    public void fetchProfiles(@NonNull final ProfilesCallback cb) {
        // Use UserRoleChecker to verify admin role
        com.example.eventease.auth.UserRoleChecker.isAdmin()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        Log.e(TAG, "fetchProfiles: Failed to verify admin status. Error: " + (e != null ? e.getMessage() : "Unknown error"), e);
                        cb.onError(new IllegalStateException("Failed to verify admin status: " + (e != null ? e.getMessage() : "Unknown error")));
                        return;
                    }
                    
                    Boolean isAdmin = task.getResult();
                    if (isAdmin == null || !isAdmin) {
                        Log.e(TAG, "fetchProfiles: Current user is not an admin. isAdmin result: " + isAdmin);
                        cb.onError(new SecurityException("Only administrators can view all profiles"));
                        return;
                    }
                    
                    Log.d(TAG, "fetchProfiles: Admin verified, proceeding to fetch all profiles");
                    // User is admin, proceed to fetch all profiles
                    fetchAllProfiles(cb);
                });
    }
    
    private void fetchAllProfiles(@NonNull final ProfilesCallback cb) {
        db.collection("users")
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    List<UserProfile> list = new ArrayList<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String uid = d.getId();
                        String email = getStr(d, "email");
                        String name = getStr(d, "name");
                        String phoneNumber = getStr(d, "phoneNumber");
                        Long createdAt = d.getLong("createdAt");

                        // Get roles
                        List<String> roles = new ArrayList<>();
                        Object rolesObj = d.get("roles");
                        if (rolesObj instanceof List<?>) {
                            for (Object role : (List<?>) rolesObj) {
                                if (role != null) {
                                    roles.add(role.toString());
                                }
                            }
                        }

                        // Organizer application metadata (optional fields on user document)
                        String organizerApplicationStatus = getStr(d, "organizerApplicationStatus");
                        String organizerApplicationIdImageUrl = getStr(d, "organizerApplicationIdImageUrl");

                        UserProfile profile = new UserProfile(uid, email, name, phoneNumber,
                                roles, createdAt != null ? createdAt : 0L);
                        profile.setOrganizerApplicationStatus(organizerApplicationStatus);
                        profile.setOrganizerApplicationIdImageUrl(organizerApplicationIdImageUrl);

                        list.add(profile);
                    }
                    Log.d(TAG, "fetchProfiles: Successfully loaded " + list.size() + " profiles");
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> {
                    String errorMsg = e.getMessage();
                    Log.e(TAG, "fetchProfiles: Failed to read users collection. Error: " + errorMsg, e);
                    if (errorMsg != null && errorMsg.contains("permission")) {
                        cb.onError(new SecurityException("Permission denied. Please check Firestore security rules to allow admins to read all user profiles."));
                    } else {
                        cb.onError(e);
                    }
                });
    }

    private static String getStr(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    public void deleteProfile(@NonNull Context context, @NonNull UserProfile profile, @NonNull DeleteCallback callback) {
        String uid = profile.getUid();
        if (uid == null || uid.isEmpty()) {
            callback.onError(new IllegalArgumentException("Profile UID is null or empty"));
            return;
        }

        Log.d(TAG, "Starting deletion of profile: " + uid);
        removeUserFromEventLists(uid);

        // If the user is an organizer, delete all of their events first
        Task<Void> organizerEventsTask = Tasks.forResult(null);
        if (profile.getRoles() != null && profile.getRoles().contains("organizer")) {
            Log.d(TAG, "deleteProfile: User is an organizer, deleting their events. uid=" + uid);
            organizerEventsTask = deleteOrganizerEvents(uid);
        }

        // Wait for organizer events to be deleted (if applicable), then proceed with user references
        organizerEventsTask
                .continueWithTask(task -> {
                    // Use ProfileDeletionHelper to delete all user references
                    ProfileDeletionHelper deletionHelper = new ProfileDeletionHelper(context);
                    TaskCompletionSource<Void> completionSource = new TaskCompletionSource<>();
                    deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
                        @Override
                        public void onDeletionComplete() {
                            completionSource.setResult(null);
                        }

                        @Override
                        public void onDeletionFailure(String error) {
                            Log.w(TAG, "Failed to delete some user references: " + error + ", proceeding with document deletion");
                            // Still proceed even if some references failed
                            completionSource.setResult(null);
                        }
                    });
                    return completionSource.getTask();
                })
                .addOnSuccessListener(aVoid -> {
                    // After cleaning up references, delete the user document
                    deleteUserDocument(uid, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed during profile deletion process", e);
                    // Still try to delete the user document
                    deleteUserDocument(uid, callback);
                });
    }

    private void deleteUserDocument(@NonNull String uid, @NonNull DeleteCallback callback) {
        DocumentReference userRef = db.collection("users").document(uid);
        userRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully deleted user document: " + uid);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Delete failed for user document: " + uid, e);
                    callback.onError(e);
                });
    }

    public void removeOrganizerRole(@NonNull String uid, @NonNull DeleteCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("UID is null or empty"));
            return;
        }

        DocumentReference userRef = db.collection("users").document(uid);
        userRef.update("roles", FieldValue.arrayRemove("organizer"))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Removed organizer role for user: " + uid);
                    this.deleteOrganizerEvents(uid);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to remove organizer role for user: " + uid, e);
                    callback.onError(e);
                });
    }

    /**
     * Approves a user's organizer application by adding the organizer role and
     * marking the application as APPROVED.
     */
    public void approveOrganizerApplication(@NonNull String uid, @NonNull DeleteCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("UID is null or empty"));
            return;
        }

        DocumentReference userRef = db.collection("users").document(uid);
        userRef.update(
                        "roles", FieldValue.arrayUnion("organizer"),
                        "organizerApplicationStatus", "APPROVED",
                        "organizerApplicationReviewedAt", System.currentTimeMillis()
                )
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Approved organizer application for user: " + uid);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to approve organizer application for user: " + uid, e);
                    callback.onError(e);
                });
    }

    /**
     * Declines a user's organizer application by marking it as DECLINED.
     */
    public void declineOrganizerApplication(@NonNull String uid, @NonNull DeleteCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("UID is null or empty"));
            return;
        }

        DocumentReference userRef = db.collection("users").document(uid);
        userRef.update(
                        "organizerApplicationStatus", "DECLINED",
                        "organizerApplicationReviewedAt", System.currentTimeMillis()
                )
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Declined organizer application for user: " + uid);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to decline organizer application for user: " + uid, e);
                    callback.onError(e);
                });
    }

    private Task<Void> deleteOrganizerEvents(@NonNull String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            Log.w(TAG, "deleteOrganizerEvents: UID is null or empty, skipping delete");
            return Tasks.forResult(null);
        }

        return db.collection("events")
                .whereEqualTo("organizerId", uid)
                .get()
                .continueWithTask(queryTask -> {
                    if (!queryTask.isSuccessful() || queryTask.getResult() == null) {
                        Log.e(TAG, "deleteOrganizerEvents: Failed to fetch events for organizer: " + uid, queryTask.getException());
                        return Tasks.forResult(null);
                    }

                    QuerySnapshot qs = queryTask.getResult();
                    if (qs.isEmpty()) {
                        Log.d(TAG, "deleteOrganizerEvents: No events found for organizer: " + uid);
                        return Tasks.forResult(null);
                    }

                    Log.d(TAG, "deleteOrganizerEvents: Found " + qs.size() + " events to delete for organizer: " + uid);
                    List<Task<Void>> deleteTasks = new ArrayList<>();
                    
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        final String eventId = d.getId();
                        deleteTasks.add(d.getReference().delete()
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "deleteOrganizerEvents: Deleted event " + eventId +
                                                " for organizer: " + uid))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "deleteOrganizerEvents: Failed to delete event " +
                                                eventId + " for organizer: " + uid, e))
                                .continueWith(task -> null));
                    }

                    if (deleteTasks.isEmpty()) {
                        return Tasks.forResult(null);
                    }

                    return Tasks.whenAll(deleteTasks)
                            .continueWith(allTasks -> {
                                if (allTasks.isSuccessful()) {
                                    Log.d(TAG, "deleteOrganizerEvents: Successfully deleted all " + qs.size() + " events for organizer: " + uid);
                                } else {
                                    Log.e(TAG, "deleteOrganizerEvents: Some events failed to delete for organizer: " + uid, allTasks.getException());
                                }
                                return null;
                            });
                });
    }

    private void removeUserFromEventLists(@NonNull String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            Log.w(TAG, "removeUserFromEventLists: UID is null or empty, skipping");
            return;
        }
        db.collection("events")
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    if (qs == null || qs.isEmpty()) {
                        Log.d(TAG, "removeUserFromEventLists: No events found when cleaning up uid=" + uid);
                        return;
                    }

                    String[] entrantCollections = new String[] {
                            "WaitlistedEntrants",
                            "SelectedEntrants",
                            "NonSelectedEntrants",
                            "CancelledEntrants",
                            "AdmittedEntrants"
                    };

                    for (DocumentSnapshot eventDoc : qs.getDocuments()) {
                        final String eventId = eventDoc.getId();
                        DocumentReference eventRef = eventDoc.getReference();

                        // Remove from any array fields on the event document (no-op if they don't exist)
                        eventRef.update(
                                        "waitlist", FieldValue.arrayRemove(uid),
                                        "admitted", FieldValue.arrayRemove(uid)
                                )
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "removeUserFromEventLists: Removed uid " + uid +
                                                " from arrays for event " + eventId))
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "removeUserFromEventLists: Failed to update arrays for event "
                                                + eventId + " and uid " + uid, e));

                        // Remove documents from entrant subcollections (if they exist)
                        for (String subPath : entrantCollections) {
                            eventRef.collection(subPath)
                                    .document(uid)
                                    .delete()
                                    .addOnSuccessListener(aVoid ->
                                            Log.d(TAG, "removeUserFromEventLists: Deleted " + subPath + "/" + uid +
                                                    " for event " + eventId))
                                    .addOnFailureListener(e ->
                                            Log.w(TAG, "removeUserFromEventLists: Failed to delete " + subPath +
                                                    "/" + uid + " for event " + eventId, e));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "removeUserFromEventLists: Failed to load events when cleaning up uid=" + uid, e));
    }

}