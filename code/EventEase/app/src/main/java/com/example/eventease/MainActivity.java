package com.example.eventease;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //FireBase
        FirebaseApp.initializeApp(this);
        Log.d("FirebaseTest", "FirebaseApp initialized: " + (FirebaseApp.getApps(this).size() > 0));


        // Hand off to AdminImageManagementActivity
        Intent intent = new Intent(this, AdminImageManagementActivity.class);
        startActivity(intent);
        finish(); // Finish MainActivity so 'Back' does not come back here.
    }
}
