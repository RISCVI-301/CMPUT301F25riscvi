package com.example.eventease.admin.image.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdminImageDatabaseController {

    public List<String> getImageLinks() {

        //Return Demo Data
        List<String> urls = new ArrayList<>(Arrays.asList(
                "https://picsum.photos/id/4/800/800",
                "https://picsum.photos/id/10/800/800",
                "https://picsum.photos/id/22/800/800",
                "https://picsum.photos/id/22/800/800",
                "https://picsum.photos/id/4/800/800"
        ));
        return urls;
    }

    public boolean deleteImage(String url) {
        return true;
    }
}
