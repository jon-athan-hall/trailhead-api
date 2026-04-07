package dev.trailhead.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.trailhead.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * IP-based rate limiting for sensitive auth endpoints. Uses Bucket4j's token-bucket algorithm
 * with one bucket per (endpoint, IP) pair, kept in memory.
 *
 * Note: in-memory storage means each app instance tracks its own counters. For multi-server
 * deployments, swap to a Redis-backed Bucket4j store.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // Defines limits for each protected endpoint. Add or adjust here as needed.
    private final Map<String, Supplier<Bucket>> limitsByPath = new HashMap<>();

    // One bucket per (endpoint + IP). The key looks like "POST:/api/auth/login:1.2.3.4".
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // 5 login attempts per minute.
        limitsByPath.put("POST:/api/auth/login",
                () -> bucket(5, Duration.ofMinutes(1)));

        // 3 attempts per hour for these endpoints.
        Supplier<Bucket> threePerHour = () -> bucket(3, Duration.ofHours(1));
        limitsByPath.put("POST:/api/auth/register", threePerHour);
        limitsByPath.put("POST:/api/auth/forgot-password", threePerHour);
        limitsByPath.put("POST:/api/auth/resend-verification", threePerHour);
        limitsByPath.put("POST:/api/auth/reset-password", threePerHour);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getMethod() + ":" + request.getRequestURI();
        Supplier<Bucket> limit = limitsByPath.get(key);

        if (limit == null) {
            // No rate limit configured for this endpoint, let it through.
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = key + ":" + clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> limit.get());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        writeRateLimitResponse(request, response, retryAfterSeconds);
    }

    private Bucket bucket(long capacity, Duration period) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, period).build())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For is set by reverse proxies (nginx, Caddy, etc.). Falls back to the direct remote address.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletRequest request,
                                        HttpServletResponse response,
                                        long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.",
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
