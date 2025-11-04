package com.EventEase.ui.entrant;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;

public class UpcomingEventsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_upcoming_events, container, false);

        // Set status bar color to match top bar
        if (getActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.ee_topbar_bg));
        }

        // Set up back button
        View btnBack = root.findViewById(R.id.btnBackUpcoming);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(v).navigateUp();
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                }
            });
        }

        // Set up RecyclerView (empty for now)
        RecyclerView recyclerView = root.findViewById(R.id.upcoming_events_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        return root;
    }
}

