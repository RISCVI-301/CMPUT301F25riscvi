package com.example.eventease.admin.profile.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.eventease.ui.entrant.profile.ProfileDeletionHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.List;

public class AdminProfileDatabaseController {

    private static final String TAG = "AdminProfileDB";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface ProfilesCallback {
        void onLoaded(@NonNull List<UserProfile> profiles);
        void onError(@NonNull Exception e);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(@NonNull Exception e);
    }

    public void fetchProfiles(@NonNull final ProfilesCallback cb) {
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "fetchProfiles: User is not authenticated");
            cb.onError(new IllegalStateException("Not authenticated"));
            return;
        }

        // Use UserRoleChecker to verify admin role (more reliable)
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

                        list.add(new UserProfile(uid, email, name, phoneNumber, roles, createdAt != null ? createdAt : 0L));
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
        
        // Use ProfileDeletionHelper to delete all user references first
        ProfileDeletionHelper deletionHelper = new ProfileDeletionHelper(context);
        deletionHelper.deleteAllUserReferences(uid, new ProfileDeletionHelper.DeletionCallback() {
            @Override
            public void onDeletionComplete() {
                // After cleaning up references, delete the user document
                deleteUserDocument(uid, callback);
            }

            @Override
            public void onDeletionFailure(String error) {
                Log.w(TAG, "Failed to delete some user references: " + error + ", proceeding with document deletion");
                // Still try to delete the user document even if some references failed
                deleteUserDocument(uid, callback);
            }
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

    private void deleteOrganizerEvents(@NonNull String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            Log.w(TAG, "deleteOrganizerEvents: UID is null or empty, skipping delete");
            return;
        }

        db.collection("events")
                .whereEqualTo("organizerId", uid)
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    if (qs == null || qs.isEmpty()) {
                        Log.d(TAG, "deleteOrganizerEvents: No events found for organizer: " + uid);
                        return;
                    }

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        final String eventId = d.getId();
                        d.getReference()
                                .delete()
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "deleteOrganizerEvents: Deleted event " + eventId +
                                                " for organizer: " + uid))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "deleteOrganizerEvents: Failed to delete event " +
                                                eventId + " for organizer: " + uid, e));
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "deleteOrganizerEvents: Failed to fetch events for organizer: "
                                + uid, e));


    }


}

