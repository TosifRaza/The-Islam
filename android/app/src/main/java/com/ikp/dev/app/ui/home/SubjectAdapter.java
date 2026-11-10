package com.ikp.dev.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/** Horizontal subject cards (Home + Library). Position badge shows the
 *  backend display order — never used as an identifier (stable id = slug). */
public final class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.Holder> {

    public interface OnSubjectClick {
        void onClick(Models.Subject subject);
    }

    private final List<Models.Subject> items = new ArrayList<>();
    private final OnSubjectClick listener;

    public SubjectAdapter(OnSubjectClick listener) {
        this.listener = listener;
    }

    public void submit(List<Models.Subject> subjects) {
        items.clear();
        if (subjects != null) {
            items.addAll(subjects);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Models.Subject s = items.get(position);
        holder.name.setText(s.name == null ? "" : s.name);
        holder.nameAr.setVisibility(s.nameAr == null || s.nameAr.isEmpty() ? View.GONE : View.VISIBLE);
        holder.nameAr.setText(s.nameAr == null ? "" : s.nameAr);
        int[] icons = {R.drawable.ic_quran_rehal, R.drawable.ic_shortcut_hadith,
                R.drawable.ic_action_notes, R.drawable.ic_mosque_silhouette,
                R.drawable.ic_shortcut_books, R.drawable.ic_chip_tag,
                R.drawable.ic_deck_flashcards, R.drawable.ic_tab_mosque,
                R.drawable.ic_book, R.drawable.ic_gold_sparkle};
        holder.icon.setImageResource(icons[position % icons.length]);
        holder.position.setText(s.position == null ? "•" : String.valueOf(s.position));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(s);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView nameAr;
        final TextView position;
        final ImageView icon;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_subject_name);
            nameAr = itemView.findViewById(R.id.tv_subject_name_ar);
            position = itemView.findViewById(R.id.tv_position);
            icon = itemView.findViewById(R.id.iv_subject_icon);
        }
    }
}
