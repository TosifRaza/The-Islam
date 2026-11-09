package com.ikp.dev.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class AssistantDtos {

    public static final class AssistantQueryRequest {
        @SerializedName("query")
        public String query;

        @SerializedName("conversationId")
        public String conversationId;

        @SerializedName("language")
        public String language;

        public AssistantQueryRequest(String query, String conversationId, String language) {
            this.query = query;
            this.conversationId = conversationId;
            this.language = language;
        }
    }

    public static final class SourceCitationDto {
        @SerializedName("type")
        public String type; // "article", "quran", "hadith", "fatwa", "scholar"

        @SerializedName("id")
        public String id;

        @SerializedName("title")
        public String title;

        @SerializedName("subtitle")
        public String subtitle;

        @SerializedName("referenceCode")
        public String referenceCode;

        @SerializedName("targetRoute")
        public String targetRoute;
    }

    public static final class AssistantResponseDto {
        @SerializedName("answerId")
        public String answerId;

        @SerializedName("query")
        public String query;

        @SerializedName("title")
        public String title;

        @SerializedName("answer")
        public String answer;

        @SerializedName("citations")
        public List<SourceCitationDto> citations;

        @SerializedName("followUps")
        public List<String> followUps;

        @SerializedName("groundedStatus")
        public String groundedStatus; // "FULLY_GROUNDED", "PARTIALLY_GROUNDED", "INSUFFICIENT_DATA"

        @SerializedName("disclaimer")
        public String disclaimer;
    }

    public static final class SuggestionDto {
        @SerializedName("id")
        public String id;

        @SerializedName("question")
        public String question;

        @SerializedName("topic")
        public String topic;

        @SerializedName("preview")
        public String preview;
    }

    public static final class AssistantFeedbackRequest {
        @SerializedName("answerId")
        public String answerId;

        @SerializedName("query")
        public String query;

        @SerializedName("rating")
        public String rating;

        @SerializedName("feedback")
        public String feedback;

        public AssistantFeedbackRequest(String answerId, String query, String rating, String feedback) {
            this.answerId = answerId;
            this.query = query;
            this.rating = rating;
            this.feedback = feedback;
        }
    }

    private AssistantDtos() {}
}
