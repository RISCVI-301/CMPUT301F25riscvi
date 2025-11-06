package com.example.eventease;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.admin.event.ui.AdminEventManagementActivity;
import com.example.eventease.admin.image.ui.AdminImageManagementActivity;
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

//        // Hand off to AdminEventManagement Activity
//        Intent intent = new Intent(this, AdminEventManagementActivity.class);
//        startActivity(intent);
//        finish();
    }
}
