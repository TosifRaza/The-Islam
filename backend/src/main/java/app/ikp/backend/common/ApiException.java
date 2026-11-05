package app.ikp.backend.common;

import org.springframework.http.HttpStatus;

/**
 * Service-level exception carrying a stable error code from the approved
 * catalogue (docs/05-api-specification.md §2). Rendered as RFC 7807
 * application/problem+json by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, HttpStatus status, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException validation(String detail) {
        return new ApiException("VAL-001", HttpStatus.BAD_REQUEST, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException("NF-001", HttpStatus.NOT_FOUND, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException("AUTHZ-001", HttpStatus.FORBIDDEN, detail);
    }

    public static ApiException workflow(String detail) {
        return new ApiException("WORKFLOW-001", HttpStatus.CONFLICT, detail);
    }

    public static ApiException of(String code, HttpStatus status, String detail) {
        return new ApiException(code, status, detail);
    }
}
