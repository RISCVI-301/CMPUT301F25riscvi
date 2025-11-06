package com.EventEase;

import android.app.Application;
import com.EventEase.data.firebase.FirebaseDevGraph;

public class App extends Application {
    private static FirebaseDevGraph GRAPH;
    @Override public void onCreate() {
        super.onCreate();
        GRAPH = new FirebaseDevGraph();
    }
    public static FirebaseDevGraph graph() { return GRAPH; }
}


