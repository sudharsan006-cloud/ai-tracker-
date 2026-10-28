package com.lifetracker;

import com.lifetracker.config.RateLimitFilter;
import com.lifetracker.config.SecurityHeadersFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class Phase6UnitTest {

    private RateLimitFilter rateLimitFilter;
    private SecurityHeadersFilter securityHeadersFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
        securityHeadersFilter = new SecurityHeadersFilter();
    }

    @Test
    @DisplayName("SecurityHeadersFilter sets nosniff, no-referrer, and Cache-Control: no-store for api routes")
    void testSecurityHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        securityHeadersFilter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("RateLimitFilter limits auth endpoints to 10 requests per minute per IP")
    void testAuthRateLimiting() throws Exception {
        String ip = "10.0.0.1";

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.addHeader("X-Forwarded-For", ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            rateLimitFilter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        // 11th request receives 429
        MockHttpServletRequest request11 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request11.addHeader("X-Forwarded-For", ip);
        MockHttpServletResponse response11 = new MockHttpServletResponse();
        MockFilterChain chain11 = new MockFilterChain();

        rateLimitFilter.doFilter(request11, response11, chain11);
        assertThat(response11.getStatus()).isEqualTo(429);
        assertThat(response11.getContentAsString()).contains("RATE_LIMITED");

        // After reset, succeeds again
        rateLimitFilter.reset();
        MockHttpServletRequest request12 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request12.addHeader("X-Forwarded-For", ip);
        MockHttpServletResponse response12 = new MockHttpServletResponse();
        MockFilterChain chain12 = new MockFilterChain();

        rateLimitFilter.doFilter(request12, response12, chain12);
        assertThat(response12.getStatus()).isNotEqualTo(429);
    }
}
