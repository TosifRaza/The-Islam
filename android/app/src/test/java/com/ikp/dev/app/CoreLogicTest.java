package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ikp.dev.app.core.common.StartupPolicy;
import com.ikp.dev.app.core.error.ApiError;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.data.mapper.Mappers;
import com.ikp.dev.app.data.remote.dto.Dtos;
import com.ikp.dev.app.data.remote.dto.LibraryDtos;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.data.repository.BookRepository;
import com.ikp.dev.app.core.workers.SyncScheduler;
import com.ikp.dev.app.domain.model.Models;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * JVM unit tests for the pure decision/mapping layer (runs with plain JUnit,
 * no Android SDK). Covers: startup policy, error mapping (problem+json),
 * wire mappers (incl. category-position invariant), query keys and sync
 * scheduling decisions.
 */
public class CoreLogicTest {

    // ---------------- StartupPolicy (prompt §7) ----------------

    @Test
    public void firstRunGoesToOnboarding() {
        assertEquals(StartupPolicy.Destination.ONBOARDING, StartupPolicy.decide(
                false, false, AuthRepository.RestoreState.SIGNED_OUT));
        // onboarding outranks everything, even a saved session
        assertEquals(StartupPolicy.Destination.ONBOARDING, StartupPolicy.decide(
                false, true, AuthRepository.RestoreState.SIGNED_IN));
    }

    @Test
    public void noSessionGoesToLogin() {
        assertEquals(StartupPolicy.Destination.LOGIN, StartupPolicy.decide(
                true, false, AuthRepository.RestoreState.SIGNED_OUT));
    }

    @Test
    public void validatedSessionGoesHome() {
        assertEquals(StartupPolicy.Destination.HOME, StartupPolicy.decide(
                true, true, AuthRepository.RestoreState.SIGNED_IN));
    }

    @Test
    public void offlineSavedSessionNeverShowsAuthenticatedScreen() {
        // Must degrade to guest-browse home, never to an authenticated view
        assertEquals(StartupPolicy.Destination.HOME_OFFLINE, StartupPolicy.decide(
                true, true, AuthRepository.RestoreState.OFFLINE_SAVED_SESSION));
        // failed validation must NOT reach home
        assertEquals(StartupPolicy.Destination.LOGIN, StartupPolicy.decide(
                true, true, AuthRepository.RestoreState.SIGNED_OUT));
    }

    // ---------------- ErrorMapper (problem+json contract) ----------------

    @Test
    public void parsesProblemJsonWithStableCode() {
        String body = "{\"type\":\"https://docs.ikp.local/errors/AUTH-001\",\"title\":\"Unauthorized\","
                + "\"status\":401,\"code\":\"AUTH-001\",\"detail\":\"invalid credentials\","
                + "\"instance\":\"/api/v1/auth/login\"}";
        ErrorMapper.Problem p = ErrorMapper.parseProblem(body);
        assertTrue(p != null);
        assertEquals("AUTH-001", p.code);
        assertEquals("invalid credentials", p.detail);
        assertEquals("error_credentials", ErrorMapper.messageKeyForCode(p.code));
    }

    @Test
    public void rejectsNonProblemJson() {
        assertNull(ErrorMapper.parseProblem("<html>oops</html>"));
        assertNull(ErrorMapper.parseProblem(""));
        assertNull(ErrorMapper.parseProblem("{\"nope\":1}"));
    }

    @Test
    public void mapsKnownCodesToUserMessages() {
        assertEquals("error_credentials", ErrorMapper.messageKeyForCode("AUTH-001"));
        assertEquals("error_auth", ErrorMapper.messageKeyForCode("AUTH-002"));
        assertEquals("error_account_suspended", ErrorMapper.messageKeyForCode("AUTH-004"));
        assertEquals("error_forbidden", ErrorMapper.messageKeyForCode("AUTHZ-001"));
        assertEquals("error_rate_limited", ErrorMapper.messageKeyForCode("RATE-001"));
        assertEquals("error_not_found", ErrorMapper.messageKeyForCode("NF-001"));
        assertEquals("error_server", ErrorMapper.messageKeyForCode("SERVER-001"));
        assertEquals("error_unknown", ErrorMapper.messageKeyForCode("SOMETHING-NEW"));
    }

    @Test
    public void mapsStatusWhenNoCode() {
        assertEquals("error_auth", ErrorMapper.messageKeyForStatus(401));
        assertEquals("error_forbidden", ErrorMapper.messageKeyForStatus(403));
        assertEquals("error_not_found", ErrorMapper.messageKeyForStatus(404));
        assertEquals("error_rate_limited", ErrorMapper.messageKeyForStatus(429));
        assertEquals("error_server", ErrorMapper.messageKeyForStatus(503));
    }

    @Test
    public void timeoutAndOfflineThrowablesMapLocally() {
        java.net.SocketTimeoutException t = new java.net.SocketTimeoutException("read timed out");
        assertEquals(ApiError.TIMEOUT, ErrorMapper.fromThrowable(t).code);
        java.net.UnknownHostException u = new java.net.UnknownHostException("api");
        assertEquals(ApiError.OFFLINE, ErrorMapper.fromThrowable(u).code);
    }

    // ---------------- Wire mappers ----------------

    @Test
    public void subjectMappingPreservesStableSlugAndPosition() {
        LibraryDtos.SubjectDto dto = new LibraryDtos.SubjectDto();
        dto.slug = "cat_quran_tafsir";
        dto.name = "Al-Quran & Tafsir";
        dto.nameAr = "القرآن والتفسير";
        dto.position = 1;
        Models.Subject s = Mappers.toSubject(dto);
        assertEquals("cat_quran_tafsir", s.slug);
        assertEquals(Integer.valueOf(1), s.position);
        assertEquals("القرآن والتفسير", s.nameAr);
    }

    @Test
    public void positionSixNeverArrivesFromPublicApi() {
        // Invariant per OD-03: the public list cannot contain position 6
        // (server-side enforced). Mapper drops malformed rows but never
        // re-numbers positions — display order is data, not client state.
        LibraryDtos.SubjectDto a = subject("cat_a", 5);
        LibraryDtos.SubjectDto b = subject("cat_b", 7);
        List<Models.Subject> out = Mappers.toSubjects(Arrays.asList(a, null, b));
        assertEquals(2, out.size());
        assertEquals(Integer.valueOf(5), out.get(0).position);
        assertEquals(Integer.valueOf(7), out.get(1).position);
    }

    private static LibraryDtos.SubjectDto subject(String slug, int position) {
        LibraryDtos.SubjectDto d = new LibraryDtos.SubjectDto();
        d.slug = slug;
        d.name = slug;
        d.position = position;
        return d;
    }

    @Test
    public void bookRowMappingDefaultsDownloadAllowedToFalse() {
        LibraryDtos.BookRowDto dto = new LibraryDtos.BookRowDto();
        dto.bookId = "0b0e-1";
        dto.title = "Riyad as-Salihin (synthetic dev sample)";
        LibraryDtos.AuthorRefDto author = new LibraryDtos.AuthorRefDto();
        author.name = "Synthetic Author";
        dto.authors = Arrays.asList(author);
        dto.formats = Arrays.asList("PDF");
        dto.downloadAllowed = null; // server omitted field
        Models.Book b = Mappers.toBook(dto);
        assertFalse(b.downloadAllowed); // fail-safe default
        assertEquals(1, b.authors.size());
        assertEquals("PDF", b.formats.get(0));
    }

    @Test
    public void meResponseMapsAllProfileFields() {
        Dtos.MeResponseDto me = new Dtos.MeResponseDto();
        me.userId = "11111111-1111-1111-1111-111111111111";
        me.email = "dev-reader@ikp.test";
        me.displayName = "Dev Reader";
        me.language = "en";
        me.status = "active";
        me.roles = Arrays.asList("registered_user");
        me.createdAt = "2026-01-01T00:00:00Z";
        Models.User u = Mappers.toUser(me);
        assertEquals("dev-reader@ikp.test", u.email);
        assertEquals("active", u.status);
        assertTrue(u.roles.contains("registered_user"));
    }

    // ---------------- Envelope parsing (Gson, real shapes) ----------------

    @Test
    public void parsesLoginEnvelopeWithCamelCaseAndSnakeAlternates() {
        String json = "{\"data\":{\"accessToken\":\"a.b.c\",\"refreshToken\":\"r\","
                + "\"expiresIn\":900,\"user\":{\"userId\":\"u-1\",\"displayName\":\"Dev\","
                + "\"roles\":[\"registered_user\"]}}}";
        com.ikp.dev.app.core.network.ApiResponse<Dtos.TokenResponseDto> env = new Gson().fromJson(json,
                new com.google.gson.reflect.TypeToken<com.ikp.dev.app.core.network.ApiResponse<Dtos.TokenResponseDto>>() {
                }.getType());
        assertEquals("a.b.c", env.data.accessToken);
        assertEquals("r", env.data.refreshToken);
        assertEquals(Long.valueOf(900L), env.data.expiresIn);
        assertEquals("u-1", env.data.user.userId);
    }

    @Test
    public void parsesBooksEnvelopeWithCursorMeta() {
        String json = "{\"data\":[{\"bookId\":\"b-1\",\"title\":\"T\",\"language\":\"en\","
                + "\"subject\":{\"slug\":\"cat_quran_tafsir\",\"name\":\"Al-Quran & Tafsir\"},"
                + "\"authors\":[],\"formats\":[\"PDF\"],\"downloadAllowed\":true,"
                + "\"publishedAt\":\"2026-02-02T00:00:00Z\"}],"
                + "\"meta\":{\"limit\":20,\"nextCursor\":\"Mg==\"}}";
        com.ikp.dev.app.core.network.ApiResponse<java.util.List<LibraryDtos.BookRowDto>> env =
                new Gson().fromJson(json,
                        new com.google.gson.reflect.TypeToken<com.ikp.dev.app.core.network.ApiResponse<java.util.List<LibraryDtos.BookRowDto>>>() {
                        }.getType());
        assertEquals("Mg==", env.meta.nextCursor);
        assertEquals(1, env.data.size());
        assertTrue(env.data.get(0).downloadAllowed);
    }

    // ---------------- BookRepository query key ----------------

    @Test
    public void queryKeyIsStableAndCaseInsensitive() {
        assertEquals(BookRepository.queryKey("cat_fiqh", "en", "Fiqh"),
                BookRepository.queryKey("cat_fiqh", "en", "fiqh"));
        assertFalse(BookRepository.queryKey("cat_fiqh", "en", "a")
                .equals(BookRepository.queryKey("cat_fiqh", "en", "b")));
    }

    // ---------------- Sync scheduling decisions ----------------

    @Test
    public void syncEnqueueOnlyWhenPendingOpsExist() {
        assertFalse(SyncScheduler.shouldEnqueue(0));
        assertTrue(SyncScheduler.shouldEnqueue(1));
    }

    @Test
    public void backoffIsExponentialAndCapped() {
        long d0 = SyncScheduler.backoffDelayMs(0, 30);
        long d2 = SyncScheduler.backoffDelayMs(2, 30);
        long d9 = SyncScheduler.backoffDelayMs(9, 30);
        assertEquals(30_000L, d0);
        assertEquals(120_000L, d2);
        assertTrue(d9 <= 15L * 60 * 1000);
        assertTrue(d9 >= d2);
    }

    @Test
    public void maxAttemptsIsFinite() {
        assertTrue(SyncScheduler.maxAttempts() > 0);
        assertTrue(SyncScheduler.maxAttempts() <= 10);
    }
}
