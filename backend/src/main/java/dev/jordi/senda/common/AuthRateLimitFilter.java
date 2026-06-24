package dev.jordi.senda.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for the public auth endpoints, keyed by client IP.
 * /api/auth/** is unauthenticated and every attempt costs a BCrypt hash, so
 * without a cap it is both a brute-force and a cheap CPU-exhaustion vector.
 * In-memory on purpose: single-instance deployment (NAS), no shared store needed.
 */
@Component
@Order(AuthRateLimitFilter.ORDER)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    // Before the Spring Security chain (-100): throttled requests never reach BCrypt
    static final int ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 10;

    private static final int PURGE_THRESHOLD = 1_000;

    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    // @Autowired is required here: with two constructors Spring cannot pick one on its own
    @Autowired
    public AuthRateLimitFilter(ObjectMapper objectMapper,
                               @Value("${senda.auth.rate-limit.max-attempts:10}") int maxAttempts,
                               @Value("${senda.auth.rate-limit.window-seconds:60}") long windowSeconds) {
        this(objectMapper, maxAttempts, windowSeconds, Clock.systemUTC());
    }

    AuthRateLimitFilter(ObjectMapper objectMapper, int maxAttempts, long windowSeconds, Clock clock) {
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1_000;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip CORS preflights so they do not eat the browser's login budget
        return !request.getRequestURI().startsWith("/api/auth/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long now = clock.millis();
        purgeExpiredIfNeeded(now);
        // getRemoteAddr, not X-Forwarded-For: the header is client-controlled and
        // trusting it here would let an attacker reset their own limit at will
        Window window = windows.compute(request.getRemoteAddr(), (ip, current) ->
                current == null || current.isExpired(now, windowMillis) ? new Window(now) : current);
        if (window.incrementAndGet() > maxAttempts) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(429, "Too Many Requests", "Too many attempts, try again later"));
    }

    private void purgeExpiredIfNeeded(long now) {
        // Bound memory: anonymous IPs would otherwise grow the map forever
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMillis));
        }
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }

        private boolean isExpired(long now, long windowMillis) {
            return now - start >= windowMillis;
        }

        private int incrementAndGet() {
            return count.incrementAndGet();
        }
    }
}
