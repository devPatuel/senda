package dev.jordi.senda.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long WINDOW_SECONDS = 60;

    private MutableClock clock;
    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        filter = new AuthRateLimitFilter(new ObjectMapper(), MAX_ATTEMPTS, WINDOW_SECONDS, "", clock);
    }

    private MockHttpServletResponse perform(String method, String uri, String ip)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowsRequestsUpToTheLimit() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            MockHttpServletResponse response = perform("POST", "/api/auth/login", "10.0.0.1");
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void blocksRequestsOverTheLimitWith429Json() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            perform("POST", "/api/auth/login", "10.0.0.1");
        }

        MockHttpServletResponse response = perform("POST", "/api/auth/login", "10.0.0.1");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("Too Many Requests");
    }

    @Test
    void limitAppliesToRegisterAndLoginTogether() throws Exception {
        perform("POST", "/api/auth/register", "10.0.0.1");
        perform("POST", "/api/auth/login", "10.0.0.1");
        perform("POST", "/api/auth/login", "10.0.0.1");

        assertThat(perform("POST", "/api/auth/register", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse performBehindProxy(AuthRateLimitFilter target, String realIp)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        // Every request reaches the backend from the proxy's own address
        request.setRemoteAddr("172.18.0.4");
        request.addHeader("X-Real-IP", realIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        target.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void behindAProxyEachRealClientGetsItsOwnLimit() throws Exception {
        AuthRateLimitFilter proxied =
                new AuthRateLimitFilter(new ObjectMapper(), MAX_ATTEMPTS, WINDOW_SECONDS, "X-Real-IP", clock);
        for (int i = 0; i <= MAX_ATTEMPTS; i++) {
            performBehindProxy(proxied, "192.168.1.50");
        }

        assertThat(performBehindProxy(proxied, "192.168.1.50").getStatus()).isEqualTo(429);
        // One client burning its attempts must not lock everybody else out
        assertThat(performBehindProxy(proxied, "192.168.1.51").getStatus()).isEqualTo(200);
    }

    @Test
    void withoutAProxyTheHeaderIsIgnored() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            performBehindProxy(filter, "10.0.0." + i);
        }

        // Rotating a client-supplied header must not reset the limit
        assertThat(performBehindProxy(filter, "10.0.0.99").getStatus()).isEqualTo(429);
    }

    @Test
    void limitIsPerClientIp() throws Exception {
        for (int i = 0; i <= MAX_ATTEMPTS; i++) {
            perform("POST", "/api/auth/login", "10.0.0.1");
        }

        assertThat(perform("POST", "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void windowResetsAfterExpiry() throws Exception {
        for (int i = 0; i <= MAX_ATTEMPTS; i++) {
            perform("POST", "/api/auth/login", "10.0.0.1");
        }
        assertThat(perform("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));

        assertThat(perform("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitNonAuthEndpoints() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS * 2; i++) {
            assertThat(perform("GET", "/api/categories", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void doesNotCountCorsPreflightRequests() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS * 2; i++) {
            assertThat(perform("OPTIONS", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }

        assertThat(perform("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    /** Minimal mutable clock to drive the fixed window in tests. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
