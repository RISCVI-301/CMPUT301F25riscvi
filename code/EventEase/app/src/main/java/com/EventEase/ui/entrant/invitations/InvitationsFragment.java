package com.EventEase.ui.entrant.invitations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.EventEase.auth.AuthManager;
import com.EventEase.data.InvitationListener;
import com.EventEase.data.InvitationRepository;
import com.EventEase.data.ListenerRegistration;
import com.EventEase.data.firebase.FirebaseDevGraph;
import com.EventEase.model.Invitation;
import com.example.eventease.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvitationsFragment extends Fragment {

    private static final FirebaseDevGraph GRAPH = new FirebaseDevGraph();
    private InvitationRepository repo;
    private AuthManager auth;

    private RecyclerView list;
    private TextView emptyView;

    private ListenerRegistration registration;
    private final InvitationAdapter adapter = new InvitationAdapter();

    public InvitationsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_invitations, container, false);
        list = root.findViewById(R.id.invitations_list);
        emptyView = root.findViewById(R.id.invitations_empty);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        repo = GRAPH.invitations;
        auth = GRAPH.auth;

        registration = repo.listenActive(auth.getUid(), new InvitationListener() {
            @Override
            public void onChanged(List<Invitation> activeInvitations) {
                adapter.submit(activeInvitations);
                emptyView.setVisibility(activeInvitations == null || activeInvitations.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    // Simple placeholder adapter
    private static class InvitationAdapter extends RecyclerView.Adapter<InvitationVH> {
        private final List<Invitation> data = new ArrayList<>();
        void submit(List<Invitation> items) {
            data.clear();
            if (items != null) data.addAll(items);
            notifyDataSetChanged();
        }
        @NonNull @Override public InvitationVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_invitation_row, parent, false);
            return new InvitationVH(v);
        }
        @Override public void onBindViewHolder(@NonNull InvitationVH h, int pos) { h.bind(data.get(pos)); }
        @Override public int getItemCount() { return data.size(); }
    }

    private static class InvitationVH extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mma", Locale.getDefault());

        InvitationVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.inv_row_title);
            subtitle = itemView.findViewById(R.id.inv_row_subtitle);
        }

        void bind(Invitation inv) {
            // Minimal placeholders (we only have eventId in the invitation; event lookup can come later)
            title.setText("Invitation • " + inv.getEventId());
            String expires = inv.getExpiresAt() != null ? ("Expires " + df.format(inv.getExpiresAt())) : "No expiry";
            subtitle.setText(inv.getStatus() + " • " + expires);
        }
    }
}
