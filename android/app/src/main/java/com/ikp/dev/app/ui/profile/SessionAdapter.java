package com.ikp.dev.app.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/** Active session rows with revoke action (current session not revocable). */
final class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.Holder> {

    interface OnRevoke {
        void onRevoke(Models.SessionInfo session);
    }

    private final List<Models.SessionInfo> items = new ArrayList<>();
    private final OnRevoke listener;

    SessionAdapter(OnRevoke listener) {
        this.listener = listener;
    }

    void submit(List<Models.SessionInfo> sessions) {
        items.clear();
        if (sessions != null) {
            items.addAll(sessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Models.SessionInfo s = items.get(position);
        holder.device.setText(s.deviceName == null || s.deviceName.isEmpty()
                ? holder.device.getContext().getString(R.string.sessions_title) : s.deviceName);
        holder.current.setVisibility(s.current ? View.VISIBLE : View.GONE);
        holder.meta.setText(buildMeta(s));
        holder.revoke.setVisibility(s.current ? View.GONE : View.VISIBLE);
        holder.revoke.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRevoke(s);
            }
        });
    }

    private static String buildMeta(Models.SessionInfo s) {
        StringBuilder sb = new StringBuilder();
        if (s.createdAt != null && !s.createdAt.isEmpty()) {
            sb.append(s.createdAt.length() > 10 ? s.createdAt.substring(0, 10) : s.createdAt);
        }
        if (s.expiresAt != null && !s.expiresAt.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(s.expiresAt.length() > 10 ? s.expiresAt.substring(0, 10) : s.expiresAt);
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView device;
        final TextView current;
        final TextView meta;
        final Button revoke;

        Holder(@NonNull View itemView) {
            super(itemView);
            device = itemView.findViewById(R.id.tv_device);
            current = itemView.findViewById(R.id.tv_current);
            meta = itemView.findViewById(R.id.tv_meta);
            revoke = itemView.findViewById(R.id.btn_revoke);
        }
    }
}
