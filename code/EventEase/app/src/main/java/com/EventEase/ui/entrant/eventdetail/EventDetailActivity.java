package com.EventEase.ui.entrant.eventdetail;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eventease.R;

/**
 * Entrant — Event detail screen (placeholder Activity).
 * Keep minimal; navigation wiring happens later.
 */
public class EventDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details_discover);
    }
}
