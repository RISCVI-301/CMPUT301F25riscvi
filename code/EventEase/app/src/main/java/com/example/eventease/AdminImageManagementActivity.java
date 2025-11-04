package com.example.eventease;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;

import java.util.Arrays;
import java.util.List;

public class AdminImageManagementActivity extends AppCompatActivity {

    private AdminImageDatabaseController AIDC = new AdminImageDatabaseController();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_image_management);

        // Images:
        List<String> demoData = Arrays.asList( // Prototype data:
                "https://picsum.photos/id/4/800/800",
                "https://picsum.photos/id/10/800/800",
                "https://picsum.photos/id/22/800/800",
                "https://picsum.photos/id/22/800/800",
                "https://picsum.photos/id/4/800/800"
        );

        RecyclerView rv = findViewById(R.id.grid); //find the Recycler View Component
        rv.setLayoutManager(new GridLayoutManager(this, 2)); // Assign GridLayout Manager to Recycler View. It shows 2 tiles in each row.
        rv.setAdapter(new AdminImageControllingAdapter(this, demoData)); //Assigning Controller to Recycler View.
    }
}