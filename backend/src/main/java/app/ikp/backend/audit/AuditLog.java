package app.ikp.backend.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Append-only administrative audit row (M1 V2 schema). Ordinary administration
 * has no update/delete path: no service method mutates rows and the entity
 * exposes no setters for written fields — the audit trail is write-only by API
 * and by service design (production DB roles additionally hold no UPDATE/DELETE
 * grants, docs/04-database-design.md §6).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    // Package-private write-once fields: only the audit package inserts rows;
    // no update/delete path exists (append-only by design).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "actor_id")
    Long actorId;

    @Column(nullable = false, length = 60)
    String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    String entityType;

    @Column(name = "entity_id")
    Long entityId;

    @Column(name = "before_hash", length = 64)
    String beforeHash;

    @Column(name = "after_hash", length = 64)
    String afterHash;

    // PostgreSQL inet column: the JDBC type code makes Hibernate bind the
    // Java String as an inet parameter (plain varchar binding is rejected).
    @JdbcTypeCode(SqlTypes.INET)
    @Column(columnDefinition = "inet")
    String ip;

    @Column(name = "user_agent", length = 300)
    String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getBeforeHash() { return beforeHash; }
    public String getAfterHash() { return afterHash; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
