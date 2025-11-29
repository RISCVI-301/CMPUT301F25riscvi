package com.example.eventease.admin.image.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.image.data.AdminImageDatabaseController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminImagesFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AdminImageDatabaseController AIDC = new AdminImageDatabaseController();
    private List<String> ImageData;
    private AdminImageControllingAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.admin_image_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageData = new ArrayList<>();

        RecyclerView rv = view.findViewById(R.id.grid);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        adapter = new AdminImageControllingAdapter(requireContext(), ImageData,
                (url, pos) -> deleteImage(url, pos));
        rv.setAdapter(adapter);

        io.execute(() -> {
            List<String> urls = AIDC.getImageLinks();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    ImageData.clear();
                    ImageData.addAll(urls);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void deleteImage(String url, int pos) {
        io.execute(() -> {
            boolean ok = AIDC.deleteImage(url);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
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
                        Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        io.shutdown();
    }
}

