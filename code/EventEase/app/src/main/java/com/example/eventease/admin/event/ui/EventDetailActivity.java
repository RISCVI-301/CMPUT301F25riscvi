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

import java.util.function.Consumer;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "com.example.eventease.extra.EVENT";
    public static final String EXTRA_WAITLIST_COUNT = "com.example.eventease.extra.WAITLIST_COUNT";

    private static Consumer<Event> onDeleteCallback; // receives the delete function

    /** Helper to start this screen and pass a delete callback. */
    public static void start(Context context, Event event, Consumer<Event> deleteCallback) {
        onDeleteCallback = deleteCallback;
        Intent i = new Intent(context, EventDetailActivity.class);
        i.putExtra(EXTRA_EVENT, event);
        i.putExtra(EXTRA_WAITLIST_COUNT, event.getWaitlist_count()); // keeping existing behavior
        context.startActivity(i);
    }

    /** Existing helper preserved (no callback). */
    public static void start(Context context, Event event, int waitlistCount) {
        Intent i = new Intent(context, EventDetailActivity.class);
        i.putExtra(EXTRA_EVENT, event);
        i.putExtra(EXTRA_WAITLIST_COUNT, waitlistCount);
        context.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        ImageButton btnBack      = findViewById(R.id.btnBack);
        TextView tvEventTitle    = findViewById(R.id.tvEventTitle);
        ImageView ivPoster       = findViewById(R.id.ivPoster);
        TextView tvDescription   = findViewById(R.id.tvDescription);
        TextView tvWaitlistCount = findViewById(R.id.tvWaitlistCount);
        TextView tvGuidelines    = findViewById(R.id.tvGuidelines);
        MaterialButton btnDelete = findViewById(R.id.btnDeleteEvent);

        // Check if views are found
        if (btnBack == null || tvEventTitle == null || ivPoster == null || 
            tvDescription == null || tvWaitlistCount == null || tvGuidelines == null || 
            btnDelete == null) {
            Toast.makeText(this, "Error loading event details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Event event = (Event) getIntent().getSerializableExtra(EXTRA_EVENT);
        int waitlistCount = getIntent().getIntExtra(EXTRA_WAITLIST_COUNT, 0);

        if (event == null) {
            Toast.makeText(this, "No event supplied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set event title
        if (event.getTitle() != null) {
            tvEventTitle.setText(event.getTitle());
        }

        // Load poster image
        String posterUrl = event.getPosterUrl();
        if (!TextUtils.isEmpty(posterUrl)) {
            Glide.with(this)
                    .load(posterUrl)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivPoster);
        }

        // Set description
        tvDescription.setText(
                TextUtils.isEmpty(event.getDescription())
                        ? "No description provided."
                        : event.getDescription()
        );

        // Set waitlist count
        tvWaitlistCount.setText(String.valueOf(waitlistCount));

        // Set guidelines
        String guidelines = event.getGuidelines();
        tvGuidelines.setText(TextUtils.isEmpty(guidelines)
                ? "No guidelines provided."
                : guidelines);

        // Setup back button
        btnBack.setOnClickListener(v -> {
            try {
                onBackPressed();
            } catch (Exception e) {
                finish();
            }
        });

        // Setup delete button
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete event?")
                    .setMessage("This action cannot be undone.")
                    .setPositiveButton("Delete", (d, which) -> {
                        if (onDeleteCallback != null) {
                            onDeleteCallback.accept(event);
                        }
                        Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}