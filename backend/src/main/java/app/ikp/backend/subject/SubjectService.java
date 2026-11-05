package app.ikp.backend.subject;

import app.ikp.backend.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Subject management (approved spec §3.4). The category set is the fixed
 * 15-position client reference (M0/M1 approved invariant, guarded by the
 * repeatable seed): administrators manage metadata, images, display order and
 * visibility of the 15 positions. Creating a 16th category or deleting one is
 * intentionally refused (WORKFLOW-001) — relaxing the approved "exactly 15
 * positions" invariant needs an owner decision plus a new forward migration;
 * no existing migration is modified here. Renaming position 06 or changing its
 * visibility is forbidden until the client confirms it (OD-03).
 */
@Service
public class SubjectService {

    private final SubjectRepository subjects;

    public SubjectService(SubjectRepository subjects) {
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public List<Subject> allForAdmin() {
        return subjects.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Subject> publishedForPublic() {
        // hidden_unconfirmed is NEVER returned publicly (OD-03); archived is
        // unpublished. Hidden slugs stay indistinguishable from absent (§3.4.2).
        return subjects.findByStatusNotOrderBySortOrderAsc(Subject.HIDDEN_UNCONFIRMED).stream()
                .filter(s -> !"archived".equals(s.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Subject bySlugOr404(String slug) {
        Subject s = subjects.findBySlug(slug).orElse(null);
        if (s == null || s.isHiddenUnconfirmed() || "archived".equals(s.getStatus())) {
            throw ApiException.notFound("subject not found");
        }
        return s;
    }

    /** Admin lookups include hidden/reserved subjects (spec §3.4.9). */
    @Transactional(readOnly = true)
    public Subject bySlugOr404Admin(String slug) {
        return subjects.findBySlug(slug).orElseThrow(() -> ApiException.notFound("subject not found"));
    }

    @Transactional
    public Subject update(String slug, Map<String, Object> patch) {
        Subject s = subjects.findBySlug(slug).orElseThrow(() -> ApiException.notFound("subject not found"));
        if (patch.containsKey("name")) {
            String name = String.valueOf(patch.get("name"));
            if (name.isBlank() || name.length() > 120) {
                throw ApiException.validation("name must be 1–120 characters");
            }
            if (s.isHiddenUnconfirmed() && !name.equals(s.getName())) {
                throw ApiException.workflow("the unidentified category 06 cannot be renamed until the client confirms it (OD-03)");
            }
            s.setName(name);
        }
        if (patch.containsKey("nameAr")) {
            Object nameAr = patch.get("nameAr");
            s.setNameAr(nameAr == null ? null : String.valueOf(nameAr));
        }
        if (patch.containsKey("description")) {
            Object d = patch.get("description");
            String desc = d == null ? null : String.valueOf(d);
            if (desc != null && desc.length() > 1000) {
                throw ApiException.validation("description must be ≤ 1000 characters");
            }
            s.setDescription(desc);
        }
        if (patch.containsKey("image")) {
            Object img = patch.get("image");
            s.setImage(img == null ? null : String.valueOf(img));
        }
        if (patch.containsKey("status")) {
            String status = String.valueOf(patch.get("status"));
            if (!List.of("active", "archived").contains(status)) {
                throw ApiException.validation("status must be active or archived");
            }
            if (s.isHiddenUnconfirmed()) {
                throw ApiException.workflow("the reserved category 06 visibility is fixed until the client confirms it (OD-03)");
            }
            s.setStatus(status);
        }
        return s;
    }

    /** Display-order update — complete set 1..15 required (spec §3.4.5). */
    @Transactional
    public int reorder(List<Map<String, Object>> order) {
        if (order == null || order.size() != 15) {
            throw ApiException.validation("order must contain the complete set of 15 subjects");
        }
        int[] positions = order.stream().mapToInt(o -> {
            Object p = o.get("position");
            if (!(p instanceof Number n)) throw ApiException.validation("position must be an integer");
            return n.intValue();
        }).sorted().toArray();
        for (int i = 0; i < 15; i++) {
            if (positions[i] != i + 1) {
                throw ApiException.validation("positions must be exactly 1..15 without gaps or duplicates");
            }
        }
        int updated = 0;
        // sort_order's UNIQUE constraint is DEFERRABLE (V4) so a permutation
        // inside 1..15 can be applied in any order and is validated at commit.
        for (Map<String, Object> entry : order) {
            Object slugValue = entry.get("subjectId");
            Object posValue = entry.get("position");
            if (slugValue == null || posValue == null) {
                throw ApiException.validation("order entries need subjectId and position");
            }
            Subject s = subjects.findBySlug(String.valueOf(slugValue))
                    .orElseThrow(() -> ApiException.validation("unknown subjectId in order list"));
            int position = ((Number) posValue).intValue();
            if (s.getSortOrder() != position) {
                s.setSortOrder(position); // display order only — never a join key
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public Subject publish(String slug) {
        Subject s = requireManaged(slug);
        s.setStatus("active");
        return s;
    }

    @Transactional
    public Subject unpublish(String slug) {
        Subject s = requireManaged(slug);
        s.setStatus("archived");
        return s;
    }

    private Subject requireManaged(String slug) {
        Subject s = subjects.findBySlug(slug).orElseThrow(() -> ApiException.notFound("subject not found"));
        if (s.isHiddenUnconfirmed()) {
            throw ApiException.workflow("the reserved category 06 is managed by the owner decision OD-03 only");
        }
        return s;
    }
}
