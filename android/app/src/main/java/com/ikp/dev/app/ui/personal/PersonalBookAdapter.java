package com.ikp.dev.app.ui.personal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.ContentDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rows for the private personal-library list (M4 §10). */
public final class PersonalBookAdapter extends RecyclerView.Adapter<PersonalBookAdapter.Holder> {

    public interface OpenListener {
        void onOpen(ContentDtos.PersonalBookDto book);
    }

    public interface ActionListener {
        void onActions(ContentDtos.PersonalBookDto book);
    }

    private final List<ContentDtos.PersonalBookDto> items = new ArrayList<>();
    private final OpenListener openListener;
    private final ActionListener actionListener;

    public PersonalBookAdapter(OpenListener open, ActionListener actions) {
        this.openListener = open;
        this.actionListener = actions;
    }

    public void submit(List<ContentDtos.PersonalBookDto> rows) {
        items.clear();
        items.addAll(rows == null ? new ArrayList<>() : rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_personal_book, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ContentDtos.PersonalBookDto b = items.get(position);
        h.title.setText(b.title);
        h.author.setText(b.author == null || b.author.isEmpty()
                ? h.itemView.getContext().getString(R.string.personal_unknown_author) : b.author);
        h.format.setText(b.format == null ? "pdf" : b.format.toUpperCase(Locale.ROOT));
        h.size.setText(b.fileSizeBytes == null ? ""
                : String.format(Locale.getDefault(), "%.1f MB", b.fileSizeBytes / 1048576.0));
        h.searchable.setVisibility(b.textSearchable != null && b.textSearchable
                ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> openListener.onOpen(b));
        h.itemView.setOnLongClickListener(v -> {
            actionListener.onActions(b);
            return true;
        });
        h.more.setOnClickListener(v -> actionListener.onActions(b));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView author;
        final TextView format;
        final TextView size;
        final TextView searchable;
        final View more;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_title);
            author = itemView.findViewById(R.id.text_author);
            format = itemView.findViewById(R.id.chip_format);
            size = itemView.findViewById(R.id.text_size);
            searchable = itemView.findViewById(R.id.text_searchable);
            more = itemView.findViewById(R.id.button_more);
        }
    }
}
