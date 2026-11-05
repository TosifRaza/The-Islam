package app.ikp.backend.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM ContentVersion v WHERE v.entityType = :type AND v.entityId = :id")
    int maxVersion(@Param("type") String type, @Param("id") Long id);
}
