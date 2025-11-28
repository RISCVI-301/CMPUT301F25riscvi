package com.example.eventease.admin.profile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.profile.data.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

import com.example.eventease.admin.profile.data.AdminProfileDatabaseController;

public class AdminProfileAdapter extends RecyclerView.Adapter<AdminProfileAdapter.ProfileViewHolder> {

    private final LayoutInflater inflater;
    private final List<UserProfile> items = new ArrayList<>();
    @NonNull private final Consumer<UserProfile> onDeleteCallback;

    private final AdminProfileDatabaseController dbController = new AdminProfileDatabaseController();

    public AdminProfileAdapter(@NonNull android.content.Context context,
                               @NonNull List<UserProfile> profiles,
                               @NonNull Consumer<UserProfile> onDeleteCallback) {
        this.inflater = LayoutInflater.from(context);
        if (profiles != null) this.items.addAll(profiles);
        this.onDeleteCallback = onDeleteCallback;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        UserProfile p = items.get(position);
        String uid = p.getUid();
        return (uid != null) ? uid.hashCode() : position;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_admin_profile, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        UserProfile profile = items.get(position);
        if (profile == null) return;

        // Store the profile in the holder to avoid stale position issues
        holder.currentProfile = profile;

        holder.tvName.setText(profile.getName() != null && !profile.getName().isEmpty() ? profile.getName() : "No name");
        holder.tvEmail.setText(profile.getEmail() != null && !profile.getEmail().isEmpty() ? profile.getEmail() : "No email");
        holder.tvPhone.setText(profile.getPhoneNumber() != null && !profile.getPhoneNumber().isEmpty() ? profile.getPhoneNumber() : "No phone");

        // Display roles
        if (profile.getRoles() != null && !profile.getRoles().isEmpty()) {
            holder.tvRoles.setText("Roles: " + String.join(", ", profile.getRoles()));
        } else {
            holder.tvRoles.setText("Roles: No roles");
        }

        // Show / hide Remove Organizer button based on roles
        if (profile.getRoles() != null && profile.getRoles().contains("organizer")) {
            holder.btnRemoveOrganizer.setVisibility(View.VISIBLE);
        } else {
            holder.btnRemoveOrganizer.setVisibility(View.GONE);
        }

        holder.btnRemoveOrganizer.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setMessage("Do you want to remove organizer role?")
                    .setPositiveButton("Yes", (dialog, which) ->
                            Toast.makeText(v.getContext(),
                                    "Remove organizer clicked", Toast.LENGTH_SHORT).show())
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });


        // Set delete button click listener - use stored profile to avoid stale data
        holder.btnDelete.setOnClickListener(null);
        holder.btnDelete.setOnClickListener(v -> {
            if (holder.currentProfile != null) {
                onDeleteCallback.accept(holder.currentProfile);
            }
        });
    }

    public void submitList(@NonNull List<UserProfile> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvEmail;
        TextView tvPhone;
        TextView tvRoles;
        Button btnDelete;
        Button btnRemoveOrganizer;
        UserProfile currentProfile; // Store the current profile to avoid stale position issues

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvRoles = itemView.findViewById(R.id.tvRoles);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnRemoveOrganizer = itemView.findViewById(R.id.btnRemoveOrganizer);
        }
    }
}