package com.ikp.dev.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/** Compact book cards (horizontal rails on Home; recently-viewed row on
 *  Library). Placeholder cover tile until the media pipeline lands. */
public final class BookCardAdapter extends RecyclerView.Adapter<BookCardAdapter.Holder> {

    public interface OnBookClick {
        void onClick(Models.Book book);
    }

    private final List<Models.Book> items = new ArrayList<>();
    private final OnBookClick listener;

    public BookCardAdapter(OnBookClick listener) {
        this.listener = listener;
    }

    public void submit(List<Models.Book> books) {
        items.clear();
        if (books != null) {
            items.addAll(books);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Models.Book b = items.get(position);
        holder.title.setText(b.title == null ? "" : b.title);
        String author = b.authors.isEmpty() ? "" : b.authors.get(0);
        holder.author.setText(author);
        holder.author.setVisibility(author.isEmpty() ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(b);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView author;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_book_title);
            author = itemView.findViewById(R.id.tv_book_author);
        }
    }
}
