package app.ikp.backend.book;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Book ↔ author join with explicit display order (never used as a key
 * elsewhere). The table's primary key is composite (book_id, author_id) —
 * modelled with an @IdClass; no surrogate id column exists.
 */
@Entity
@Table(name = "book_authors")
@IdClass(BookAuthor.PK.class)
public class BookAuthor {

    public static class PK implements Serializable {
        private Long book;
        private Long author;

        public PK() {
        }

        public PK(Long book, Long author) {
            this.book = book;
            this.author = author;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return java.util.Objects.equals(book, pk.book) && java.util.Objects.equals(author, pk.author);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(book, author);
        }
    }

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private Author author;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 1;

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
