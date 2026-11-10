package com.ikp.dev.app.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/**
 * Islamic Book Row Adapter matching the gold-embossed leather book cards design.
 */
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

        // 1. Parse English and Arabic Titles
        String rawTitle = b.title == null ? "" : b.title.trim();
        String engTitle = rawTitle;
        String arTitle = "";

        int parenIdx = rawTitle.indexOf('(');
        if (parenIdx > 0 && rawTitle.endsWith(")")) {
            engTitle = rawTitle.substring(0, parenIdx).trim();
            arTitle = rawTitle.substring(parenIdx + 1, rawTitle.length() - 1).trim();
        }

        holder.title.setText(engTitle);
        if (!arTitle.isEmpty()) {
            holder.titleAr.setText(arTitle);
            holder.titleAr.setVisibility(View.VISIBLE);
        } else {
            holder.titleAr.setVisibility(View.GONE);
        }

        // 2. Realistic 3D Leather Book Cover Asset
        int coverRes = getBookCoverDrawable(b.bookId, engTitle);
        holder.coverImage.setImageResource(coverRes);

        // 3. Author Row
        String author = b.authors.isEmpty() ? "" : b.authors.get(0);
        holder.author.setText(author);
        holder.authorRow.setVisibility(author.isEmpty() ? View.GONE : View.VISIBLE);

        // 4. Badges (Language & Format)
        if (b.language != null && !b.language.isEmpty()) {
            holder.badgeLang.setText(b.language);
            holder.layoutBadgeLang.setVisibility(View.VISIBLE);
        } else {
            holder.layoutBadgeLang.setVisibility(View.GONE);
        }

        if (!b.formats.isEmpty()) {
            holder.badgeFormat.setText(b.formats.get(0));
            holder.layoutBadgeFormat.setVisibility(View.VISIBLE);
        } else {
            holder.layoutBadgeFormat.setVisibility(View.GONE);
        }

        // 5. Actions
        final String bookTitleFinal = engTitle;
        holder.btnBookmark.setOnClickListener(v -> {
            v.setSelected(!v.isSelected());
            Toast.makeText(v.getContext(), "Saved: " + bookTitleFinal, Toast.LENGTH_SHORT).show();
        });

        holder.btnDownload.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), "Downloading: " + bookTitleFinal + " (PDF)", Toast.LENGTH_SHORT).show();
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(b);
            }
        });

        if (loadMore != null && position >= items.size() - 3) {
            loadMore.onLoadMore();
        }
    }

    private static int getBookCoverDrawable(String bookId, String engTitle) {
        if (bookId != null) {
            if (bookId.contains("tafsir_ibn_kathir") || bookId.contains("kathir")) {
                return R.drawable.ic_book_cover_ibn_kathir;
            }
            if (bookId.contains("tafsir_jalalayn") || bookId.contains("jalalayn")) {
                return R.drawable.ic_book_cover_jalalayn;
            }
            if (bookId.contains("bukhari")) {
                return R.drawable.ic_book_cover_bukhari;
            }
            if (bookId.contains("muslim")) {
                return R.drawable.ic_book_cover_muslim;
            }
            if (bookId.contains("riyadh_salihin") || bookId.contains("salihin")) {
                return R.drawable.ic_book_cover_riyadh;
            }
        }
        String lower = engTitle.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("kathir")) return R.drawable.ic_book_cover_ibn_kathir;
        if (lower.contains("jalalayn")) return R.drawable.ic_book_cover_jalalayn;
        if (lower.contains("bukhari")) return R.drawable.ic_book_cover_bukhari;
        if (lower.contains("muslim")) return R.drawable.ic_book_cover_muslim;
        if (lower.contains("salihin")) return R.drawable.ic_book_cover_riyadh;
        return R.drawable.ic_book_cover_bukhari;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView coverImage;
        final TextView title;
        final TextView titleAr;
        final View authorRow;
        final TextView author;
        final View layoutBadgeLang;
        final TextView badgeLang;
        final View layoutBadgeFormat;
        final TextView badgeFormat;
        final ImageButton btnBookmark;
        final ImageButton btnDownload;

        Holder(@NonNull View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.iv_book_cover);
            title = itemView.findViewById(R.id.tv_book_title);
            titleAr = itemView.findViewById(R.id.tv_book_title_ar);
            authorRow = itemView.findViewById(R.id.tv_book_author).getParent() instanceof View
                    ? (View) itemView.findViewById(R.id.tv_book_author).getParent() : itemView;
            author = itemView.findViewById(R.id.tv_book_author);
            layoutBadgeLang = itemView.findViewById(R.id.layout_badge_lang);
            badgeLang = itemView.findViewById(R.id.tv_badge_lang);
            layoutBadgeFormat = itemView.findViewById(R.id.layout_badge_format);
            badgeFormat = itemView.findViewById(R.id.tv_badge_format);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}
