package app.ikp.backend.research;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.quran.QuranRepository;
import app.ikp.backend.user.UserRepository;
import app.ikp.backend.workflow.ContentReviewRecord;
import app.ikp.backend.workflow.ContentReviewRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Research service (M5 spec Module 11). The three-way source classification
 * (primary / secondary / editorial) is mandatory; primary and secondary
 * articles additionally require scholarly review before publication
 * (editorial tier does not). Citations are validated against the records they
 * reference at save time — a citation pointing to nothing is rejected
 * (never silently stored, never fabricated).
 */
@Service
public class ResearchService {

    private final ResearchTopicRepository topics;
    private final ResearchArticleRepository articles;
    private final ResearchArticleCitationRepository citations;
    private final app.ikp.backend.subject.SubjectRepository subjects;
    private final UserRepository users;
    private final ContentReviewRecordRepository reviews;
    private final AuditService audit;
    private final QuranRepository quran;
    private final JdbcTemplate jdbc;

    public ResearchService(ResearchTopicRepository topics, ResearchArticleRepository articles,
                           ResearchArticleCitationRepository citations,
                           app.ikp.backend.subject.SubjectRepository subjects, UserRepository users,
                           ContentReviewRecordRepository reviews, AuditService audit,
                           QuranRepository quran, JdbcTemplate jdbc) {
        this.topics = topics;
        this.articles = articles;
        this.citations = citations;
        this.subjects = subjects;
        this.users = users;
        this.reviews = reviews;
        this.audit = audit;
        this.quran = quran;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- admin

    @Transactional
    public ResearchArticle createArticle(String topicSlug, String title, String titleAr, String summary,
                                         String body, String language, String authorDisplayName,
                                         ResearchArticle.SourceTier sourceTier,
                                         List<CitationInput> citationInputs, SecurityPrincipal actor) {
        ResearchTopic topic = topics.findBySlug(topicSlug)
                .orElseThrow(() -> ApiException.validation("unknown research topic slug"));
        ResearchArticle a = new ResearchArticle();
        a.setTopic(topic);
        a.setTitle(title);
        a.setTitleAr(titleAr);
        a.setSummary(summary);
        a.setBody(body);
        a.setLanguage(language == null ? "en" : language);
        a.setAuthorDisplayName(authorDisplayName);
        a.setSourceTier(sourceTier);
        a.setCreatedBy(users.getReferenceById(actor.id()));
        articles.save(a);
        replaceCitations(a, citationInputs);
        audit.record(actor.id(), "research.article.create", "research_article", a.getId(), null,
                snapshot(a), null);
        return a;
    }

    @Transactional
    public ResearchArticle updateArticle(UUID publicId, String topicSlug, String title, String titleAr,
                                         String summary, String body, String language,
                                         String authorDisplayName, ResearchArticle.SourceTier sourceTier,
                                         List<CitationInput> citationInputs, SecurityPrincipal actor) {
        ResearchArticle a = article(publicId);
        if (a.getStatus() != ResearchArticle.Status.draft) {
            throw ApiException.workflow("only draft articles can be edited (state " + a.getStatus() + ")");
        }
        ResearchTopic topic = topics.findBySlug(topicSlug)
                .orElseThrow(() -> ApiException.validation("unknown research topic slug"));
        a.setTopic(topic);
        a.setTitle(title);
        a.setTitleAr(titleAr);
        a.setSummary(summary);
        a.setBody(body);
        if (language != null) a.setLanguage(language);
        a.setAuthorDisplayName(authorDisplayName);
        a.setSourceTier(sourceTier);
        replaceCitations(a, citationInputs);
        audit.record(actor.id(), "research.article.update", "research_article", a.getId(),
                snapshot(a), snapshot(a), null);
        return a;
    }

    @Transactional
    public Transition transition(UUID publicId, String action, String notes, SecurityPrincipal actor) {
        ResearchArticle a = article(publicId);
        ResearchArticle.Status before = a.getStatus();
        switch (action == null ? "" : action) {
            case "submit-review" -> {
                requireState(a, ResearchArticle.Status.draft, action);
                a.setStatus(ResearchArticle.Status.in_editorial_review);
            }
            case "approve-editorial" -> {
                requireState(a, ResearchArticle.Status.in_editorial_review, action);
                requireRoles(actor);
                if (actor.id().equals(a.getCreatedBy() == null ? null : a.getCreatedBy().getId())) {
                    deny(a, action, "editorial approval requires a reviewer other than the creator");
                }
                saveReview("research_article", a.getId(), ContentReviewRecord.Stage.editorial_review,
                        ContentReviewRecord.Decision.approved, actor, notes);
                a.setStatus(a.getSourceTier() == ResearchArticle.SourceTier.editorial
                        ? ResearchArticle.Status.approved
                        : ResearchArticle.Status.in_scholarly_review);
            }
            case "approve" -> {
                requireState(a, ResearchArticle.Status.in_scholarly_review, action);
                requireScholar(actor, a, action);
                saveReview("research_article", a.getId(), ContentReviewRecord.Stage.scholarly_review,
                        ContentReviewRecord.Decision.approved, actor, notes);
                a.setStatus(ResearchArticle.Status.approved);
            }
            case "reject" -> {
                if (notes == null || notes.isBlank()) throw ApiException.validation("rejection requires notes");
                requireState(a, ResearchArticle.Status.in_editorial_review,
                        ResearchArticle.Status.in_scholarly_review, action);
                if (a.getStatus() == ResearchArticle.Status.in_scholarly_review) {
                    requireScholar(actor, a, action);
                    saveReview("research_article", a.getId(), ContentReviewRecord.Stage.scholarly_review,
                            ContentReviewRecord.Decision.rejected, actor, notes);
                } else {
                    requireRoles(actor);
                    saveReview("research_article", a.getId(), ContentReviewRecord.Stage.editorial_review,
                            ContentReviewRecord.Decision.rejected, actor, notes);
                }
                a.setStatus(ResearchArticle.Status.draft);
            }
            case "publish" -> {
                requireState(a, ResearchArticle.Status.approved, action);
                requirePermission(actor, "content.publish", action, a);
                a.setStatus(ResearchArticle.Status.published);
                a.setPublishedAt(Instant.now());
            }
            case "unpublish" -> {
                requireState(a, ResearchArticle.Status.published, action);
                requirePermission(actor, "content.publish", action, a);
                a.setStatus(ResearchArticle.Status.approved);
            }
            case "archive" -> {
                requireState(a, ResearchArticle.Status.draft, ResearchArticle.Status.approved, action);
                requirePermission(actor, "content.publish", action, a);
                a.setStatus(ResearchArticle.Status.archived);
            }
            default -> throw ApiException.validation("unsupported action");
        }
        audit.record(actor.id(), "research.article." + action, "research_article", a.getId(),
                Map.of("state", before.name()), Map.of("state", a.getStatus().name()), notes);
        return new Transition(a.getPublicId(), before.name(), a.getStatus().name());
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public ResearchArticle article(UUID publicId) {
        return articles.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("research article not found"));
    }

    @Transactional(readOnly = true)
    public Page<ResearchArticle> adminList(String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (status != null && !status.isBlank()) {
            return articles.findByStatusOrderByUpdatedAtDesc(
                    ResearchArticle.Status.valueOf(status.toLowerCase()), pr);
        }
        return articles.findAll(pr);
    }

    @Transactional(readOnly = true)
    public Page<ResearchArticle> publicList(String topicSlug, String tier, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        if (topicSlug != null && !topicSlug.isBlank()) {
            return articles.findByTopicSlugAndStatusOrderByPublishedAtDesc(topicSlug,
                    ResearchArticle.Status.published, pr);
        }
        return articles.findByStatusOrderByPublishedAtDesc(ResearchArticle.Status.published, pr);
    }

    @Transactional(readOnly = true)
    public List<ResearchTopic> activeTopics() {
        return topics.findByStatusOrderBySortOrderAsc("active");
    }

    @Transactional(readOnly = true)
    public List<ResearchArticleCitation> citationsOf(Long articleId) {
        return citations.findByArticleIdOrderBySortOrderAsc(articleId);
    }

    // ---------------------------------------------------------------- citations

    public record CitationInput(String citationKind, Integer surahId, Integer ayahNumber, Long hadithId,
                                UUID bookPublicId, UUID articlePublicId, String externalRef,
                                String quote, String note, Integer sortOrder) { }

    /**
     * Structural citation verification: each citation must point to a record
     * that actually exists (quran ayah, hadith, book, article) or carry an
     * explicit external reference. Pointing to nothing → VAL-001.
     */
    private void replaceCitations(ResearchArticle a, List<CitationInput> inputs) {
        citations.findByArticleIdOrderBySortOrderAsc(a.getId())
                .forEach(citations::delete);
        if (inputs == null) return;
        int order = 0;
        for (CitationInput in : inputs) {
            ResearchArticleCitation c = new ResearchArticleCitation();
            c.setArticle(a);
            ResearchArticleCitation.CitationKind kind;
            try {
                kind = ResearchArticleCitation.CitationKind.valueOf(in.citationKind());
            } catch (Exception e) {
                throw ApiException.validation("unknown citation kind: " + in.citationKind());
            }
            c.setCitationKind(kind);
            c.setQuote(in.quote());
            c.setNote(in.note());
            c.setSortOrder(in.sortOrder() == null ? order : in.sortOrder());
            switch (kind) {
                case quran_ayah -> {
                    if (in.surahId() == null || in.ayahNumber() == null) {
                        throw ApiException.validation("quran_ayah citation requires surahId and ayahNumber");
                    }
                    if (quran.ayah(in.surahId(), in.ayahNumber()) == null) {
                        throw ApiException.validation("cited ayah does not exist (surah "
                                + in.surahId() + ":" + in.ayahNumber() + ")");
                    }
                    c.setSurahId(in.surahId());
                    c.setAyahNumber(in.ayahNumber());
                }
                case hadith -> {
                    if (in.hadithId() == null || !hadithExists(in.hadithId())) {
                        throw ApiException.validation("cited hadith does not exist");
                    }
                    c.setHadithId(in.hadithId());
                }
                case book -> {
                    if (in.bookPublicId() == null || !bookExists(in.bookPublicId())) {
                        throw ApiException.validation("cited book does not exist");
                    }
                    c.setBookPublicId(in.bookPublicId());
                }
                case research_article -> {
                    if (in.articlePublicId() == null
                            || articles.findByPublicId(in.articlePublicId()).isEmpty()) {
                        throw ApiException.validation("cited research article does not exist");
                    }
                    c.setArticlePublicId(in.articlePublicId());
                }
                case external -> {
                    if (in.externalRef() == null || in.externalRef().isBlank()) {
                        throw ApiException.validation("external citation requires externalRef");
                    }
                    c.setExternalRef(in.externalRef());
                }
            }
            citations.save(c);
            order++;
        }
    }

    private boolean hadithExists(Long hadithId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM hadiths WHERE id = ?", Long.class, hadithId);
        return n != null && n > 0;
    }

    private boolean bookExists(UUID bookPublicId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE public_id = ? AND status = 'published'",
                Long.class, bookPublicId);
        return n != null && n > 0;
    }

    // ---------------------------------------------------------------- mapping

    public Map<String, Object> citationView(ResearchArticleCitation c) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("citationKind", c.getCitationKind().name());
        m.put("quote", c.getQuote());
        m.put("note", c.getNote());
        m.put("sortOrder", c.getSortOrder());
        switch (c.getCitationKind()) {
            case quran_ayah -> {
                Map<String, Object> ayah = quran.ayah(c.getSurahId(), c.getAyahNumber());
                m.put("surahId", c.getSurahId());
                m.put("ayahNumber", c.getAyahNumber());
                m.put("resolved", ayah == null ? null : Map.of(
                        "surahName", String.valueOf(ayah.getOrDefault("surahName", "")),
                        "textUthmani", String.valueOf(ayah.getOrDefault("textUthmani", ""))));
            }
            case hadith -> {
                List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT h.id AS hadith_id, col.code AS collection_code, h.text_en AS text_en
                        FROM hadiths h JOIN hadith_collections col ON col.id = h.collection_id
                        WHERE h.id = ?""", c.getHadithId());
                m.put("hadithId", c.getHadithId());
                m.put("resolved", rows.isEmpty() ? null : rows.get(0));
            }
            case book -> m.put("bookPublicId", String.valueOf(c.getBookPublicId()));
            case research_article -> m.put("articlePublicId", String.valueOf(c.getArticlePublicId()));
            case external -> m.put("externalRef", c.getExternalRef());
        }
        return m;
    }

    public Map<String, Object> articleView(ResearchArticle a, boolean admin) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("articleId", a.getPublicId());
        m.put("topic", Map.of("slug", a.getTopic().getSlug(), "name", a.getTopic().getName()));
        m.put("title", a.getTitle());
        m.put("titleAr", a.getTitleAr());
        m.put("summary", a.getSummary());
        m.put("body", a.getBody());
        m.put("language", a.getLanguage());
        m.put("authorDisplayName", a.getAuthorDisplayName());
        m.put("sourceTier", a.getSourceTier().name());
        m.put("status", a.getStatus().name());
        m.put("citations", citationsOf(a.getId()).stream().map(this::citationView).toList());
        m.put("updatedAt", String.valueOf(a.getUpdatedAt()));
        if (admin) m.put("publishedAt", String.valueOf(a.getPublishedAt()));
        return m;
    }

    public Map<String, Object> articleRow(ResearchArticle a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("articleId", a.getPublicId());
        m.put("title", a.getTitle());
        m.put("summary", a.getSummary());
        m.put("sourceTier", a.getSourceTier().name());
        m.put("topic", Map.of("slug", a.getTopic().getSlug(), "name", a.getTopic().getName()));
        m.put("language", a.getLanguage());
        m.put("authorDisplayName", a.getAuthorDisplayName());
        m.put("publishedAt", String.valueOf(a.getPublishedAt()));
        return m;
    }

    // ---------------------------------------------------------------- helpers

    private void requireState(ResearchArticle a, ResearchArticle.Status expected, String action) {
        if (a.getStatus() != expected) deny(a, action, "state " + a.getStatus() + " does not permit "
                + action + " (requires " + expected + ")");
    }

    private void requireState(ResearchArticle a, ResearchArticle.Status e1, ResearchArticle.Status e2,
                              String action) {
        if (a.getStatus() != e1 && a.getStatus() != e2) {
            deny(a, action, "state " + a.getStatus() + " does not permit " + action);
        }
    }

    private void requireRoles(SecurityPrincipal actor) {
        List<String> wanted = List.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_CONTENT_MANAGER");
        if (actor.authorities().stream().noneMatch(x -> wanted.contains(x.getAuthority()))) {
            throw ApiException.forbidden("editorial decisions require Super Admin, Admin or Content Manager");
        }
    }

    private void requireScholar(SecurityPrincipal actor, ResearchArticle a, String action) {
        if (actor.authorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_SCHOLAR_REVIEWER"))) {
            deny(a, action, "scholarly approval requires the Scholar Reviewer role");
        }
    }

    private void requirePermission(SecurityPrincipal actor, String permission, String action, ResearchArticle a) {
        if (actor.authorities().stream().noneMatch(x -> x.getAuthority().equals(permission))) {
            deny(a, action, "actor lacks permission " + permission);
        }
    }

    private void deny(ResearchArticle a, String action, String detail) {
        audit.recordDenial("research.workflow.deny", "research_article", a.getId(), action + ": " + detail);
        throw ApiException.workflow(detail);
    }

    private void saveReview(String entityType, Long entityId, ContentReviewRecord.Stage stage,
                            ContentReviewRecord.Decision decision, SecurityPrincipal actor, String notes) {
        ContentReviewRecord row = new ContentReviewRecord();
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setStage(stage);
        row.setReviewerId(actor.id());
        row.setDecision(decision);
        row.setNotes(notes);
        reviews.save(row);
    }

    private Map<String, Object> snapshot(ResearchArticle a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("title", a.getTitle());
        m.put("topic", a.getTopic().getSlug());
        m.put("sourceTier", a.getSourceTier().name());
        m.put("status", a.getStatus().name());
        return m;
    }

    public record Transition(UUID articleId, String before, String after) { }
}
