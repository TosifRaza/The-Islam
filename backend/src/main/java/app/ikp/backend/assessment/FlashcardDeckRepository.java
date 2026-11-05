package app.ikp.backend.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    Optional<FlashcardDeck> findByPublicId(UUID publicId);

    /** Public catalogue: admin decks only, publish state gated. */
    Page<FlashcardDeck> findByOwnerTypeAndPublishStateOrderByUpdatedAtDesc(
            FlashcardDeck.OwnerType ownerType, FlashcardDeck.PublishState state, Pageable pageable);

    /** Personal decks: strictly owner-scoped. */
    List<FlashcardDeck> findByOwnerTypeAndOwnerUserIdOrderByUpdatedAtDesc(FlashcardDeck.OwnerType ownerType, Long ownerUserId);

    Page<FlashcardDeck> findByOwnerTypeOrderByUpdatedAtDesc(FlashcardDeck.OwnerType ownerType, Pageable pageable);

    long countByOwnerTypeAndPublishState(FlashcardDeck.OwnerType ownerType, FlashcardDeck.PublishState state);
}
