package com.example.eventease;

import android.app.Application;
import com.example.eventease.data.firebase.FirebaseDevGraph;
import com.example.eventease.notifications.NotificationChannelManager;

/**
 * Main application class that initializes the dependency graph.
 * Provides access to shared repositories and services throughout the app.
 * This class follows the Application singleton pattern and initializes the Firebase dependency graph
 * which contains all repository instances used throughout the application.
 */
public class App extends Application {
    private static FirebaseDevGraph GRAPH;

    /**
     * Initializes the application and creates the dependency graph.
     * Called when the application process is created.
     */
    @Override public void onCreate() {
        super.onCreate();
        GRAPH = new FirebaseDevGraph();
        
        // Initialize UserRoleChecker with application context
        com.example.eventease.auth.UserRoleChecker.initialize(this);
        
        // Create notification channel early so it's always available
        // This ensures notifications work properly from the start
        NotificationChannelManager.createNotificationChannel(this);
    }

    /**
     * Gets the shared dependency graph instance.
     * The dependency graph provides access to all repositories and services.
     *
     * @return the FirebaseDevGraph instance containing all repositories
     */
    public static FirebaseDevGraph graph() { return GRAPH; }
}


