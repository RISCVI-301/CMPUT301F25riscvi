package com.EventEase.ui.entrant.discover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;

import java.util.Arrays;
import java.util.List;

public class DiscoverFragment extends Fragment {

    public DiscoverFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvDiscover);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);

        // TEMP data to prove it renders
        List<DiscoverAdapter.EventUi> items = Arrays.asList(
                new DiscoverAdapter.EventUi("Event 1"),
                new DiscoverAdapter.EventUi("Event 2"),
                new DiscoverAdapter.EventUi("Event 3")
        );
        rv.setAdapter(new DiscoverAdapter(items, v -> { /* handle click */ }));
    }

}
