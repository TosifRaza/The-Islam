package com.ikp.dev.app.domain.model;

import java.util.List;

/**
 * Domain models — the app works with these, never with wire DTOs or Room
 * entities directly (clean repository boundary).
 */
public final class Models {

    private Models() {
    }

    /** Authenticated user (from /auth/me or login response). */
    public static final class User {
        public final String userId;
        public final String email;
        public final String displayName;
        public final String language;
        public final String status;
        public final List<String> roles;
        public final List<String> permissions;
        public final String createdAt;

        public User(String userId, String email, String displayName, String language,
                    String status, List<String> roles, List<String> permissions, String createdAt) {
            this.userId = userId;
            this.email = email;
            this.displayName = displayName;
            this.language = language;
            this.status = status;
            this.roles = roles == null ? java.util.Collections.<String>emptyList() : roles;
            this.permissions = permissions == null ? java.util.Collections.<String>emptyList() : permissions;
            this.createdAt = createdAt;
        }
    }

    /** Library category (stable id = slug, CAT-*; position 06 never arrives). */
    public static final class Subject {
        public final String slug;
        public final String name;
        public final String nameAr;
        public final String description;
        public final Integer position;

        public Subject(String slug, String name, String nameAr, String description, Integer position) {
            this.slug = slug;
            this.name = name;
            this.nameAr = nameAr;
            this.description = description;
            this.position = position;
        }
    }

    /** Book list row. */
    public static final class Book {
        public final String bookId;
        public final String title;
        public final String subtitle;
        public final String language;
        public final String subjectSlug;
        public final String subjectName;
        public final List<String> authors;
        public final List<String> formats;
        public final boolean downloadAllowed;

        public Book(String bookId, String title, String subtitle, String language,
                    String subjectSlug, String subjectName, List<String> authors,
                    List<String> formats, boolean downloadAllowed) {
            this.bookId = bookId;
            this.title = title;
            this.subtitle = subtitle;
            this.language = language;
            this.subjectSlug = subjectSlug;
            this.subjectName = subjectName;
            this.authors = authors == null ? java.util.Collections.<String>emptyList() : authors;
            this.formats = formats == null ? java.util.Collections.<String>emptyList() : formats;
            this.downloadAllowed = downloadAllowed;
        }
    }

    /** Book detail. */
    public static final class BookDetail {
        public final String bookId;
        public final String title;
        public final String subtitle;
        public final String description;
        public final String language;
        public final String subjectSlug;
        public final String subjectName;
        public final List<String> authors;
        public final List<String> formats;
        public final String publisher;
        public final String edition;
        public final Integer publicationYear;
        public final Integer pageCount;
        public final String copyrightInfo;
        public final boolean downloadAllowed;

        public BookDetail(String bookId, String title, String subtitle, String description,
                          String language, String subjectSlug, String subjectName,
                          List<String> authors, List<String> formats, String publisher,
                          String edition, Integer publicationYear, Integer pageCount,
                          String copyrightInfo, boolean downloadAllowed) {
            this.bookId = bookId;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.language = language;
            this.subjectSlug = subjectSlug;
            this.subjectName = subjectName;
            this.authors = authors == null ? java.util.Collections.<String>emptyList() : authors;
            this.formats = formats == null ? java.util.Collections.<String>emptyList() : formats;
            this.publisher = publisher;
            this.edition = edition;
            this.publicationYear = publicationYear;
            this.pageCount = pageCount;
            this.copyrightInfo = copyrightInfo;
            this.downloadAllowed = downloadAllowed;
        }
    }

    /** Active session (device) entry. */
    public static final class SessionInfo {
        public final long sessionId;
        public final String deviceName;
        public final String createdAt;
        public final String expiresAt;
        public final boolean current;

        public SessionInfo(long sessionId, String deviceName, String createdAt,
                           String expiresAt, boolean current) {
            this.sessionId = sessionId;
            this.deviceName = deviceName;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.current = current;
        }
    }

    /** Simple page result for cursor/offset lists. */
    public static final class Page<T> {
        public final List<T> items;
        @androidx.annotation.Nullable
        public final String nextCursor;

        public Page(List<T> items, @androidx.annotation.Nullable String nextCursor) {
            this.items = items == null ? java.util.Collections.<T>emptyList() : items;
            this.nextCursor = nextCursor;
        }

        public boolean hasNext() {
            return nextCursor != null && !nextCursor.isEmpty();
        }
    }
}
