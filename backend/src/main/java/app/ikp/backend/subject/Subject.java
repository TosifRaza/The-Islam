package app.ikp.backend.subject;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Library category / subject (M1 V2 spine). The slug is the stable join key;
 * sort_order is the client-reference DISPLAY POSITION only (1–15) and is never
 * a relationship key. Position 06 (cat_unconfirmed_06) is hidden_unconfirmed
 * until the client confirms it — never renamed, never renumbered (OD-03).
 */
@Entity
@Table(name = "subjects")
public class Subject {

    public static final String HIDDEN_UNCONFIRMED = "hidden_unconfirmed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_ar", length = 120)
    private String nameAr;

    @Column(name = "sort_order", nullable = false, unique = true)
    private int sortOrder;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 255)
    private String image;

    @Column(columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public boolean isHiddenUnconfirmed() {
        return HIDDEN_UNCONFIRMED.equals(status);
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
