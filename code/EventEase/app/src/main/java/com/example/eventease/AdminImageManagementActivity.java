package com.example.eventease;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminImageManagementActivity extends AppCompatActivity {

    private AdminImageDatabaseController AIDC = new AdminImageDatabaseController();
    List<String> demoData;
    private AdminImageControllingAdapter adapter;

    private void deleteImage(String url){
        int pos = demoData.indexOf(url);       // or pass position directly
        if (pos != -1) {
            demoData.remove(pos);
            adapter.notifyItemRemoved(pos);
            AIDC.deleteImage(url);
        }
        //Remaining to be Populated when DB logic Confirm
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_image_management);

        // Images:
        demoData = new ArrayList<>(Arrays.asList(
                "https://picsum.photos/id/4/800/800",
                "https://picsum.photos/id/10/800/800",
                "https://picsum.photos/id/22/800/800",
                "https://picsum.photos/id/22/800/800",
                "https://picsum.photos/id/4/800/800"
        ));

        RecyclerView rv = findViewById(R.id.grid); //find the Recycler View Component
        rv.setLayoutManager(new GridLayoutManager(this, 2)); // Assign GridLayout Manager to Recycler View. It shows 2 tiles in each row.

        adapter = new AdminImageControllingAdapter(this, demoData, (url, pos) -> deleteImage(url));
        rv.setAdapter(adapter); //Assigning Controller to Recycler View.
    }
}