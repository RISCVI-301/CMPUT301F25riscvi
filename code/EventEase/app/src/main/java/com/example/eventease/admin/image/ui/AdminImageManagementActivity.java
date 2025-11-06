package com.example.eventease.admin.image.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.image.data.AdminImageDatabaseController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminImageManagementActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AdminImageDatabaseController AIDC = new AdminImageDatabaseController();
    private List<String> ImageData;
    private AdminImageControllingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_image_management);

        ImageData = new ArrayList<>();

        RecyclerView rv = findViewById(R.id.grid);
        rv.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new AdminImageControllingAdapter(this, ImageData,
                (url, pos) -> deleteImage(url, pos));
        rv.setAdapter(adapter);

        // Load images off the main thread (controller is blocking)
        io.execute(() -> {
            List<String> urls = AIDC.getImageLinks();
            runOnUiThread(() -> {
                ImageData.clear();
                ImageData.addAll(urls);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void deleteImage(String url, int pos) {
        io.execute(() -> {
            boolean ok = AIDC.deleteImage(url);
            runOnUiThread(() -> {
                if (ok) {
                    if (pos >= 0 && pos < ImageData.size() && ImageData.get(pos).equals(url)) {
                        ImageData.remove(pos);
                        adapter.notifyItemRemoved(pos);
                    } else {
                        int idx = ImageData.indexOf(url);
                        if (idx != -1) { ImageData.remove(idx); adapter.notifyItemRemoved(idx); }
                        else { adapter.notifyDataSetChanged(); }
                    }
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
