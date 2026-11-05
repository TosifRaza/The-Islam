package com.ikp.dev.app.ui.library;

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

/** Book rows for the search/browse list. */
public final class BookRowAdapter extends RecyclerView.Adapter<BookRowAdapter.Holder> {

    public interface OnBookClick {
        void onClick(Models.Book book);
    }

    public interface OnLoadMore {
        void onLoadMore();
    }

    private final List<Models.Book> items = new ArrayList<>();
    private final OnBookClick listener;
    private OnLoadMore loadMore;

    public BookRowAdapter(OnBookClick listener) {
        this.listener = listener;
    }

    public void setLoadMore(OnLoadMore loadMore) {
        this.loadMore = loadMore;
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
                .inflate(R.layout.item_book_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Models.Book b = items.get(position);
        holder.title.setText(b.title == null ? "" : b.title);
        String author = b.authors.isEmpty() ? "" : b.authors.get(0);
        holder.author.setText(author);
        holder.author.setVisibility(author.isEmpty() ? View.GONE : View.VISIBLE);

        StringBuilder meta = new StringBuilder();
        if (b.language != null && !b.language.isEmpty()) {
            meta.append(b.language);
        }
        if (!b.formats.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(android.text.TextUtils.join(", ", b.formats));
        }
        holder.meta.setText(meta.toString());
        holder.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(b);
            }
        });

        if (loadMore != null && position >= items.size() - 3) {
            loadMore.onLoadMore();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView author;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_book_title);
            author = itemView.findViewById(R.id.tv_book_author);
            meta = itemView.findViewById(R.id.tv_book_meta);
        }
    }
}
