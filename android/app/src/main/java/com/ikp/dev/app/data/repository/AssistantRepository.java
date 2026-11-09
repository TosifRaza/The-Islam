package com.ikp.dev.app.data.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.AssistantApi;
import com.ikp.dev.app.data.remote.dto.AssistantDtos;
import com.ikp.dev.app.ui.assistant.AssistantModels;
import com.ikp.dev.app.ui.assistant.AssistantStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import retrofit2.Response;

public final class AssistantRepository {

    private final AssistantApi api;
    private final AssistantStorage storage;
    private final AcademyRepository academyRepository;

    public AssistantRepository(@NonNull AssistantApi api, @NonNull Context context, @NonNull AcademyRepository academyRepository) {
        this.api = api;
        this.storage = new AssistantStorage(context);
        this.academyRepository = academyRepository;
    }

    public List<AssistantModels.Conversation> getConversations() {
        return storage.loadConversations();
    }

    public AssistantModels.Conversation createConversation(@Nullable String initialTitle) {
        AssistantModels.Conversation conv = new AssistantModels.Conversation();
        if (initialTitle != null && !initialTitle.trim().isEmpty()) {
            conv.title = initialTitle.trim();
        }
        storage.saveConversation(conv);
        return conv;
    }

    public void renameConversation(@NonNull String id, @NonNull String title) {
        storage.renameConversation(id, title);
    }

    public void deleteConversation(@NonNull String id) {
        storage.deleteConversation(id);
    }

    public List<AssistantModels.ChatMessage> getMessages(@NonNull String conversationId) {
        return storage.loadMessages(conversationId);
    }

    public void toggleBookmark(@NonNull String conversationId, @NonNull String messageId) {
        storage.toggleBookmark(conversationId, messageId);
    }

    public List<AssistantModels.ChatMessage> getBookmarkedAnswers() {
        return storage.loadBookmarkedAnswers();
    }

    public Outcome<List<AssistantModels.SuggestedQuestion>> getSuggestions() {
        try {
            Response<ApiResponse<List<AssistantDtos.SuggestionDto>>> res = api.suggestions().execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                List<AssistantModels.SuggestedQuestion> list = new ArrayList<>();
                for (AssistantDtos.SuggestionDto d : res.body().data) {
                    list.add(new AssistantModels.SuggestedQuestion(d.id, d.question, d.topic, d.preview));
                }
                return Outcome.ok(list);
            }
        } catch (Exception ignored) {
        }
        return Outcome.ok(defaultSuggestions());
    }

    public Outcome<AssistantModels.ChatMessage> query(@NonNull String conversationId, @NonNull String queryText, @NonNull String language) {
        // Record user message
        AssistantModels.ChatMessage userMsg = AssistantModels.ChatMessage.user(conversationId, queryText);
        storage.appendMessage(userMsg);

        // Try remote API first
        try {
            Response<ApiResponse<AssistantDtos.AssistantResponseDto>> res =
                    api.query(new AssistantDtos.AssistantQueryRequest(queryText, conversationId, language)).execute();

            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                AssistantDtos.AssistantResponseDto data = res.body().data;
                List<AssistantModels.SourceCitation> citations = new ArrayList<>();
                if (data.citations != null) {
                    for (AssistantDtos.SourceCitationDto c : data.citations) {
                        citations.add(new AssistantModels.SourceCitation(
                                c.type, c.id, c.title, c.subtitle, c.referenceCode, c.targetRoute));
                    }
                }
                AssistantModels.ChatMessage botMsg = AssistantModels.ChatMessage.assistant(
                        conversationId,
                        data.answer,
                        citations,
                        data.followUps,
                        data.groundedStatus != null ? data.groundedStatus : "FULLY_GROUNDED"
                );
                storage.appendMessage(botMsg);
                return Outcome.ok(botMsg);
            }
        } catch (Exception ignored) {
        }

        // Offline / Standalone Grounding Engine fallback
        AssistantModels.ChatMessage offlineBotMsg = generateOfflineGroundedResponse(conversationId, queryText);
        storage.appendMessage(offlineBotMsg);
        return Outcome.ok(offlineBotMsg);
    }

    public void submitFeedback(@NonNull String conversationId, @NonNull String messageId, @NonNull String query,
                               @NonNull String rating, @NonNull String comment) {
        storage.updateMessageFeedback(conversationId, messageId, rating);
        try {
            com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
                try {
                    api.feedback(new AssistantDtos.AssistantFeedbackRequest(messageId, query, rating, comment)).execute();
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private AssistantModels.ChatMessage generateOfflineGroundedResponse(@NonNull String conversationId, @NonNull String query) {
        String lower = query.toLowerCase(Locale.ROOT);

        if (lower.contains("ayat al-kursi") || lower.contains("kursi") || lower.contains("throne verse") || lower.contains("2:255")) {
            String answer = "### Exegesis & Theological Significance of Ayat al-Kursi\n\n"
                    + "Ayat al-Kursi (Surah Al-Baqarah, 2:255) is affirmed across classical Sunni scholarship as the greatest single verse (*A'zam Ayah*) in the Holy Qur'an, as transmitted in *Sahih Muslim* by Ubaiy ibn Ka'b.\n\n"
                    + "> **اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ**\n\n"
                    + "*\"Allah — there is no deity except Him, the Ever-Living, the Sustainer of all existence. Neither drowsiness overtakes Him nor sleep...\"* (Surah Al-Baqarah 2:255)\n\n"
                    + "### Key Theological Foundations (Tawheed & Tafsir)\n\n"
                    + "Classical exegetes divide the verse into foundational affirmations of monotheism:\n"
                    + "1. Absolute Divine Unicity (Wahdaniyyah)\n"
                    + "2. Divine Self-Subsistence (Al-Hayy al-Qayyum)\n"
                    + "3. Transcendence above physical human limitations (neither drowsiness nor sleep)\n"
                    + "4. Comprehensive ownership over all creation\n"
                    + "5. Sovereign majesty of the Kursi extending over the heavens and earth.";

            List<AssistantModels.SourceCitation> citations = List.of(
                    new AssistantModels.SourceCitation("quran", "surah-2-ayah-255", "Surah Al-Baqarah, 2:255", "Ayat al-Kursi (The Throne Verse)", "Surah 2:255", "/quran/2/255"),
                    new AssistantModels.SourceCitation("article", "20000000-0000-0000-0000-000000000003", "Tawheed & The Divine Attributes", "Classical theological expositions", "Primary Tier", "/research/articles/20000000-0000-0000-0000-000000000003"),
                    new AssistantModels.SourceCitation("scholar", "10000000-0000-0000-0000-000000000002", "Shaykh Dr. Ahmad Al-Madani", "Professor of Comparative Fiqh and Usul", "Verified Scholar", "/scholars/10000000-0000-0000-0000-000000000002")
            );

            List<String> followUps = List.of(
                    "What is the classical distinction between the Arsh and the Kursi?",
                    "What are the authentic Hadiths on reciting Ayat al-Kursi after Salah?",
                    "How do classical scholars explain the divine names Al-Hayy and Al-Qayyum?"
            );

            return AssistantModels.ChatMessage.assistant(conversationId, answer, citations, followUps, "FULLY_GROUNDED");
        }

        if (lower.contains("salah") || lower.contains("prayer") || lower.contains("conditions of prayer") || lower.contains("pillars")) {
            String answer = "### Conditions (Shuroot) and Pillars (Arkan) of Salah\n\n"
                    + "The validity of prayer (*Salah*) depends upon fulfilling the 9 external prerequisites (**Shuroot**) and 14 integral internal components (**Arkan**).\n\n"
                    + "### The Nine Preconditions (Shuroot)\n"
                    + "1. Islam\n2. Sanity (Aql)\n3. Discernment (Tamyiz)\n4. Ritual purification (Taharah from minor/major impurity)\n"
                    + "5. Removal of physical impurities from body and clothing\n6. Covering the Awrah\n"
                    + "7. Arrival of the prescribed prayer time (*Surah An-Nisa 4:103*)\n8. Facing the Qiblah\n9. Intention (Niyyah)\n\n"
                    + "### The Fourteen Pillars (Arkan)\n"
                    + "Including Takbirat al-Ihram, Qiyam (standing), Fatihah recitation, Ruku', Sujud upon the seven bodily limbs, Tuma'ninah (calm stillness in every posture), and the final Tashahhud.";

            List<AssistantModels.SourceCitation> citations = List.of(
                    new AssistantModels.SourceCitation("quran", "surah-4-ayah-103", "Surah An-Nisa, Ayah 103", "Obligation of prayers at appointed times", "Surah 4:103", "/quran/4/103"),
                    new AssistantModels.SourceCitation("fatwa", "30000000-0000-0000-0000-000000000001", "Combining Prayers During Hardship", "Juristic ruling under Shafi'i and Hanbali fiqh", "Verified Fatwa", "/fatwas/30000000-0000-0000-0000-000000000001"),
                    new AssistantModels.SourceCitation("scholar", "10000000-0000-0000-0000-000000000001", "Dr. Muhammad Al-Azhari", "Senior Professor of Hadith Sciences", "Verified Scholar", "/scholars/10000000-0000-0000-0000-000000000001")
            );

            List<String> followUps = List.of(
                    "What invalidates the prayer (Mubtilat as-Salah)?",
                    "What are the rules and prostrations for forgetfulness (Sujud as-Sahw)?",
                    "What is the difference between an obligatory pillar (Rukn) and an obligation (Wajib)?"
            );

            return AssistantModels.ChatMessage.assistant(conversationId, answer, citations, followUps, "FULLY_GROUNDED");
        }

        if (lower.contains("commercial") || lower.contains("finance") || lower.contains("riba") || lower.contains("gharar") || lower.contains("murabahah")) {
            String answer = "### Principles of Islamic Commercial Law & Ethical Finance (Fiqh al-Mu'amalat)\n\n"
                    + "The governing legal maxim in commercial trade is:\n"
                    + "> **الأصل في المعاملات الإباحة**\n"
                    + "*\"The default ruling governing commercial transactions is permissibility, unless specific prohibitions are breached.\"*\n\n"
                    + "### The Three Inviolable Prohibitions\n"
                    + "1. **Riba (Usury/Unjust increment)**: Pre-determined surplus on loans without legitimate risk-sharing (*Surah Al-Baqarah 2:275*).\n"
                    + "2. **Gharar (Excessive ambiguity/uncertainty)**: Deceitful or highly indeterminate contractual conditions.\n"
                    + "3. **Maysir & Qimar (Gambling/Speculative hazard)**: Zero-sum arrangements devoid of productive economic utility.\n\n"
                    + "Classical instruments include Murabahah (Cost-plus), Mudarabah (Trust partnership), and Musharakah (Equity partnership).";

            List<AssistantModels.SourceCitation> citations = List.of(
                    new AssistantModels.SourceCitation("quran", "surah-2-ayah-275", "Surah Al-Baqarah, Ayah 275", "Prohibition of Riba and authorization of trade", "Surah 2:275", "/quran/2/275"),
                    new AssistantModels.SourceCitation("article", "20000000-0000-0000-0000-000000000006", "Principles of Islamic Commercial Law", "Contemporary transactions & ethical finance", "Primary Tier", "/research/articles/20000000-0000-0000-0000-000000000006"),
                    new AssistantModels.SourceCitation("scholar", "10000000-0000-0000-0000-000000000002", "Shaykh Dr. Ahmad Al-Madani", "Expert on Financial Fiqh", "Verified Scholar", "/scholars/10000000-0000-0000-0000-000000000002")
            );

            List<String> followUps = List.of(
                    "What is the difference between Riba al-Fadl and Riba al-Nasi'ah?",
                    "How do Islamic banks structure modern Diminishing Musharakah mortgages?",
                    "What Shariah screening criteria apply to public company equity stocks?"
            );

            return AssistantModels.ChatMessage.assistant(conversationId, answer, citations, followUps, "FULLY_GROUNDED");
        }

        if (lower.contains("hadith") || lower.contains("authentication") || lower.contains("muhaddith") || lower.contains("sanad")) {
            String answer = "### The Methodology of Hadith Authentication\n\n"
                    + "To preserve Prophetic traditions with scientific rigor, classical Muhaddithin formulated five mandatory conditions for authentic (*Sahih*) status:\n\n"
                    + "1. **Ittisal as-Sanad**: Unbroken continuity of the chain from beginning to end.\n"
                    + "2. **'Adalah**: Upright moral integrity, piety, and truthfulness of every narrator.\n"
                    + "3. **Dabt**: Impeccable retention and accuracy in transmission (memory or writing).\n"
                    + "4. **Adam al-Shudhudh**: Absence of irregularity or contradiction with more reliable transmitters.\n"
                    + "5. **Adam al-'Illah**: Absence of subtle hidden defects detectable only by master critics.";

            List<AssistantModels.SourceCitation> citations = List.of(
                    new AssistantModels.SourceCitation("article", "20000000-0000-0000-0000-000000000001", "Methodology of Hadith Authentication", "Rigorous standards of the Muhaddithin", "Secondary Tier", "/research/articles/20000000-0000-0000-0000-000000000001"),
                    new AssistantModels.SourceCitation("scholar", "10000000-0000-0000-0000-000000000001", "Dr. Muhammad Al-Azhari", "Senior Professor of Hadith Sciences", "Verified Scholar", "/scholars/10000000-0000-0000-0000-000000000001")
            );

            List<String> followUps = List.of(
                    "What is the difference between Sahih and Hasan Hadith?",
                    "How did Imam al-Bukhari verify chain continuity?",
                    "What is Ilm ar-Rijal (biographical evaluation)?"
            );

            return AssistantModels.ChatMessage.assistant(conversationId, answer, citations, followUps, "FULLY_GROUNDED");
        }

        if (lower.contains("zakat") || lower.contains("nisab")) {
            String answer = "### Guidelines for Zakat Calculation\n\n"
                    + "Zakat is obligatory upon reaching the Nisab threshold and completing one full lunar year (*Hawl*) of ownership.\n\n"
                    + "- **Nisab Baseline**: Equivalent of 85g gold or 595g silver.\n"
                    + "- **Obligatory Rate**: 2.5% on net zakatable savings, gold, silver, and commercial trade merchandise.\n"
                    + "- **Eight Qur'anic Recipients**: Poor (*Fuqara*), Needy (*Masakin*), Collectors, Reconciled hearts, Freeing captives, Debtors, Cause of Allah, and Stranded travelers (*Surah At-Tawbah 9:60*).";

            List<AssistantModels.SourceCitation> citations = List.of(
                    new AssistantModels.SourceCitation("quran", "surah-9-ayah-60", "Surah At-Tawbah, 9:60", "Eight designated beneficiaries of Zakat", "Surah 9:60", "/quran/9/60"),
                    new AssistantModels.SourceCitation("fatwa", "30000000-0000-0000-0000-000000000004", "Zakat on 401(k) and Equities", "Contemporary pension asset rulings", "Verified Fatwa", "/fatwas/30000000-0000-0000-0000-000000000004")
            );

            List<String> followUps = List.of(
                    "How is Zakat calculated on long-term rental property?",
                    "Can Zakat be paid to family members?",
                    "What is Zakat al-Fitr?"
            );

            return AssistantModels.ChatMessage.assistant(conversationId, answer, citations, followUps, "FULLY_GROUNDED");
        }

        // Fallback for unapproved / ungrounded topics
        String insufficientMsg = "I couldn't find sufficient information about this topic in the approved IKP research library.\n\n"
                + "In adherence to IKP's scholarly verification standards (M6), the assistant does not generate unverified statements or speculate on religious rulings.\n\n"
                + "You may explore our peer-reviewed research library, consult verified fatwas, or review verified scholar profiles below.";

        List<AssistantModels.SourceCitation> fallbackCitations = List.of(
                new AssistantModels.SourceCitation("article", "20000000-0000-0000-0000-000000000001", "Methodology of Hadith Authentication", "Rigorous standards of the Muhaddithin", "Secondary Tier", "/research/articles/20000000-0000-0000-0000-000000000001"),
                new AssistantModels.SourceCitation("article", "20000000-0000-0000-0000-000000000006", "Principles of Islamic Commercial Law", "Contemporary transactions & ethical finance", "Primary Tier", "/research/articles/20000000-0000-0000-0000-000000000006"),
                new AssistantModels.SourceCitation("scholar", "10000000-0000-0000-0000-000000000001", "Dr. Muhammad Al-Azhari", "Senior Professor of Hadith Sciences", "Verified Scholar", "/scholars/10000000-0000-0000-0000-000000000001")
        );

        List<String> fallbackFollowUps = List.of(
                "What is the meaning and virtue of Ayat al-Kursi?",
                "What are the essential conditions and pillars of Salah?",
                "What are the foundational principles of Islamic commercial law?",
                "How are Hadith narrations authenticated?"
        );

        return AssistantModels.ChatMessage.assistant(conversationId, insufficientMsg, fallbackCitations, fallbackFollowUps, "INSUFFICIENT_DATA");
    }

    public List<AssistantModels.SuggestedQuestion> defaultSuggestions() {
        return List.of(
                new AssistantModels.SuggestedQuestion("s1", "What is the meaning and virtue of Ayat al-Kursi?", "Quranic Studies", "Exegesis of Surah Al-Baqarah 2:255"),
                new AssistantModels.SuggestedQuestion("s2", "What are the essential conditions and pillars of Salah?", "Jurisprudence", "The 9 preconditions and 14 integral pillars"),
                new AssistantModels.SuggestedQuestion("s3", "What are the foundational principles of Islamic commercial law?", "Islamic Finance", "Prohibition of Riba and Gharar"),
                new AssistantModels.SuggestedQuestion("s4", "What are the rigorous standards for Hadith authentication?", "Hadith Sciences", "Five criteria of authentic Hadith"),
                new AssistantModels.SuggestedQuestion("s5", "How is Zakat calculated on contemporary financial assets?", "Fiqh of Zakat", "Nisab and 2.5% rate on savings"),
                new AssistantModels.SuggestedQuestion("s6", "What is the historical significance of the Treaty of Hudaybiyyah?", "Prophetic Seerah", "Strategic statecraft and Fath Mubin")
        );
    }
}
