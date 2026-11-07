package com.ikp.dev.app.ui.academy;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FlashcardDeckAdapter extends RecyclerView.Adapter<FlashcardDeckAdapter.ViewHolder> {

    public interface OnDeckClickListener {
        void onDeckClick(@NonNull AcademyDtos.DeckRowDto deck);
    }

    private final List<AcademyDtos.DeckRowDto> items = new ArrayList<>();
    private final OnDeckClickListener listener;

    public FlashcardDeckAdapter(@Nullable OnDeckClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AcademyDtos.DeckRowDto> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard_deck, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AcademyDtos.DeckRowDto deck = items.get(position);
        int count = deck.cardCount != null ? deck.cardCount : 0;
        holder.countView.setText(String.format(Locale.US, "%d Cards", count));
        holder.titleView.setText(deck.title != null ? deck.title : "");
        holder.descView.setText(deck.description != null ? deck.description : "");

        View.OnClickListener click = v -> {
            if (listener != null) {
                listener.onDeckClick(deck);
            }
        };

        holder.itemView.setOnClickListener(click);
        holder.practiceBtn.setOnClickListener(click);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView countView;
        final TextView titleView;
        final TextView descView;
        final Button practiceBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            countView = itemView.findViewById(R.id.tv_deck_count);
            titleView = itemView.findViewById(R.id.tv_deck_title);
            descView = itemView.findViewById(R.id.tv_deck_desc);
            practiceBtn = itemView.findViewById(R.id.btn_deck_practice);
        }
    }
}
