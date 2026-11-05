package app.ikp.backend.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentReviewRecordRepository extends JpaRepository<ContentReviewRecord, Long> {

    List<ContentReviewRecord> findByEntityTypeAndEntityIdOrderByDecidedAtDesc(String entityType, Long entityId);

    List<ContentReviewRecord> findByEntityTypeAndDecisionOrderByDecidedAtDesc(String entityType,
                                                                              ContentReviewRecord.Decision decision);
}
