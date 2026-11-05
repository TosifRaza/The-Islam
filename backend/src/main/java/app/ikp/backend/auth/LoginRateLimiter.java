package app.ikp.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal fixed-window limiter for credential endpoints (approved spec §1:
 * tighter buckets on /auth/**). Keyed by client IP; 429 RATE-001 when the
 * bucket is exhausted. Sufficient for dev/admin traffic; a distributed limiter
 * arrives with production infrastructure (OD-06).
 */
@Component
public class LoginRateLimiter {

    private record Window(long epoch, AtomicInteger count) {
    }

    private final Map<String, Window> buckets = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowSeconds;

    public LoginRateLimiter(@Value("${app.security.login-rate-limit}") int limit,
                            @Value("${app.security.login-rate-window-seconds}") long windowSeconds) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    /** @return true when the request is allowed (and consumes one token). */
    public boolean tryAcquire(String key) {
        long epoch = System.currentTimeMillis() / (windowSeconds * 1000);
        Window w = buckets.compute(key, (k, old) ->
                old == null || old.epoch() != epoch ? new Window(epoch, new AtomicInteger()) : old);
        return w.count().incrementAndGet() <= limit;
    }
}
