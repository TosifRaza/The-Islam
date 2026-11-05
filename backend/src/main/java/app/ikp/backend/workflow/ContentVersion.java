package app.ikp.backend.workflow;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Immutable metadata snapshot on publication (version history "where appropriate"). */
@Entity
@Table(name = "content_versions")
public class ContentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private int version;

    // jsonb column — the JDBC type code makes Hibernate bind the String as a
    // jsonb parameter (a plain varchar bind is rejected by PostgreSQL).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "changed_by")
    private Long changedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
