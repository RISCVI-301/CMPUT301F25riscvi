package com.example.eventease.admin.image.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.admin.image.data.AdminImageDatabaseController;
import com.example.eventease.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminImageManagementActivity extends AppCompatActivity {

    private AdminImageDatabaseController AIDC = new AdminImageDatabaseController();
    List<String> ImageData;
    private AdminImageControllingAdapter adapter;

    // UI->Controller deleting interface. Calls Controller for deleting, while updates it's own View List.
    private void deleteImage(String url){
        int pos = ImageData.indexOf(url);
        if (pos != -1) {
            ImageData.remove(pos);
            adapter.notifyItemRemoved(pos);
            AIDC.deleteImage(url);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_image_management);

        // Get Images by asking Controller:
        ImageData = AIDC.getImageLinks();

        RecyclerView rv = findViewById(R.id.grid); //find the Recycler View Component
        rv.setLayoutManager(new GridLayoutManager(this, 2)); // Assign GridLayout Manager to Recycler View. It shows 2 tiles in each row.

        adapter = new AdminImageControllingAdapter(this, ImageData, (url, pos) -> deleteImage(url));
        rv.setAdapter(adapter); //Assigning Controller to Recycler View.
    }
}