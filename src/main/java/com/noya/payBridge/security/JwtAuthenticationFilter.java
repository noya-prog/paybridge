package com.noya.payBridge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * This filter runs once per request.
     * It extracts the JWT from the Authorization header,
     * validates it, and sets the merchant's identity
     * in the Spring Security context.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                UUID merchantId = jwtTokenProvider.getMerchantIdFromToken(jwt);
                String email = jwtTokenProvider.getEmailFromToken(jwt);

                // Create authentication object with merchant identity
                // Principal = merchantId (what we use in controllers to know who made the request)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                merchantId,           // principal
                                null,                 // credentials (not needed after validation)
                                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set authentication in SecurityContext
                // After this line, the request is considered authenticated
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Set authentication for merchant: {} ({})", merchantId, email);
            }
        } catch (Exception e) {
            log.error("Could not set merchant authentication in security context: {}", e.getMessage());
            // Don't throw — let the request continue and Spring Security will reject it
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header.
     * Expects format: "Bearer <token>"
     *
     * @param request the HTTP request
     * @return the token string, or null if not present
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Remove "Bearer " prefix
        }

        return null;
    }
}
