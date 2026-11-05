package com.ikp.dev.app.data.mapper;

import com.ikp.dev.app.data.remote.dto.Dtos;
import com.ikp.dev.app.data.remote.dto.LibraryDtos;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO → domain mappers. Pure Java (JVM-testable). Null-tolerant: wire
 * additions must never crash the app.
 */
public final class Mappers {

    private Mappers() {
    }

    public static Models.User toUser(Dtos.MeResponseDto dto) {
        if (dto == null) {
            return null;
        }
        return new Models.User(dto.userId, dto.email, dto.displayName, dto.language,
                dto.status, dto.roles, dto.permissions, dto.createdAt);
    }

    public static Models.User toUser(Dtos.TokenResponseDto token) {
        if (token == null || token.user == null) {
            return null;
        }
        // Login response carries a reduced user; email/language/status unknown
        // until /auth/me — represented as null, never fabricated.
        return new Models.User(token.user.userId, null, token.user.displayName,
                null, null, token.user.roles, null, null);
    }

    public static Models.Subject toSubject(LibraryDtos.SubjectDto dto) {
        if (dto == null || dto.slug == null) {
            return null;
        }
        return new Models.Subject(dto.slug, dto.name, dto.nameAr, dto.description, dto.position);
    }

    public static List<Models.Subject> toSubjects(List<LibraryDtos.SubjectDto> dtos) {
        List<Models.Subject> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }
        for (LibraryDtos.SubjectDto d : dtos) {
            Models.Subject s = toSubject(d);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    public static Models.Book toBook(LibraryDtos.BookRowDto dto) {
        if (dto == null || dto.bookId == null) {
            return null;
        }
        return new Models.Book(dto.bookId, dto.title, dto.subtitle, dto.language,
                dto.subject == null ? null : dto.subject.slug,
                dto.subject == null ? null : dto.subject.name,
                names(dto.authors), dto.formats,
                Boolean.TRUE.equals(dto.downloadAllowed));
    }

    public static List<Models.Book> toBooks(List<LibraryDtos.BookRowDto> dtos) {
        List<Models.Book> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }
        for (LibraryDtos.BookRowDto d : dtos) {
            Models.Book b = toBook(d);
            if (b != null) {
                out.add(b);
            }
        }
        return out;
    }

    public static Models.BookDetail toBookDetail(LibraryDtos.BookDetailDto dto) {
        if (dto == null || dto.bookId == null) {
            return null;
        }
        return new Models.BookDetail(dto.bookId, dto.title, dto.subtitle, dto.description,
                dto.language,
                dto.subject == null ? null : dto.subject.slug,
                dto.subject == null ? null : dto.subject.name,
                names(dto.authors), dto.formats, dto.publisher, dto.edition,
                dto.publicationYear, dto.pageCount, dto.copyrightInfo,
                Boolean.TRUE.equals(dto.downloadAllowed));
    }

    public static Models.SessionInfo toSession(Dtos.SessionDto dto) {
        if (dto == null || dto.sessionId == null) {
            return null;
        }
        return new Models.SessionInfo(dto.sessionId, dto.deviceName, dto.createdAt,
                dto.expiresAt, Boolean.TRUE.equals(dto.current));
    }

    public static List<Models.SessionInfo> toSessions(List<Dtos.SessionDto> dtos) {
        List<Models.SessionInfo> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }
        for (Dtos.SessionDto d : dtos) {
            Models.SessionInfo s = toSession(d);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<String> names(List<LibraryDtos.AuthorRefDto> authors) {
        List<String> out = new ArrayList<>();
        if (authors == null) {
            return out;
        }
        for (LibraryDtos.AuthorRefDto a : authors) {
            if (a != null && a.name != null) {
                out.add(a.name);
            }
        }
        return out;
    }
}
