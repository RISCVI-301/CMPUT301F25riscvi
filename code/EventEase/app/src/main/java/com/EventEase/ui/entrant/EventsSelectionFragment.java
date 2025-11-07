package com.EventEase.ui.entrant;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.EventEase.R;

public class EventsSelectionFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_events_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set status bar color to match top bar
        if (getActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.ee_topbar_bg));
        }

        Button btnUpcoming = view.findViewById(R.id.btnUpcomingEvents);
        Button btnWaitlisted = view.findViewById(R.id.btnWaitlistedEvents);
        Button btnPrevious = view.findViewById(R.id.btnPreviousEvents);

        btnUpcoming.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_eventsSelection_to_upcomingEvents);
        });

        btnWaitlisted.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_eventsSelection_to_myEvents);
        });

        btnPrevious.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_eventsSelection_to_previousEvents);
        });
    }
}

