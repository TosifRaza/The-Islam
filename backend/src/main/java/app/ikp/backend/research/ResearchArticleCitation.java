package app.ikp.backend.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Article citation. The referenced record is validated in ResearchService at
 * save time: quran_ayah must exist in quran_ayahs, hadith in hadiths, book in
 * books, research_article in research_articles. A citation that points to
 * nothing is rejected — citations are never silently stored.
 */
@Entity
@Table(name = "research_article_citations")
public class ResearchArticleCitation {

    public enum CitationKind { quran_ayah, hadith, book, research_article, external }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private ResearchArticle article;

    @Enumerated(EnumType.STRING)
    @Column(name = "citation_kind", nullable = false, length = 20)
    private CitationKind citationKind = CitationKind.external;

    @Column(name = "surah_id")
    private Integer surahId;

    @Column(name = "ayah_number")
    private Integer ayahNumber;

    @Column(name = "hadith_id")
    private Long hadithId;

    @Column(name = "book_public_id")
    private UUID bookPublicId;

    @Column(name = "article_public_id")
    private UUID articlePublicId;

    @Column(name = "external_ref", length = 500)
    private String externalRef;

    @Column(columnDefinition = "text")
    private String quote;

    @Column(length = 500)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Long getId() { return id; }
    public ResearchArticle getArticle() { return article; }
    public void setArticle(ResearchArticle article) { this.article = article; }
    public CitationKind getCitationKind() { return citationKind; }
    public void setCitationKind(CitationKind citationKind) { this.citationKind = citationKind; }
    public Integer getSurahId() { return surahId; }
    public void setSurahId(Integer surahId) { this.surahId = surahId; }
    public Integer getAyahNumber() { return ayahNumber; }
    public void setAyahNumber(Integer ayahNumber) { this.ayahNumber = ayahNumber; }
    public Long getHadithId() { return hadithId; }
    public void setHadithId(Long hadithId) { this.hadithId = hadithId; }
    public UUID getBookPublicId() { return bookPublicId; }
    public void setBookPublicId(UUID bookPublicId) { this.bookPublicId = bookPublicId; }
    public UUID getArticlePublicId() { return articlePublicId; }
    public void setArticlePublicId(UUID articlePublicId) { this.articlePublicId = articlePublicId; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getQuote() { return quote; }
    public void setQuote(String quote) { this.quote = quote; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
