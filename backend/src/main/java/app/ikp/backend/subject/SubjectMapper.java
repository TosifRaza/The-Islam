package app.ikp.backend.subject;

import app.ikp.backend.subject.dto.SubjectView;
import org.springframework.stereotype.Component;

/** Stable slug identifiers only — internal BIGINT ids are never exposed. */
@Component
public class SubjectMapper {

    public SubjectView toView(Subject s) {
        return new SubjectView(s.getSlug(), s.getSlug(), s.getName(), s.getNameAr(),
                s.getDescription(), s.getImage(), s.getSortOrder(), s.getStatus(), s.getUpdatedAt());
    }
}
