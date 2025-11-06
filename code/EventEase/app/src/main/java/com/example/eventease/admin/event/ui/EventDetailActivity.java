// File: EventDetailActivity.java
package com.example.eventease.admin.event.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.eventease.R;
import com.example.eventease.admin.event.data.Event;
import com.google.android.material.button.MaterialButton;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "com.example.eventease.extra.EVENT";
    public static final String EXTRA_WAITLIST_COUNT = "com.example.eventease.extra.WAITLIST_COUNT";

    /** Helper to start this screen from anywhere. */
    public static void start(Context context, Event event, int waitlistCount) {
        Intent i = new Intent(context, EventDetailActivity.class);
        i.putExtra(EXTRA_EVENT, event);              // Event passed via Serializable (see note below)
        i.putExtra(EXTRA_WAITLIST_COUNT, waitlistCount);
        context.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        // Views
        ImageButton btnBack      = findViewById(R.id.btnBack);
        TextView tvEventTitle    = findViewById(R.id.tvEventTitle);
        ImageView ivPoster       = findViewById(R.id.ivPoster);
        TextView tvDescription   = findViewById(R.id.tvDescription);
        TextView tvWaitlistCount = findViewById(R.id.tvWaitlistCount);
        MaterialButton btnDelete = findViewById(R.id.btnDeleteEvent);

        // Get data
        Event event = (Event) getIntent().getSerializableExtra(EXTRA_EVENT);
        int waitlistCount = getIntent().getIntExtra(EXTRA_WAITLIST_COUNT, 0);

        if (event == null) {
            Toast.makeText(this, "No event supplied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind
        tvEventTitle.setText(event.getTitle());

        String posterUrl = event.getPosterUrl();
        if (!TextUtils.isEmpty(posterUrl)) {
            Glide.with(this)
                    .load(posterUrl)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivPoster);
        } // else keep placeholder from tools/drawable

        tvDescription.setText(
                TextUtils.isEmpty(event.getDescription())
                        ? "No description provided."
                        : event.getDescription()
        );

        tvWaitlistCount.setText(String.valueOf(waitlistCount));

        // Interactions
        btnBack.setOnClickListener(v -> onBackPressed());

        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete event?")
                    .setMessage("This action cannot be undone.")
                    .setPositiveButton("Delete", (d, which) -> {
                        // TODO: delete from your data source.
                        // Return which event was deleted to the caller (optional):
                        Intent result = new Intent().putExtra("deleted_event_id", event.getId());
                        setResult(RESULT_OK, result);
                        Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}