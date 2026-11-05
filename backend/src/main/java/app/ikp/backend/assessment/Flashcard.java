package app.ikp.backend.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One flashcard (front/back). Content is admin- or user-authored; never generated. */
@Entity
@Table(name = "flashcards")
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private FlashcardDeck deck;

    @Column(name = "front_text", nullable = false, columnDefinition = "text")
    private String frontText;

    @Column(name = "back_text", nullable = false, columnDefinition = "text")
    private String backText;

    @Column(length = 500)
    private String hint;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Long getId() { return id; }
    public FlashcardDeck getDeck() { return deck; }
    public void setDeck(FlashcardDeck deck) { this.deck = deck; }
    public String getFrontText() { return frontText; }
    public void setFrontText(String frontText) { this.frontText = frontText; }
    public String getBackText() { return backText; }
    public void setBackText(String backText) { this.backText = backText; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
