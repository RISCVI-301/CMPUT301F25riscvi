package com.example.eventease.auth;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

/**
 * Utility class for checking user roles from Firestore.
 * Provides static methods to query and validate user roles stored in the Firestore database.
 * Supports both array-based roles (stored in "roles" field) and single role (stored in "role" field) for legacy compatibility.
 */
public class UserRoleChecker {
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final FirebaseAuth auth = FirebaseAuth.getInstance();

    /**
     * Checks if the currently authenticated user has the specified role.
     * Queries the Firestore users collection to check both the "roles" array and "role" field.
     *
     * @param role the role to check (e.g., "admin", "entrant", "organizer"), case-insensitive
     * @return a Task that completes with true if the user has the role, false otherwise.
     *         Returns false if no user is signed in or if an error occurs.
     */
    @NonNull
    public static Task<Boolean> hasRole(@NonNull String role) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return Tasks.forResult(false);
        }

        return db.collection("users").document(user.getUid()).get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return false;
                    }

                    DocumentSnapshot doc = task.getResult();
                    return hasRole(doc, role);
                });
    }

    /**
     * Checks if a Firestore document has the specified role.
     * Examines both the "roles" array field and the "role" string field for compatibility.
     * Role comparison is case-insensitive.
     *
     * @param documentSnapshot the Firestore document to check
     * @param targetRole the role to check for, case-insensitive
     * @return true if the document contains the role, false otherwise
     */
    public static boolean hasRole(@NonNull DocumentSnapshot documentSnapshot, @NonNull String targetRole) {
        // Check roles array field
        Object rolesObj = documentSnapshot.get("roles");
        if (rolesObj instanceof List<?>) {
            for (Object role : (List<?>) rolesObj) {
                if (role != null && targetRole.equalsIgnoreCase(role.toString())) {
                    return true;
                }
            }
        }

        // Check single role field (legacy support)
        String roleField = documentSnapshot.getString("role");
        if (roleField != null && roleField.toLowerCase(Locale.US).contains(targetRole.toLowerCase(Locale.US))) {
            return true;
        }

        return false;
    }

    /**
     * Gets all roles for the currently authenticated user as a list.
     * Returns roles from either the "roles" array field or the "role" string field.
     *
     * @return a Task that completes with a list of roles for the user.
     *         Returns an empty list if no user is signed in, the user has no roles, or an error occurs.
     */
    @NonNull
    public static Task<List<String>> getUserRoles() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return Tasks.forResult(java.util.Collections.emptyList());
        }

        return db.collection("users").document(user.getUid()).get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return java.util.Collections.<String>emptyList();
                    }

                    DocumentSnapshot doc = task.getResult();
                    Object rolesObj = doc.get("roles");
                    if (rolesObj instanceof List<?>) {
                        java.util.List<String> roles = new java.util.ArrayList<>();
                        for (Object role : (List<?>) rolesObj) {
                            if (role != null) {
                                roles.add(role.toString());
                            }
                        }
                        return roles;
                    }

                    String roleField = doc.getString("role");
                    if (roleField != null && !roleField.trim().isEmpty()) {
                        return java.util.Arrays.asList(roleField);
                    }

                    return java.util.Collections.<String>emptyList();
                });
    }

    /**
     * Checks if the currently authenticated user is an admin.
     * This is a convenience method that calls hasRole("admin").
     *
     * @return a Task that completes with true if the user has the "admin" role, false otherwise
     */
    @NonNull
    public static Task<Boolean> isAdmin() {
        return hasRole("admin");
    }
}

