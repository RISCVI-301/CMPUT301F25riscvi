package com.example.eventease.admin.profile.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.eventease.R;
import com.example.eventease.admin.profile.data.UserProfile;
import com.example.eventease.auth.DeviceAuthManager;
import com.example.eventease.ui.entrant.profile.DialogBlurHelper;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private final Context context;

    public AdminProfileAdapter(@NonNull android.content.Context context,
                               @NonNull List<UserProfile> profiles,
                               @NonNull Consumer<UserProfile> onDeleteCallback) {
        this.inflater = LayoutInflater.from(context);
        this.context = context.getApplicationContext();
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

        // Organizer / application actions
        boolean isOrganizer = profile.getRoles() != null && profile.getRoles().contains("organizer");
        String appStatus = profile.getOrganizerApplicationStatus();

        // Reset visibility
        holder.btnRemoveOrganizer.setVisibility(View.GONE);
        holder.btnViewApplication.setVisibility(View.GONE);

        if (isOrganizer) {
            // Existing organizers: can remove organizer role
            holder.btnRemoveOrganizer.setVisibility(View.VISIBLE);
        } else if (appStatus != null && appStatus.equalsIgnoreCase("PENDING")) {
            // Entrants with a pending organizer application: show "View Application"
            holder.btnViewApplication.setVisibility(View.VISIBLE);
        }

        holder.btnRemoveOrganizer.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setMessage("Do you want to remove organizer role?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        if (holder.currentProfile == null) return;
                        String uid = holder.currentProfile.getUid();
                        dbController.removeOrganizerRole(uid,
                                new AdminProfileDatabaseController.DeleteCallback() {
                                    @Override
                                    public void onSuccess() {
                                        if (holder.currentProfile.getRoles() != null) {
                                            holder.currentProfile.getRoles().remove("organizer");
                                        }
                                        int pos = holder.getAdapterPosition();
                                        if (pos != RecyclerView.NO_POSITION) {
                                            notifyItemChanged(pos);
                                        }
                                        Toast.makeText(v.getContext(),
                                                "Organizer role removed", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onError(@NonNull Exception e) {
                                        Toast.makeText(v.getContext(),
                                                "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        holder.btnViewApplication.setOnClickListener(v -> {
            if (holder.currentProfile == null) return;
            showOrganizerApplicationDialog(v.getContext(), holder.currentProfile, holder);
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

    private void showOrganizerApplicationDialog(Context ctx, UserProfile profile, ProfileViewHolder holder) {
        if (ctx == null) return;
        Dialog dialog = new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (ctx instanceof android.app.Activity) {
            DialogBlurHelper.setupBlurredDialog(dialog, (android.app.Activity) ctx, R.layout.admin_dialog_view_organizer_application);
        } else {
            dialog.setContentView(R.layout.admin_dialog_view_organizer_application);
        }

        ImageView idImageView = dialog.findViewById(R.id.idImageView);
        android.widget.TextView applicantNameText = dialog.findViewById(R.id.applicantNameText);
        android.widget.TextView statusText = dialog.findViewById(R.id.applicationStatusText);
        androidx.appcompat.widget.AppCompatButton btnAccept = dialog.findViewById(R.id.btnAccept);
        androidx.appcompat.widget.AppCompatButton btnDecline = dialog.findViewById(R.id.btnDecline);
        androidx.appcompat.widget.AppCompatButton btnClose = dialog.findViewById(R.id.btnClose);

        if (applicantNameText != null) {
            String name = profile.getName() != null && !profile.getName().isEmpty()
                    ? profile.getName()
                    : (profile.getEmail() != null ? profile.getEmail() : "Applicant");
            applicantNameText.setText(name);
        }

        if (statusText != null) {
            String status = profile.getOrganizerApplicationStatus();
            if (status == null || status.isEmpty()) {
                statusText.setText("No organizer application on file.");
            } else {
                statusText.setText("Status: " + status);
            }
        }

        if (idImageView != null) {
            String url = profile.getOrganizerApplicationIdImageUrl();
            if (url != null && !url.isEmpty()) {
                Glide.with(idImageView.getContext())
                        .load(url)
                        .placeholder(R.drawable.entrant_icon)
                        .error(R.drawable.entrant_icon)
                        .into(idImageView);
            }
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                if (profile.getUid() == null || profile.getUid().isEmpty()) {
                    Toast.makeText(v.getContext(), "Missing user ID", Toast.LENGTH_SHORT).show();
                    return;
                }
                dbController.approveOrganizerApplication(profile.getUid(), new AdminProfileDatabaseController.DeleteCallback() {
                    @Override
                    public void onSuccess() {
                        // Update local model
                        if (profile.getRoles() != null && !profile.getRoles().contains("organizer")) {
                            profile.getRoles().add("organizer");
                        }
                        profile.setOrganizerApplicationStatus("APPROVED");
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            notifyItemChanged(pos);
                        }
                        Toast.makeText(v.getContext(), "Organizer role granted", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        Toast.makeText(v.getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> {
                if (profile.getUid() == null || profile.getUid().isEmpty()) {
                    Toast.makeText(v.getContext(), "Missing user ID", Toast.LENGTH_SHORT).show();
                    return;
                }

                dbController.declineOrganizerApplication(profile.getUid(), new AdminProfileDatabaseController.DeleteCallback() {
                    @Override
                    public void onSuccess() {
                        profile.setOrganizerApplicationStatus("DECLINED");
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            notifyItemChanged(pos);
                        }

                        // Send a notification to the user about the rejection
                        sendOrganizerApplicationDecisionNotification(profile.getUid(),
                                "Organizer application declined",
                                "Your request to become an organizer has been declined by an administrator.");

                        Toast.makeText(v.getContext(), "Application declined", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        Toast.makeText(v.getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        dialog.show();
        if (ctx instanceof android.app.Activity) {
            DialogBlurHelper.applyDialogAnimations(dialog, ctx);
        }
    }

    private void sendOrganizerApplicationDecisionNotification(@NonNull String userId,
                                                               @NonNull String title,
                                                               @NonNull String message) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> userIds = new ArrayList<>();
        userIds.add(userId);

        String adminId;
        try {
            DeviceAuthManager authManager = new DeviceAuthManager(context);
            adminId = authManager.getUid();
        } catch (Exception e) {
            adminId = "admin";
        }

        java.util.Map<String, Object> notificationRequest = new java.util.HashMap<>();
        notificationRequest.put("eventId", "organizer_role_application");
        notificationRequest.put("eventTitle", "Organizer Role Application");
        notificationRequest.put("organizerId", adminId);
        notificationRequest.put("userIds", userIds);
        notificationRequest.put("groupType", "organizerApplicationDecision");
        notificationRequest.put("message", message);
        notificationRequest.put("title", title);
        notificationRequest.put("status", "PENDING");
        notificationRequest.put("createdAt", System.currentTimeMillis());
        notificationRequest.put("processed", false);

        db.collection("notificationRequests").add(notificationRequest)
                .addOnSuccessListener(docRef -> {
                    android.util.Log.d("AdminProfileAdapter", "Organizer application decision notification created: " + docRef.getId());
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("AdminProfileAdapter", "Failed to create organizer application decision notification", e);
                });
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvEmail;
        TextView tvPhone;
        TextView tvRoles;
        Button btnDelete;
        Button btnRemoveOrganizer;
        Button btnViewApplication;
        UserProfile currentProfile; // Store the current profile to avoid stale position issues

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvRoles = itemView.findViewById(R.id.tvRoles);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnRemoveOrganizer = itemView.findViewById(R.id.btnRemoveOrganizer);
            btnViewApplication = itemView.findViewById(R.id.btnViewApplication);
        }
    }
}