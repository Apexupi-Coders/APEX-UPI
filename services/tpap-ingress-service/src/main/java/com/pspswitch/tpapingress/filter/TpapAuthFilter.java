package com.pspswitch.tpapingress.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.tpapingress.dto.response.ErrorResponse;
import com.pspswitch.tpapingress.exception.RateLimitExceededException;
import com.pspswitch.tpapingress.service.TpapAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
<<<<<<< HEAD
import org.springframework.core.Ordered;
=======
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

>>>>>>> c24d976 (Initial commit)
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Servlet filter that authenticates every inbound request.
 * Extracts X-TPAP-API-Key and X-TPAP-ID headers and validates against registry.
 * Skips the /health endpoint.
 * See architecture_spec.md Section 6.
 */
<<<<<<< HEAD
=======
@Slf4j
>>>>>>> c24d976 (Initial commit)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TpapAuthFilter extends OncePerRequestFilter {

    private final TpapAuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
<<<<<<< HEAD
        return path.endsWith("/health") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
=======

        boolean skip = path.equals("/actuator/health") ||

               path.equals("/actuator/health/") ||
               path.equals("/actuator/health/readiness") ||
               path.equals("/actuator/health/readiness/") ||
               path.equals("/actuator/health/liveness") ||
               path.equals("/actuator/health/liveness/") ||
               path.endsWith("/health") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               // Callback is invoked by orchestrator/webhook; do not require any TPAP auth headers.
               path.equals("/api/v1/tpap/callback") ||
               path.equals("/api/v1/tpap/callback/") ||
               path.equals("/tpap/api/v1/tpap/callback") ||
               path.equals("/tpap/api/v1/tpap/callback/");

        if (skip) {

        }

        return skip;
>>>>>>> c24d976 (Initial commit)
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String tpapId = request.getHeader("X-TPAP-ID");

<<<<<<< HEAD
=======


>>>>>>> c24d976 (Initial commit)
        // Check headers are present and non-blank
        if (isBlank(tpapId)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "MISSING_TPAP_ID", "X-TPAP-ID header is required");
            return;
        }

        // Authenticate against registry
        try {
            boolean authenticated = authService.authenticate(tpapId);
            if (!authenticated) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID_TPAP_ID", "Provided TPAP ID is not registered");
                return;
            }
        } catch (RateLimitExceededException e) {
            response.setIntHeader("Retry-After", 60);
            writeError(response, 429,
                    "RATE_LIMIT_EXCEEDED", e.getMessage());
            return;
        }

        // Set tpapId as request attribute for controllers
        request.setAttribute("tpapId", tpapId);
        filterChain.doFilter(request, response);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void writeError(HttpServletResponse response, int status,
                            String errorCode, String message) throws IOException {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .build();

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
