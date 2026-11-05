package app.ikp.backend.fatwa;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.fatwa.dto.FatwaRequest;
import app.ikp.backend.user.Role;
import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Fatwa workflow service — the non-bypassable publication gate (owner-mandated
 * separation of duties; docs/07-security-plan.md §3, spec §3.17):
 *
 *  1. Scholar Reviewer is the ONLY role that records approve/reject decisions
 *     (permission fatwa.review.approve, granted to scholar_reviewer alone).
 *  2. Publishing (fatwa.publish) requires ALL of:
 *       - state == approved
 *       - a scholarly_review approval record by a user holding the
 *         Scholar Reviewer role
 *       - publisher ≠ approving reviewer (separation of duties)
 *     Permission alone is NEVER sufficient — Admin/Super Admin cannot publish
 *     an unapproved or self-reviewed Fatwa. Every denied attempt is audited
 *     (own transaction) and returns WORKFLOW-001 with nothing published.
 */
@Service
public class FatwaService {

    private final FatwaRepository fatwas;
    private final UserRepository users;
    private final app.ikp.backend.workflow.ContentReviewRecordRepository reviews;
    private final AuditService audit;

    public FatwaService(FatwaRepository fatwas, UserRepository users,
                        app.ikp.backend.workflow.ContentReviewRecordRepository reviews, AuditService audit) {
        this.fatwas = fatwas;
        this.users = users;
        this.reviews = reviews;
        this.audit = audit;
    }

    @Transactional
    public Fatwa create(FatwaRequest request, SecurityPrincipal actor) {
        Fatwa f = new Fatwa();
        f.setQuestion(request.question());
        f.setAnswerDraft(request.answerDraft());
        f.setCategory(request.category());
        f.setLanguage(request.language() == null ? "ar" : request.language());
        f.setSourceRefs(serialize(request.sourceRefs()));
        f.setCreatedBy(users.getReferenceById(actor.id()));
        fatwas.save(f);
        audit.record(actor.id(), "fatwa.create", "fatwa", f.getId(), null, snapshot(f), null);
        return f;
    }

    /** Editing is allowed in draft/in_review; editing after approval resets to draft. */
    @Transactional
    public Fatwa update(UUID publicId, FatwaRequest request, SecurityPrincipal actor) {
        Fatwa f = byPublicId(publicId);
        if (f.getState() == Fatwa.State.published || f.getState() == Fatwa.State.archived) {
            throw ApiException.workflow("published/archived Fatwas are immutable");
        }
        Map<String, Object> before = snapshot(f);
        f.setQuestion(request.question());
        f.setAnswerDraft(request.answerDraft());
        f.setCategory(request.category());
        f.setSourceRefs(serialize(request.sourceRefs()));
        if (f.getState() == Fatwa.State.approved) {
            f.setState(Fatwa.State.draft); // approved edit invalidates the approval
            audit.record(actor.id(), "fatwa.edit_after_approval", "fatwa", f.getId(),
                    before, snapshot(f), "state reset to draft");
        } else {
            audit.record(actor.id(), "fatwa.update", "fatwa", f.getId(), before, snapshot(f), null);
        }
        return f;
    }

    @Transactional
    public Fatwa submitReview(UUID publicId, SecurityPrincipal actor) {
        Fatwa f = byPublicId(publicId);
        requireState(f, Fatwa.State.draft, "submit-review");
        f.setState(Fatwa.State.in_review);
        audit.record(actor.id(), "fatwa.submit_review", "fatwa", f.getId(), null, snapshot(f), null);
        return f;
    }

    /** Assignee must hold the Scholar Reviewer role (spec §3.17 assign-reviewer). */
    @Transactional
    public Fatwa assignReviewer(UUID publicId, UUID reviewerPublicId, SecurityPrincipal actor) {
        Fatwa f = byPublicId(publicId);
        requireState(f, Fatwa.State.in_review, "assign-reviewer");
        User reviewer = users.findByPublicId(reviewerPublicId)
                .orElseThrow(() -> ApiException.validation("reviewer user not found"));
        boolean scholar = reviewer.getRoles().stream().anyMatch(r -> "scholar_reviewer".equals(r.getCode()));
        if (!scholar) {
            audit.recordDenial("fatwa.assign_deny", "fatwa", f.getId(),
                    "assignee does not hold the Scholar Reviewer role");
            throw ApiException.forbidden("assignee must hold the Scholar Reviewer role");
        }
        f.setAssignedReviewerId(reviewer.getId());
        audit.record(actor.id(), "fatwa.assign_reviewer", "fatwa", f.getId(), null, snapshot(f), null);
        return f;
    }

    /** Scholar Reviewer ONLY — sole authority to approve/reject (spec §3.17 decision). */
    @Transactional
    public Fatwa decide(UUID publicId, boolean approve, String notes, SecurityPrincipal actor) {
        Fatwa f = byPublicId(publicId);
        requireState(f, Fatwa.State.in_review, "decision");
        boolean scholar = actor.authorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SCHOLAR_REVIEWER"));
        if (!scholar) {
            // Admin/Super Admin without the role: 403, attempt audited.
            audit.recordDenial("fatwa.decision_deny", "fatwa", f.getId(),
                    "actor without Scholar Reviewer role attempted a review decision");
            throw ApiException.forbidden("only the Scholar Reviewer records review decisions");
        }
        app.ikp.backend.workflow.ContentReviewRecord row = new app.ikp.backend.workflow.ContentReviewRecord();
        row.setEntityType("fatwa");
        row.setEntityId(f.getId());
        row.setStage(app.ikp.backend.workflow.ContentReviewRecord.Stage.scholarly_review);
        row.setReviewerId(actor.id());
        row.setDecision(approve
                ? app.ikp.backend.workflow.ContentReviewRecord.Decision.approved
                : app.ikp.backend.workflow.ContentReviewRecord.Decision.rejected);
        row.setNotes(notes);
        reviews.save(row);
        f.setDecidedBy(actor.id());
        f.setDecidedAt(Instant.now());
        f.setDecisionNotes(notes);
        f.setState(approve ? Fatwa.State.approved : Fatwa.State.draft);
        audit.record(actor.id(), approve ? "fatwa.approved" : "fatwa.rejected", "fatwa", f.getId(),
                null, snapshot(f), notes);
        return f;
    }

    /**
     * Publication gate — SUPER-VALIDATED server-side (state + scholar approval
     * record + publisher ≠ reviewer). No bypass route exists; every violation
     * returns WORKFLOW-001, publishes nothing and is audited.
     */
    @Transactional
    public Fatwa publish(UUID publicId, SecurityPrincipal actor) {
        Fatwa f = byPublicId(publicId);
        // (a) state gate
        if (f.getState() != Fatwa.State.approved) {
            deny(f, actor, "publish refused: Fatwa state is " + f.getState() + ", not approved");
        }
        // (b) recorded Scholar Reviewer approval by a user HOLDING the role
        var approvals = reviews.findByEntityTypeAndEntityIdOrderByDecidedAtDesc("fatwa", f.getId()).stream()
                .filter(r -> r.getStage() == app.ikp.backend.workflow.ContentReviewRecord.Stage.scholarly_review)
                .filter(r -> r.getDecision() == app.ikp.backend.workflow.ContentReviewRecord.Decision.approved)
                .toList();
        if (approvals.isEmpty()) {
            deny(f, actor, "publish refused: no recorded Scholar Reviewer approval");
        }
        app.ikp.backend.workflow.ContentReviewRecord approval = approvals.get(0);
        User approver = users.findById(approval.getReviewerId()).orElse(null);
        boolean approverIsScholar = approver != null && approver.getRoles().stream()
                .anyMatch(r -> "scholar_reviewer".equals(r.getCode()));
        if (!approverIsScholar) {
            deny(f, actor, "publish refused: recorded approval was not made by a Scholar Reviewer");
        }
        // (c) separation of duties: publisher ≠ approving reviewer
        if (approver != null && approver.getId().equals(actor.id())) {
            deny(f, actor, "publish refused: the publisher must be different from the approving reviewer");
        }
        f.setState(Fatwa.State.published);
        f.setAnswer(f.getAnswerDraft());
        f.setPublishedBy(actor.id());
        f.setPublishedAt(Instant.now());
        audit.record(actor.id(), "fatwa.publish", "fatwa", f.getId(), null, snapshot(f), null);
        return f;
    }

    @Transactional
    public Fatwa unpublish(UUID publicId, SecurityPrincipal actor) {
        Fatwa f = byPublicId(publicId);
        requireState(f, Fatwa.State.published, "unpublish");
        f.setState(Fatwa.State.archived);
        audit.record(actor.id(), "fatwa.unpublish", "fatwa", f.getId(), null, snapshot(f), null);
        return f;
    }

    @Transactional(readOnly = true)
    public Fatwa byPublicId(UUID publicId) {
        return fatwas.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("fatwa not found"));
    }

    @Transactional(readOnly = true)
    public java.util.List<Fatwa> byState(Fatwa.State state) {
        return state == null ? fatwas.findAll() : fatwas.findByState(state);
    }

    private void requireState(Fatwa f, Fatwa.State expected, String action) {
        if (f.getState() != expected) {
            deny(f, null, "state " + f.getState() + " does not permit " + action + " (requires " + expected + ")");
        }
    }

    private void deny(Fatwa f, SecurityPrincipal actor, String detail) {
        audit.recordDenial("fatwa.publish_deny", "fatwa", f.getId(), detail);
        throw ApiException.workflow(detail);
    }

    static Map<String, Object> snapshot(Fatwa f) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("state", f.getState().name());
        m.put("category", f.getCategory());
        m.put("language", f.getLanguage());
        m.put("assignedReviewerId", f.getAssignedReviewerId());
        m.put("decidedBy", f.getDecidedBy());
        m.put("publishedBy", f.getPublishedBy());
        return m;
    }

    private static String serialize(java.util.List<String> refs) {
        if (refs == null || refs.isEmpty()) return "[]";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(refs);
        } catch (Exception e) {
            return "[]";
        }
    }
}
