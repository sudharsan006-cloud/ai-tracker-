package com.lifetracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private volatile long lastCleanupMinute = 0;

    public void reset() {
        requestCounters.clear();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            long currentMinute = System.currentTimeMillis() / 60000L;
            cleanupOldWindows(currentMinute);

            // Auth endpoints: 10 per minute per client IP
            if (isAuthEndpoint(path)) {
                String clientIp = getClientIp(request);
                String key = "auth:" + clientIp + ":" + currentMinute;
                int count = requestCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                if (count > 10) {
                    writeRateLimitResponse(response);
                    return;
                }
            } else if ("/api/v1/insights/generate".equals(path)) {
                // Insight generation: 6 per minute per user
                String userKey = getAuthenticatedUserKey(request);
                String key = "insight:" + userKey + ":" + currentMinute;
                int count = requestCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                if (count > 6) {
                    writeRateLimitResponse(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(String path) {
        return "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/signup".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/refresh".equals(path);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                return parts[0].trim();
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private String getAuthenticatedUserKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            return auth.getPrincipal().toString();
        }
        return getClientIp(request);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again in a minute.\"}");
    }

    private void cleanupOldWindows(long currentMinute) {
        if (currentMinute > lastCleanupMinute) {
            lastCleanupMinute = currentMinute;
            long cutoff = currentMinute - 2;
            requestCounters.keySet().removeIf(k -> {
                int lastColon = k.lastIndexOf(':');
                if (lastColon > 0) {
                    try {
                        long min = Long.parseLong(k.substring(lastColon + 1));
                        return min < cutoff;
                    } catch (NumberFormatException ignored) {
                    }
                }
                return false;
            });
        }
    }
}
