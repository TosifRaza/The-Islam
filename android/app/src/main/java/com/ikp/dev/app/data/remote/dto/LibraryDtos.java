package com.ikp.dev.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Wire DTOs for the public library endpoints (docs/05 §3.4/§3.5). */
public final class LibraryDtos {

    private LibraryDtos() {
    }

    /** GET /subjects row. Stable public id = slug (CAT-*); position 06 is
     *  never returned by the backend (OD-03 enforced server-side). */
    public static final class SubjectDto {
        @SerializedName(value = "subjectId", alternate = {"subject_id", "id"})
        public String subjectId;
        public String slug;
        public String name;
        @SerializedName(value = "nameAr", alternate = "name_ar")
        public String nameAr;
        public String description;
        @SerializedName(value = "imageUrl", alternate = "image_url")
        public String imageUrl;
        @SerializedName(value = "position", alternate = {"sort_order", "sortOrder"})
        public Integer position;
        public String status;
        @SerializedName(value = "updatedAt", alternate = "updated_at")
        public String updatedAt;
    }

    /** GET /library/books row (published only). */
    public static final class BookRowDto {
        @SerializedName(value = "bookId", alternate = "book_id")
        public String bookId;
        public String title;
        public String subtitle;
        public String language;
        public SubjectRefDto subject;
        public List<AuthorRefDto> authors;
        public List<String> formats;
        @SerializedName(value = "downloadAllowed", alternate = "download_allowed")
        public Boolean downloadAllowed;
        @SerializedName(value = "publishedAt", alternate = "published_at")
        public String publishedAt;
        /** M4: per-format file refs for the reader download-token flow. */
        public List<BookFileRefDto> files;
    }

    public static final class BookFileRefDto {
        @SerializedName(value = "fileId", alternate = "file_id")
        public String fileId;
        public String format;
        @SerializedName(value = "downloadAllowed", alternate = "download_allowed")
        public Boolean downloadAllowed;
        @SerializedName(value = "ocrAvailable", alternate = "ocr_available")
        public Boolean ocrAvailable;
    }

    public static final class SubjectRefDto {
        public String slug;
        public String name;
    }

    public static final class AuthorRefDto {
        @SerializedName(value = "authorId", alternate = "author_id")
        public String authorId;
        public String name;
    }

    /** GET /library/books/{id} detail payload. NOTE: file UUIDs are NOT part
     *  of the public contract — only format names (backend gap recorded in
     *  the M3 API integration report). */
    public static final class BookDetailDto {
        @SerializedName(value = "bookId", alternate = "book_id")
        public String bookId;
        public String title;
        public String subtitle;
        public String description;
        public String language;
        public SubjectRefDto subject;
        public List<AuthorRefDto> authors;
        public String publisher;
        public String edition;
        public Integer publicationYear;
        public Integer pageCount;
        public String copyrightInfo;
        @SerializedName(value = "downloadAllowed", alternate = "download_allowed")
        public Boolean downloadAllowed;
        public List<String> formats;
        @SerializedName(value = "publishedAt", alternate = "published_at")
        public String publishedAt;
        /** M4: per-format file refs for the reader download-token flow. */
        public List<BookFileRefDto> files;
    }

    

    /** POST /library/books/{id}/download-token response. */
    public static final class DownloadTokenDto {
        public String url;
        @SerializedName(value = "expiresAt", alternate = "expires_at")
        public String expiresAt;
        @SerializedName(value = "sizeBytes", alternate = "size_bytes")
        public Long sizeBytes;
        public String sha256;
    }
}
