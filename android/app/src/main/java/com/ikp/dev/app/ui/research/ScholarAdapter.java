package com.ikp.dev.app.ui.research;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.util.ArrayList;
import java.util.List;

public final class ScholarAdapter extends RecyclerView.Adapter<ScholarAdapter.ScholarViewHolder> {

    public interface OnScholarClickListener {
        void onScholarClick(AcademyDtos.ScholarDto scholar);
    }

    private final List<AcademyDtos.ScholarDto> items = new ArrayList<>();
    private final OnScholarClickListener clickListener;

    public ScholarAdapter(OnScholarClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submitList(List<AcademyDtos.ScholarDto> scholars) {
        this.items.clear();
        if (scholars != null) {
            this.items.addAll(scholars);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScholarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scholar_card, parent, false);
        return new ScholarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScholarViewHolder holder, int position) {
        AcademyDtos.ScholarDto scholar = items.get(position);
        holder.bind(scholar, clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ScholarViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvInitials;
        private final TextView tvName;
        private final TextView tvNameAr;
        private final View boxVerified;
        private final TextView tvQualifications;
        private final TextView tvExpertise;
        private final TextView tvStats;

        ScholarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitials = itemView.findViewById(R.id.tv_scholar_initials);
            tvName = itemView.findViewById(R.id.tv_scholar_name);
            tvNameAr = itemView.findViewById(R.id.tv_scholar_name_ar);
            boxVerified = itemView.findViewById(R.id.box_scholar_verified);
            tvQualifications = itemView.findViewById(R.id.tv_scholar_qualifications);
            tvExpertise = itemView.findViewById(R.id.tv_scholar_expertise);
            tvStats = itemView.findViewById(R.id.tv_scholar_stats);
        }

        void bind(AcademyDtos.ScholarDto s, OnScholarClickListener listener) {
            // Compute initials
            String initials = getInitials(s.name);
            tvInitials.setText(initials);

            tvName.setText(s.name == null ? "" : s.name);

            if (s.nameAr != null && !s.nameAr.trim().isEmpty()) {
                tvNameAr.setVisibility(View.VISIBLE);
                tvNameAr.setText(s.nameAr);
            } else {
                tvNameAr.setVisibility(View.GONE);
            }

            if (s.verificationStatus != null && !s.verificationStatus.isEmpty()) {
                boxVerified.setVisibility(View.VISIBLE);
            } else {
                boxVerified.setVisibility(View.GONE);
            }

            if (s.qualifications != null && !s.qualifications.trim().isEmpty()) {
                tvQualifications.setVisibility(View.VISIBLE);
                tvQualifications.setText(s.qualifications);
            } else {
                tvQualifications.setVisibility(View.GONE);
            }

            if (s.areasOfExpertise != null && !s.areasOfExpertise.trim().isEmpty()) {
                tvExpertise.setVisibility(View.VISIBLE);
                tvExpertise.setText("Specialization: " + s.areasOfExpertise);
            } else {
                tvExpertise.setVisibility(View.GONE);
            }

            StringBuilder sb = new StringBuilder();
            if (s.articleCount > 0) {
                sb.append(s.articleCount).append(" Articles");
            }
            if (s.fatwaCount > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(s.fatwaCount).append(" Fatwas");
            }
            if (sb.length() == 0) {
                sb.append("Verified Academic Scholar");
            }
            tvStats.setText(sb.toString());

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onScholarClick(s);
            });
        }

        private static String getInitials(String name) {
            if (name == null || name.trim().isEmpty()) return "S";
            String[] parts = name.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, 1).toUpperCase();
            }
            String p0 = parts[0].replace(".", "");
            String p1 = parts[parts.length - 1].replace(".", "");
            if (p0.length() > 0 && p1.length() > 0) {
                return (p0.substring(0, 1) + p1.substring(0, 1)).toUpperCase();
            }
            return name.substring(0, 1).toUpperCase();
        }
    }
}
