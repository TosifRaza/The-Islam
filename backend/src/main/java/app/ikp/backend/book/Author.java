package app.ikp.backend.book;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "authors")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "name_ar", length = 200)
    private String nameAr;

    @Column(columnDefinition = "text")
    private String bio;

    @CreationTimestamp
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (publicId == null) publicId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Instant getCreatedAt() { return createdAt; }
}
