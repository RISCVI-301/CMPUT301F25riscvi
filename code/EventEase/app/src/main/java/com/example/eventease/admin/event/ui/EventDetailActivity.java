package com.example.eventease.admin.event.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.eventease.R;
import com.example.eventease.admin.event.data.Event;
import com.google.android.material.button.MaterialButton;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "extra_event";              // Parcelable Event
    public static final String EXTRA_WAITLIST_COUNT = "extra_waitlist";  // int (optional)

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

        // Read extras
        Event event = getIntent().getParcelableExtra(EXTRA_EVENT);
        int waitlistCount = getIntent().getIntExtra(EXTRA_WAITLIST_COUNT, 0);

        if (event == null) {
            finish(); // No data—bail out
            return;
        }

        // Bind data
        tvEventTitle.setText(event.getTitle());
        tvDescription.setText(event.getDescription());
        tvWaitlistCount.setText(String.valueOf(waitlistCount));

        String url = event.getPosterUrl();
        if (TextUtils.isEmpty(url)) {
            ivPoster.setImageDrawable(new ColorDrawable(Color.parseColor("#546E7A")));
        } else {
            Glide.with(this)
                    .load(url)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivPoster);
        }

        // Back
        btnBack.setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed()
        );

        // Delete handler (confirm then return result)
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Event")
                    .setMessage("Are you sure you want to delete \"" + event.getTitle() + "\"?")
                    .setPositiveButton("Delete", (DialogInterface dialog, int which) -> {
                        Intent result = new Intent()
                                .putExtra("deleted_event_id", event.getId());
                        setResult(RESULT_OK, result);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}