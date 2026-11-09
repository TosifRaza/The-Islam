package com.ikp.dev.app.ui.assistant;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AssistantStorage {

    private static final String CONVERSATIONS_FILE = "assistant_conversations.json";
    private static final String MESSAGES_PREFIX = "assistant_msgs_";

    private final Context context;
    private final Gson gson = new Gson();
    private final Object lock = new Object();

    public AssistantStorage(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public List<AssistantModels.Conversation> loadConversations() {
        synchronized (lock) {
            File file = new File(context.getFilesDir(), CONVERSATIONS_FILE);
            if (!file.exists()) {
                return new ArrayList<>();
            }
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<AssistantModels.Conversation>>() {}.getType();
                List<AssistantModels.Conversation> list = gson.fromJson(reader, listType);
                if (list != null) {
                    list.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
                    return list;
                }
            } catch (Exception ignored) {
            }
            return new ArrayList<>();
        }
    }

    public void saveConversation(@NonNull AssistantModels.Conversation conversation) {
        synchronized (lock) {
            List<AssistantModels.Conversation> all = loadConversations();
            int index = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id.equals(conversation.id)) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                all.set(index, conversation);
            } else {
                all.add(0, conversation);
            }
            persistConversations(all);
        }
    }

    public void renameConversation(@NonNull String id, @NonNull String newTitle) {
        synchronized (lock) {
            List<AssistantModels.Conversation> all = loadConversations();
            for (AssistantModels.Conversation c : all) {
                if (c.id.equals(id)) {
                    c.title = newTitle;
                    c.updatedAt = System.currentTimeMillis();
                    break;
                }
            }
            persistConversations(all);
        }
    }

    public void deleteConversation(@NonNull String id) {
        synchronized (lock) {
            List<AssistantModels.Conversation> all = loadConversations();
            all.removeIf(c -> c.id.equals(id));
            persistConversations(all);

            File msgFile = new File(context.getFilesDir(), MESSAGES_PREFIX + id + ".json");
            if (msgFile.exists()) {
                msgFile.delete();
            }
        }
    }

    private void persistConversations(List<AssistantModels.Conversation> all) {
        File file = new File(context.getFilesDir(), CONVERSATIONS_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(all, writer);
        } catch (IOException ignored) {
        }
    }

    public List<AssistantModels.ChatMessage> loadMessages(@NonNull String conversationId) {
        synchronized (lock) {
            File file = new File(context.getFilesDir(), MESSAGES_PREFIX + conversationId + ".json");
            if (!file.exists()) {
                return new ArrayList<>();
            }
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<AssistantModels.ChatMessage>>() {}.getType();
                List<AssistantModels.ChatMessage> msgs = gson.fromJson(reader, listType);
                return msgs != null ? msgs : new ArrayList<>();
            } catch (Exception ignored) {
            }
            return new ArrayList<>();
        }
    }

    public void appendMessage(@NonNull AssistantModels.ChatMessage msg) {
        synchronized (lock) {
            List<AssistantModels.ChatMessage> msgs = loadMessages(msg.conversationId);
            msgs.add(msg);

            File file = new File(context.getFilesDir(), MESSAGES_PREFIX + msg.conversationId + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(msgs, writer);
            } catch (IOException ignored) {
            }

            // Update conversation metadata
            List<AssistantModels.Conversation> convs = loadConversations();
            AssistantModels.Conversation target = null;
            for (AssistantModels.Conversation c : convs) {
                if (c.id.equals(msg.conversationId)) {
                    target = c;
                    break;
                }
            }
            if (target == null) {
                target = new AssistantModels.Conversation(
                        msg.conversationId,
                        generateTitleFromQuestion(msg.content),
                        truncate(msg.content, 90),
                        msgs.size(),
                        System.currentTimeMillis()
                );
                convs.add(0, target);
            } else {
                target.messageCount = msgs.size();
                target.updatedAt = System.currentTimeMillis();
                target.lastPreview = truncate(msg.content, 90);
                if ("New Research Inquiry".equals(target.title) && "user".equals(msg.role)) {
                    target.title = generateTitleFromQuestion(msg.content);
                }
            }
            persistConversations(convs);
        }
    }

    public void updateMessageFeedback(@NonNull String conversationId, @NonNull String messageId, @NonNull String feedback) {
        synchronized (lock) {
            List<AssistantModels.ChatMessage> msgs = loadMessages(conversationId);
            for (AssistantModels.ChatMessage m : msgs) {
                if (m.id.equals(messageId)) {
                    m.feedback = feedback;
                    break;
                }
            }
            persistMessages(conversationId, msgs);
        }
    }

    public void toggleBookmark(@NonNull String conversationId, @NonNull String messageId) {
        synchronized (lock) {
            List<AssistantModels.ChatMessage> msgs = loadMessages(conversationId);
            for (AssistantModels.ChatMessage m : msgs) {
                if (m.id.equals(messageId)) {
                    m.isBookmarked = !m.isBookmarked;
                    break;
                }
            }
            persistMessages(conversationId, msgs);
        }
    }

    private void persistMessages(String conversationId, List<AssistantModels.ChatMessage> msgs) {
        File file = new File(context.getFilesDir(), MESSAGES_PREFIX + conversationId + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(msgs, writer);
        } catch (IOException ignored) {
        }
    }

    public List<AssistantModels.ChatMessage> loadBookmarkedAnswers() {
        synchronized (lock) {
            List<AssistantModels.ChatMessage> bookmarks = new ArrayList<>();
            List<AssistantModels.Conversation> all = loadConversations();
            for (AssistantModels.Conversation c : all) {
                List<AssistantModels.ChatMessage> msgs = loadMessages(c.id);
                for (AssistantModels.ChatMessage m : msgs) {
                    if (m.isBookmarked) {
                        bookmarks.add(m);
                    }
                }
            }
            return bookmarks;
        }
    }

    private static String generateTitleFromQuestion(String text) {
        if (text == null || text.trim().isEmpty()) return "Research Inquiry";
        String clean = text.trim();
        if (clean.toLowerCase().contains("ayat al-kursi")) return "Ayat al-Kursi Exegesis";
        if (clean.toLowerCase().contains("salah")) return "Conditions & Pillars of Salah";
        if (clean.toLowerCase().contains("commercial") || clean.toLowerCase().contains("finance")) return "Islamic Commercial Law";
        if (clean.toLowerCase().contains("hadith")) return "Hadith Authentication Criteria";
        if (clean.toLowerCase().contains("zakat")) return "Zakat Calculation Rules";
        if (clean.toLowerCase().contains("ramadan") || clean.toLowerCase().contains("fasting")) return "Fasting & Ramadan Fiqh";
        if (clean.toLowerCase().contains("hudaybiyyah")) return "Treaty of Hudaybiyyah";

        if (clean.length() <= 32) return clean;
        return clean.substring(0, 30) + "…";
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }
}
