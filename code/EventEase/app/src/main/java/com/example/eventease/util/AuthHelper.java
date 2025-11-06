package com.example.eventease.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthHelper {
    private static final String ORGANIZER_ID = "organizer_test_1";
    
    public static String getCurrentOrganizerId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("User must be authenticated to get organizer ID");
        }
        return ORGANIZER_ID;
    }
    
    public static String getCurrentOrganizerIdOrNull() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return null;
        }
        return ORGANIZER_ID;
    }
    
    public static boolean isAuthenticated() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }
}

