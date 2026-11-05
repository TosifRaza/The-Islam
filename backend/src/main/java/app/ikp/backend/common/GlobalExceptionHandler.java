package app.ikp.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RFC 7807 error rendering per the approved API specification:
 * application/problem+json with {"type","title","status","code","detail",
 * "instance","errors":[{field,message}]} — stable machine codes from the
 * approved catalogue (docs/05-api-specification.md §2).
 *
 * M2 note: the M1 placeholder shape {"error":{code,message}} is replaced by
 * the approved problem+json contract; the error CODES are unchanged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> apiException(ApiException ex, jakarta.servlet.http.HttpServletRequest req) {
        return problem(ex.status(), ex.code(),
                ex.getMessage() == null ? ex.status().getReasonPhrase() : ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest req) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.add(Map.of("field", fe.getField(), "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage())));
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "VAL-001", "validation failed", req);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> auth(AuthenticationException ex, jakarta.servlet.http.HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "AUTH-002",
                ex instanceof AuthenticationCredentialsNotFoundException ? "authentication required" : "access token expired or invalid",
                req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "AUTHZ-001", "insufficient role or permission", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(NoResourceFoundException ex, jakarta.servlet.http.HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "NF-001", "resource not found", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex, jakarta.servlet.http.HttpServletRequest req) {
        // Log server-side; never leak internals to clients.
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER-001", "unexpected server error", req);
    }

    private ProblemDetail base(HttpStatus status, String code, String detail, jakarta.servlet.http.HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setType(URI.create("https://docs.ikp.local/errors/" + code));
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, jakarta.servlet.http.HttpServletRequest req) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(base(status, code, detail, req));
    }
}
