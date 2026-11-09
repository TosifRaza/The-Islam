package com.ikp.dev.app.ui.assistant;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;

import java.util.ArrayList;
import java.util.List;

public final class AssistantConversationAdapter extends RecyclerView.Adapter<AssistantConversationAdapter.ViewHolder> {

    public interface OnConversationActionListener {
        void onOpenConversation(@NonNull AssistantModels.Conversation conversation);
        void onRenameConversation(@NonNull AssistantModels.Conversation conversation);
        void onDeleteConversation(@NonNull AssistantModels.Conversation conversation);
    }

    private final List<AssistantModels.Conversation> items = new ArrayList<>();
    private final OnConversationActionListener listener;

    public AssistantConversationAdapter(@NonNull OnConversationActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AssistantModels.Conversation> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assistant_conversation, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvPreview;
        private final TextView tvMeta;
        private final ImageButton btnMenu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_conv_title);
            tvPreview = itemView.findViewById(R.id.tv_conv_preview);
            tvMeta = itemView.findViewById(R.id.tv_conv_meta);
            btnMenu = itemView.findViewById(R.id.btn_conv_menu);
        }

        void bind(AssistantModels.Conversation conv) {
            tvTitle.setText(conv.title != null ? conv.title : "Research Inquiry");
            tvPreview.setText(conv.lastPreview != null && !conv.lastPreview.isEmpty()
                    ? conv.lastPreview : "No messages yet");

            CharSequence timeSpan = DateUtils.getRelativeTimeSpanString(
                    conv.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            tvMeta.setText(conv.messageCount + " messages · " + timeSpan);

            itemView.setOnClickListener(v -> listener.onOpenConversation(conv));

            btnMenu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.getMenu().add(0, 1, 0, "Rename");
                popup.getMenu().add(0, 2, 1, "Delete");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        listener.onRenameConversation(conv);
                        return true;
                    } else if (item.getItemId() == 2) {
                        listener.onDeleteConversation(conv);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }
}
