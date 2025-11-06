package com.example.eventease.admin.image.data;

import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminImageDatabaseController {

    private final StorageReference postersRef =
            FirebaseStorage.getInstance().getReference().child("posters/");

    /** 1) Get all download URLs in posters/ */
    public List<String> getImageLinks() {
        try {
            ListResult res = Tasks.await(postersRef.listAll());
            List<Task<Uri>> urlTasks = new ArrayList<>();
            for (StorageReference item : res.getItems()) urlTasks.add(item.getDownloadUrl());
            List<?> uris = Tasks.await(Tasks.whenAllSuccess(urlTasks));
            List<String> urls = new ArrayList<>(uris.size());
            for (Object o : uris) urls.add(((Uri) o).toString());
            return urls;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public boolean deleteImage(String urlOrPath) {
        try {
            StorageReference ref;
            if (urlOrPath.startsWith("http") || urlOrPath.startsWith("gs://")) {
                ref = FirebaseStorage.getInstance().getReferenceFromUrl(urlOrPath);
            } else {
                ref = postersRef.child(urlOrPath); // e.g., "posters/myPoster.jpg" or just "myPoster.jpg"
            }
            Tasks.await(ref.delete());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
