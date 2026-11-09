package com.ikp.dev.app.ui.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AssistantModels {

    public static final class Conversation {
        public String id;
        public String title;
        public String lastPreview;
        public int messageCount;
        public long updatedAt;
        public boolean isBookmarked;

        public Conversation() {
            this.id = java.util.UUID.randomUUID().toString();
            this.title = "New Research Inquiry";
            this.lastPreview = "";
            this.messageCount = 0;
            this.updatedAt = System.currentTimeMillis();
            this.isBookmarked = false;
        }

        public Conversation(String id, String title, String lastPreview, int messageCount, long updatedAt) {
            this.id = id;
            this.title = title;
            this.lastPreview = lastPreview;
            this.messageCount = messageCount;
            this.updatedAt = updatedAt;
            this.isBookmarked = false;
        }
    }

    public static final class SourceCitation {
        public String type; // "article", "quran", "hadith", "fatwa", "scholar"
        public String id;
        public String title;
        public String subtitle;
        public String referenceCode;
        public String targetRoute;

        public SourceCitation() {}

        public SourceCitation(String type, String id, String title, String subtitle, String referenceCode, String targetRoute) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.referenceCode = referenceCode;
            this.targetRoute = targetRoute;
        }
    }

    public static final class ChatMessage {
        public String id;
        public String conversationId;
        public String role; // "user" or "assistant"
        public String content;
        public long timestamp;
        public List<SourceCitation> citations = new ArrayList<>();
        public List<String> followUps = new ArrayList<>();
        public boolean isBookmarked = false;
        public String feedback = "none"; // "none", "positive", "negative"
        public boolean citationsExpanded = false;
        public String groundedStatus = "FULLY_GROUNDED";

        public ChatMessage() {
            this.id = java.util.UUID.randomUUID().toString();
            this.timestamp = System.currentTimeMillis();
        }

        public static ChatMessage user(@NonNull String conversationId, @NonNull String text) {
            ChatMessage m = new ChatMessage();
            m.conversationId = conversationId;
            m.role = "user";
            m.content = text;
            return m;
        }

        public static ChatMessage assistant(@NonNull String conversationId, @NonNull String text,
                                            @Nullable List<SourceCitation> citations,
                                            @Nullable List<String> followUps,
                                            @NonNull String groundedStatus) {
            ChatMessage m = new ChatMessage();
            m.conversationId = conversationId;
            m.role = "assistant";
            m.content = text;
            if (citations != null) m.citations.addAll(citations);
            if (followUps != null) m.followUps.addAll(followUps);
            m.groundedStatus = groundedStatus;
            return m;
        }
    }

    public static final class SuggestedQuestion {
        public String id;
        public String question;
        public String topic;
        public String preview;

        public SuggestedQuestion(String id, String question, String topic, String preview) {
            this.id = id;
            this.question = question;
            this.topic = topic;
            this.preview = preview;
        }
    }

    private AssistantModels() {}
}
