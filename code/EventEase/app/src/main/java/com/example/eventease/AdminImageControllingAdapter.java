package com.example.eventease;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class AdminImageControllingAdapter extends RecyclerView.Adapter<AdminImageControllingAdapter.VH> {
    private final List<String> urls; // List which hold's the URL
    private final int itemW, itemH, margin; // Holding Tile width, height, and margin.

    public interface OnDelete {
        void onDelete(String url, int pos); // void is fine
    }

    private final OnDelete onDelete;

    // Constructor
    public AdminImageControllingAdapter(Context ctx, List<String> urls, OnDelete onDelete) {

        // Assign URL
        this.urls = urls;
        this.onDelete = onDelete;
        // Find Screen Width and Height
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        // Assign Tile Margin, width, Height
        margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, dm);
        itemW = (int) (screenW * 0.46f) - margin * 2;
        itemH = (int) (screenH * 0.25f) - margin * 2; // keep same height
    }

    //
    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        View delete;
        VH(View v) {
            super(v);
            image = v.findViewById(R.id.image);
            delete = v.findViewById(R.id.btnDelete);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup p, int vt) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_admin_image, p, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(itemW, itemH);
        lp.setMargins(margin, margin, margin, margin);
        v.setLayoutParams(lp);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH h, int i) {

        // Attach Image to Data:

        String url = urls.get(i);
        Glide.with(h.image.getContext())
                .load(url)
                .centerCrop()
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.holo_red_dark)
                .into(h.image);

        h.delete.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                    .setTitle("Delete image?")
                    .setMessage("Delete image Number " + (pos + 1) + "?")
                    .setPositiveButton("Yes", (d, w) -> {
                        int p = h.getBindingAdapterPosition(); // re-check
                        if (p != RecyclerView.NO_POSITION) {
                            onDelete.onDelete(urls.get(p), p);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override public int getItemCount() { return urls.size(); }
}
