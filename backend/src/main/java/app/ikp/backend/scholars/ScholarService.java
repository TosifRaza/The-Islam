package app.ikp.backend.scholars;

import app.ikp.backend.audit.AuditService;
import app.ikp.backend.auth.SecurityPrincipal;
import app.ikp.backend.common.ApiException;
import app.ikp.backend.storage.MediaAssetRepository;
import app.ikp.backend.user.UserRepository;
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
 * Scholar directory service (M5 spec Module 14). Qualifications are
 * admin-recorded facts — the platform never generates them. Verification
 * transitions are restricted to scholars.manage holders and audited.
 */
@Service
public class ScholarService {

    private final ScholarRepository scholars;
    private final MediaAssetRepository media;
    private final UserRepository users;
    private final ContentReviewRecordRepository reviews;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ScholarService(ScholarRepository scholars, MediaAssetRepository media, UserRepository users,
                          ContentReviewRecordRepository reviews, JdbcTemplate jdbc, AuditService audit) {
        this.scholars = scholars;
        this.media = media;
        this.users = users;
        this.reviews = reviews;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public Scholar create(String name, String nameAr, String biography, String qualifications,
                          String areasOfExpertise, java.util.UUID photoMediaId, java.util.UUID linkedUserPublicId,
                          SecurityPrincipal actor) {
        Scholar s = new Scholar();
        apply(s, name, nameAr, biography, qualifications, areasOfExpertise, photoMediaId);
        if (linkedUserPublicId != null) {
            s.setLinkedUser(users.findByPublicId(linkedUserPublicId)
                    .orElseThrow(() -> ApiException.validation("linked user not found")));
        }
        s.setCreatedBy(users.getReferenceById(actor.id()));
        scholars.save(s);
        audit.record(actor.id(), "scholars.create", "scholar", s.getId(), null, snapshot(s), null);
        return s;
    }

    @Transactional
    public Scholar update(UUID publicId, String name, String nameAr, String biography, String qualifications,
                          String areasOfExpertise, java.util.UUID photoMediaId, SecurityPrincipal actor) {
        Scholar s = scholar(publicId);
        apply(s, name, nameAr, biography, qualifications, areasOfExpertise, photoMediaId);
        audit.record(actor.id(), "scholars.update", "scholar", s.getId(), null, snapshot(s), null);
        return s;
    }

    /** Verification: unverified → verified / verified|unverified → revoked. Audited with actor identity. */
    @Transactional
    public Scholar verify(UUID publicId, String status, SecurityPrincipal actor) {
        Scholar s = scholar(publicId);
        Scholar.VerificationStatus next;
        try {
            next = Scholar.VerificationStatus.valueOf(status);
        } catch (Exception e) {
            throw ApiException.validation("status must be verified or revoked");
        }
        if (next == Scholar.VerificationStatus.unverified) {
            throw ApiException.validation("unverified is the initial state, not a transition target");
        }
        Scholar.VerificationStatus before = s.getVerificationStatus();
        s.setVerificationStatus(next);
        if (next == Scholar.VerificationStatus.verified) {
            s.setVerifiedBy(users.getReferenceById(actor.id()));
            s.setVerifiedAt(Instant.now());
        } else {
            s.setVerifiedBy(null);
            s.setVerifiedAt(null);
        }
        audit.record(actor.id(), "scholars.verify." + next, "scholar", s.getId(),
                Map.of("verificationStatus", before.name()), snapshot(s), null);
        return s;
    }

    @Transactional(readOnly = true)
    public Scholar scholar(UUID publicId) {
        return scholars.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("scholar not found"));
    }

    @Transactional(readOnly = true)
    public Page<Scholar> adminList(int page, int size) {
        return scholars.findAllByOrderByUpdatedAtDesc(
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public Page<Scholar> publicList(int page, int size) {
        return scholars.findByVerificationStatusOrderByUpdatedAtDesc(Scholar.VerificationStatus.verified,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100)));
    }

    /** Published fatwas the scholar reviewed + published research articles reviewed (audit-trail derived). */
    @Transactional(readOnly = true)
    public Map<String, Object> reviewHistory(Scholar s) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (s.getLinkedUser() == null) {
            m.put("publishedFatwasReviewed", 0);
            m.put("publishedArticlesReviewed", 0);
            m.put("note", "scholar profile is not linked to a platform account; review history is unavailable");
            return m;
        }
        Long uid = s.getLinkedUser().getId();
        Long fatwas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM content_review_records r
                JOIN fatwas f ON f.id = r.entity_id
                WHERE r.entity_type = 'fatwa' AND r.reviewer_id = ?
                  AND r.decision = 'approved' AND f.state = 'published'
                """, Long.class, uid);
        Long articles = jdbc.queryForObject("""
                SELECT COUNT(*) FROM content_review_records r
                JOIN research_articles a ON a.id = r.entity_id
                WHERE r.entity_type = 'research_article' AND r.reviewer_id = ?
                  AND r.decision = 'approved' AND a.status = 'published'
                """, Long.class, uid);
        m.put("publishedFatwasReviewed", fatwas == null ? 0 : fatwas);
        m.put("publishedArticlesReviewed", articles == null ? 0 : articles);
        return m;
    }

    public Map<String, Object> view(Scholar s, boolean admin) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("scholarId", s.getPublicId());
        m.put("name", s.getName());
        m.put("nameAr", s.getNameAr());
        m.put("biography", s.getBiography());
        m.put("qualifications", s.getQualifications());
        m.put("areasOfExpertise", s.getAreasOfExpertise());
        m.put("verificationStatus", s.getVerificationStatus().name());
        if (s.getPhoto() != null) m.put("photoPath", "/api/v1/files/" + s.getPhoto().getPublicId());
        if (admin) {
            m.put("verifiedBy", s.getVerifiedBy() == null ? null : String.valueOf(s.getVerifiedBy().getId()));
            m.put("verifiedAt", s.getVerifiedAt() == null ? null : String.valueOf(s.getVerifiedAt()));
        }
        return m;
    }

    private void apply(Scholar s, String name, String nameAr, String biography, String qualifications,
                       String areasOfExpertise, java.util.UUID photoMediaId) {
        s.setName(name);
        s.setNameAr(nameAr);
        s.setBiography(biography);
        s.setQualifications(qualifications);
        s.setAreasOfExpertise(areasOfExpertise);
        if (photoMediaId != null) {
            s.setPhoto(media.findByPublicId(photoMediaId)
                    .orElseThrow(() -> ApiException.validation("photo media asset not found")));
        }
    }

    private static Map<String, Object> snapshot(Scholar s) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("verificationStatus", s.getVerificationStatus().name());
        return m;
    }
}
