package com.EventEase;

import android.app.Application;
import com.EventEase.data.firebase.FirebaseDevGraph;

/**
 * Main application class that initializes the dependency graph.
 * Provides access to shared repositories and services throughout the app.
 */
public class App extends Application {
    private static FirebaseDevGraph GRAPH;
    @Override public void onCreate() {
        super.onCreate();
        GRAPH = new FirebaseDevGraph();
    }
    public static FirebaseDevGraph graph() { return GRAPH; }
}


