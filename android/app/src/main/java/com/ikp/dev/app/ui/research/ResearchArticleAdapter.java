package com.ikp.dev.app.ui.research;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.util.ArrayList;
import java.util.List;

public final class ResearchArticleAdapter extends RecyclerView.Adapter<ResearchArticleAdapter.ArticleViewHolder> {

    public interface OnArticleClickListener {
        void onArticleClick(AcademyDtos.ArticleRowDto article);
    }

    public interface OnBookmarkClickListener {
        void onBookmarkClick(AcademyDtos.ArticleRowDto article);
    }

    private final List<AcademyDtos.ArticleRowDto> items = new ArrayList<>();
    private final OnArticleClickListener clickListener;
    private final OnBookmarkClickListener bookmarkListener;

    public ResearchArticleAdapter(OnArticleClickListener clickListener, OnBookmarkClickListener bookmarkListener) {
        this.clickListener = clickListener;
        this.bookmarkListener = bookmarkListener;
    }

    public void submitList(List<AcademyDtos.ArticleRowDto> articles) {
        this.items.clear();
        if (articles != null) {
            this.items.addAll(articles);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_research_article_card, parent, false);
        return new ArticleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
        AcademyDtos.ArticleRowDto article = items.get(position);
        holder.bind(article, clickListener, bookmarkListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ArticleViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTopic;
        private final TextView tvTierBadge;
        private final TextView tvReadTime;
        private final ImageView btnBookmark;
        private final TextView tvTitle;
        private final TextView tvTitleAr;
        private final TextView tvSummary;
        private final TextView tvAuthor;

        ArticleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTopic = itemView.findViewById(R.id.tv_article_topic);
            tvTierBadge = itemView.findViewById(R.id.tv_article_tier_badge);
            tvReadTime = itemView.findViewById(R.id.tv_article_read_time);
            btnBookmark = itemView.findViewById(R.id.btn_article_bookmark);
            tvTitle = itemView.findViewById(R.id.tv_article_title);
            tvTitleAr = itemView.findViewById(R.id.tv_article_title_ar);
            tvSummary = itemView.findViewById(R.id.tv_article_summary);
            tvAuthor = itemView.findViewById(R.id.tv_article_author);
        }

        void bind(AcademyDtos.ArticleRowDto item, OnArticleClickListener clickListener,
                  OnBookmarkClickListener bookmarkListener) {
            if (item.topic != null && item.topic.name != null && !item.topic.name.isEmpty()) {
                tvTopic.setVisibility(View.VISIBLE);
                tvTopic.setText(item.topic.name);
            } else {
                tvTopic.setVisibility(View.GONE);
            }

            if (item.sourceTier != null && !item.sourceTier.isEmpty()) {
                tvTierBadge.setVisibility(View.VISIBLE);
                tvTierBadge.setText(item.sourceTier.toUpperCase());
                String tier = item.sourceTier.toLowerCase();
                if (tier.contains("primary")) {
                    tvTierBadge.setBackgroundResource(R.drawable.bg_tier_primary);
                    tvTierBadge.setTextColor(Color.parseColor("#3D2C04"));
                } else if (tier.contains("editorial")) {
                    tvTierBadge.setBackgroundResource(R.drawable.bg_tier_editorial);
                    tvTierBadge.setTextColor(Color.parseColor("#063F32"));
                } else {
                    tvTierBadge.setBackgroundResource(R.drawable.bg_tier_secondary);
                    tvTierBadge.setTextColor(Color.parseColor("#0F4E34"));
                }
            } else {
                tvTierBadge.setVisibility(View.GONE);
            }

            if (item.readingMinutes != null && item.readingMinutes > 0) {
                tvReadTime.setVisibility(View.VISIBLE);
                tvReadTime.setText(item.readingMinutes + " min read");
            } else {
                tvReadTime.setVisibility(View.GONE);
            }

            tvTitle.setText(item.title == null ? "" : item.title);

            if (item.titleAr != null && !item.titleAr.trim().isEmpty()) {
                tvTitleAr.setVisibility(View.VISIBLE);
                tvTitleAr.setText(item.titleAr);
            } else {
                tvTitleAr.setVisibility(View.GONE);
            }

            if (item.summary != null && !item.summary.trim().isEmpty()) {
                tvSummary.setVisibility(View.VISIBLE);
                tvSummary.setText(item.summary);
            } else {
                tvSummary.setVisibility(View.GONE);
            }

            if (item.authorDisplayName != null && !item.authorDisplayName.trim().isEmpty()) {
                tvAuthor.setVisibility(View.VISIBLE);
                tvAuthor.setText("By " + item.authorDisplayName);
            } else {
                tvAuthor.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onArticleClick(item);
            });

            btnBookmark.setOnClickListener(v -> {
                if (bookmarkListener != null) bookmarkListener.onBookmarkClick(item);
            });
        }
    }
}
