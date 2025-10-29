package com.example.eventease;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mainfragments);

        // Don't draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // The <include> replaces the child's id, so the real id is include_bottom
        BottomNavigationView bottom = findViewById(R.id.include_bottom);
        if (bottom == null) {
            throw new IllegalStateException("include_bottom not found. Check activity_mainfragments.xml include tag.");
        }

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);
        if (host == null) {
            throw new IllegalStateException("nav_host_main not found or not a NavHostFragment.");
        }

        NavController nav = host.getNavController();
        NavigationUI.setupWithNavController(bottom, nav);
    }
}
