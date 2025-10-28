package com.example.eventease;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import com.example.eventease.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mainfragments);

        // Do NOT draw behind status/navigation bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        BottomNavigationView bottom = findViewById(R.id.bottom_nav);
        NavController nav = Navigation.findNavController(this, R.id.nav_host_main);
        NavigationUI.setupWithNavController(bottom, nav);
    }
}
