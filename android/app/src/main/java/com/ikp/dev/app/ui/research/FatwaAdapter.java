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

public final class FatwaAdapter extends RecyclerView.Adapter<FatwaAdapter.FatwaViewHolder> {

    public interface OnFatwaClickListener {
        void onFatwaClick(AcademyDtos.FatwaRowDto fatwa);
    }

    private final List<AcademyDtos.FatwaRowDto> items = new ArrayList<>();
    private final OnFatwaClickListener clickListener;

    public FatwaAdapter(OnFatwaClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submitList(List<AcademyDtos.FatwaRowDto> fatwas) {
        this.items.clear();
        if (fatwas != null) {
            this.items.addAll(fatwas);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FatwaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fatwa_card, parent, false);
        return new FatwaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FatwaViewHolder holder, int position) {
        AcademyDtos.FatwaRowDto fatwa = items.get(position);
        holder.bind(fatwa, clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class FatwaViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCategory;
        private final TextView tvDate;
        private final TextView tvQuestion;
        private final TextView tvExcerpt;
        private final TextView tvScholar;

        FatwaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_fatwa_category);
            tvDate = itemView.findViewById(R.id.tv_fatwa_date);
            tvQuestion = itemView.findViewById(R.id.tv_fatwa_question);
            tvExcerpt = itemView.findViewById(R.id.tv_fatwa_excerpt);
            tvScholar = itemView.findViewById(R.id.tv_fatwa_scholar);
        }

        void bind(AcademyDtos.FatwaRowDto f, OnFatwaClickListener listener) {
            if (f.category != null && !f.category.trim().isEmpty()) {
                tvCategory.setVisibility(View.VISIBLE);
                tvCategory.setText(f.category);
            } else {
                tvCategory.setVisibility(View.GONE);
            }

            if (f.publicationDate != null && !f.publicationDate.trim().isEmpty()) {
                tvDate.setVisibility(View.VISIBLE);
                tvDate.setText(f.publicationDate);
            } else {
                tvDate.setVisibility(View.GONE);
            }

            tvQuestion.setText(f.question == null ? "" : f.question);

            if (f.answerExcerpt != null && !f.answerExcerpt.trim().isEmpty()) {
                tvExcerpt.setVisibility(View.VISIBLE);
                tvExcerpt.setText(f.answerExcerpt);
            } else {
                tvExcerpt.setVisibility(View.GONE);
            }

            if (f.scholarName != null && !f.scholarName.trim().isEmpty()) {
                tvScholar.setText("Reviewed by " + f.scholarName);
            } else {
                tvScholar.setText("Verified Scholarly Review");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onFatwaClick(f);
            });
        }
    }
}
