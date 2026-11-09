package com.ikp.dev.app.ui.assistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    public interface OnChatInteractionListener {
        void onCitationClick(@NonNull AssistantModels.SourceCitation citation);
        void onFollowUpClick(@NonNull String followUpQuestion);
        void onBookmarkToggle(@NonNull AssistantModels.ChatMessage message);
        void onFeedbackSubmit(@NonNull AssistantModels.ChatMessage message, @NonNull String rating);
    }

    private final List<AssistantModels.ChatMessage> items = new ArrayList<>();
    private final OnChatInteractionListener listener;

    public ChatMessageAdapter(@NonNull OnChatInteractionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AssistantModels.ChatMessage> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return "user".equalsIgnoreCase(items.get(position).role) ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_assistant_user_message, parent, false);
            return new UserViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_assistant_bot_message, parent, false);
            return new AssistantViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AssistantModels.ChatMessage msg = items.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(msg);
        } else if (holder instanceof AssistantViewHolder) {
            ((AssistantViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvText;
        private final TextView tvTime;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_user_text);
            tvTime = itemView.findViewById(R.id.tv_user_time);
        }

        void bind(AssistantModels.ChatMessage msg) {
            tvText.setText(msg.content);
            tvTime.setText(DateFormat.format("h:mm a", new Date(msg.timestamp)));
        }
    }

    final class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvGroundedBadge;
        private final TextView tvText;
        private final View btnToggleSources;
        private final TextView tvSourcesLabel;
        private final ImageView ivSourcesArrow;
        private final LinearLayout llCitationsContainer;
        private final ImageButton btnCopy;
        private final ImageButton btnShare;
        private final ImageButton btnBookmark;
        private final ImageButton btnThumbsUp;
        private final ImageButton btnThumbsDown;
        private final LinearLayout llFollowupsSection;
        private final LinearLayout llFollowupsChips;

        AssistantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroundedBadge = itemView.findViewById(R.id.tv_grounded_badge);
            tvText = itemView.findViewById(R.id.tv_bot_text);
            btnToggleSources = itemView.findViewById(R.id.btn_toggle_sources);
            tvSourcesLabel = itemView.findViewById(R.id.tv_sources_label);
            ivSourcesArrow = itemView.findViewById(R.id.iv_sources_arrow);
            llCitationsContainer = itemView.findViewById(R.id.ll_citations_container);
            btnCopy = itemView.findViewById(R.id.btn_copy);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnThumbsUp = itemView.findViewById(R.id.btn_thumbs_up);
            btnThumbsDown = itemView.findViewById(R.id.btn_thumbs_down);
            llFollowupsSection = itemView.findViewById(R.id.ll_followups_section);
            llFollowupsChips = itemView.findViewById(R.id.ll_followups_chips);
        }

        void bind(AssistantModels.ChatMessage msg) {
            Context ctx = itemView.getContext();

            // Grounded Badge Status
            if ("INSUFFICIENT_DATA".equalsIgnoreCase(msg.groundedStatus)) {
                tvGroundedBadge.setText("INSUFFICIENT DATA");
                tvGroundedBadge.setTextColor(Color.parseColor("#8A6F2F"));
            } else {
                tvGroundedBadge.setText("M6 GROUNDED");
                tvGroundedBadge.setTextColor(Color.parseColor("#164638"));
            }

            // Message text
            tvText.setText(msg.content);

            // Sources / Citations Container
            if (msg.citations != null && !msg.citations.isEmpty()) {
                btnToggleSources.setVisibility(View.VISIBLE);
                tvSourcesLabel.setText("Sources & Citations (" + msg.citations.size() + ")");
                llCitationsContainer.setVisibility(msg.citationsExpanded ? View.VISIBLE : View.GONE);
                ivSourcesArrow.setRotation(msg.citationsExpanded ? 90f : 0f);

                btnToggleSources.setOnClickListener(v -> {
                    msg.citationsExpanded = !msg.citationsExpanded;
                    llCitationsContainer.setVisibility(msg.citationsExpanded ? View.VISIBLE : View.GONE);
                    ivSourcesArrow.animate().rotation(msg.citationsExpanded ? 90f : 0f).setDuration(180).start();
                });

                llCitationsContainer.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(ctx);
                for (AssistantModels.SourceCitation c : msg.citations) {
                    View citView = inflater.inflate(R.layout.item_assistant_citation, llCitationsContainer, false);
                    TextView tvType = citView.findViewById(R.id.tv_citation_type);
                    TextView tvTitle = citView.findViewById(R.id.tv_citation_title);
                    TextView tvSubtitle = citView.findViewById(R.id.tv_citation_subtitle);

                    tvType.setText(c.type != null ? c.type.toUpperCase() : "SOURCE");
                    tvTitle.setText(c.title != null ? c.title : "");
                    tvSubtitle.setText(c.subtitle != null ? c.subtitle : "");

                    citView.setOnClickListener(v -> listener.onCitationClick(c));
                    llCitationsContainer.addView(citView);
                }
            } else {
                btnToggleSources.setVisibility(View.GONE);
                llCitationsContainer.setVisibility(View.GONE);
            }

            // Action Buttons
            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("IKP Research Answer", msg.content));
                    Toast.makeText(ctx, "Answer copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });

            btnShare.setOnClickListener(v -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                StringBuilder sb = new StringBuilder(msg.content);
                if (msg.citations != null && !msg.citations.isEmpty()) {
                    sb.append("\n\n--- Supporting Sources ---\n");
                    for (AssistantModels.SourceCitation c : msg.citations) {
                        sb.append("• ").append(c.title).append(" (").append(c.type).append(")\n");
                    }
                }
                sb.append("\nAnswered by The Islam Research Assistant (M6 Grounded)");
                shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                ctx.startActivity(Intent.createChooser(shareIntent, "Share Research Answer"));
            });

            // Bookmark icon state
            if (msg.isBookmarked) {
                btnBookmark.setImageResource(R.drawable.ic_bookmark_filled);
            } else {
                btnBookmark.setImageResource(R.drawable.ic_bookmark_outline);
            }
            btnBookmark.setOnClickListener(v -> {
                listener.onBookmarkToggle(msg);
                notifyItemChanged(getBindingAdapterPosition());
            });

            // Feedback Thumbs Up / Down
            if ("positive".equalsIgnoreCase(msg.feedback)) {
                btnThumbsUp.setColorFilter(Color.parseColor("#10B981"));
                btnThumbsDown.setColorFilter(Color.parseColor("#9AA69F"));
            } else if ("negative".equalsIgnoreCase(msg.feedback)) {
                btnThumbsUp.setColorFilter(Color.parseColor("#9AA69F"));
                btnThumbsDown.setColorFilter(Color.parseColor("#A33B2E"));
            } else {
                btnThumbsUp.setColorFilter(Color.parseColor("#063F32"));
                btnThumbsDown.setColorFilter(Color.parseColor("#063F32"));
            }

            btnThumbsUp.setOnClickListener(v -> {
                listener.onFeedbackSubmit(msg, "positive");
                msg.feedback = "positive";
                notifyItemChanged(getBindingAdapterPosition());
                Toast.makeText(ctx, "Thank you for the feedback!", Toast.LENGTH_SHORT).show();
            });

            btnThumbsDown.setOnClickListener(v -> {
                listener.onFeedbackSubmit(msg, "negative");
                msg.feedback = "negative";
                notifyItemChanged(getBindingAdapterPosition());
                Toast.makeText(ctx, "Feedback submitted for scholarly review", Toast.LENGTH_SHORT).show();
            });

            // Follow-up suggestions
            if (msg.followUps != null && !msg.followUps.isEmpty()) {
                llFollowupsSection.setVisibility(View.VISIBLE);
                llFollowupsChips.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(ctx);

                for (String fu : msg.followUps) {
                    TextView chip = new TextView(ctx);
                    chip.setText("→ " + fu);
                    chip.setTextSize(12f);
                    chip.setTextColor(Color.parseColor("#063F32"));
                    chip.setBackgroundResource(R.drawable.bg_category_badge);
                    chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EBF5EE")));
                    chip.setPadding(24, 14, 24, 14);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 4, 0, 4);
                    chip.setLayoutParams(lp);
                    chip.setClickable(true);
                    chip.setFocusable(true);
                    chip.setOnClickListener(v -> listener.onFollowUpClick(fu));
                    llFollowupsChips.addView(chip);
                }
            } else {
                llFollowupsSection.setVisibility(View.GONE);
            }
        }
    }
}
