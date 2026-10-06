package com.educationerp.auth.security;

import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.common.web.RequestContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verifies the bearer access token and installs the resolved principal.
 *
 * A token that fails verification simply leaves the context empty; the entry point then
 * produces the standard {@code AUTHENTICATION_REQUIRED} response. A token whose subject
 * no longer resolves is rejected outright so a deleted user cannot keep using a live
 * token until it expires.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AuthenticatedUserLoader userLoader;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER) || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        try {
            Claims claims = tokenProvider.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            var resolved = userLoader.load(userId, true);
            if (resolved.isEmpty()) {
                log.warn("Rejected token for unknown subject requestId={}", RequestContext.getRequestId());
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            AuthenticatedUser principal = resolved.get();
            if (!tokenProvider.isCurrentTokenVersion(claims, principal.tokenVersion())) {
                log.warn("Rejected stale access token (password changed) subject={} requestId={}",
                        principal.username(), RequestContext.getRequestId());
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
            for (String permission : principal.permissions()) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
            for (String role : principal.roles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid access token presented requestId={}: {}", RequestContext.getRequestId(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    public static Set<String> rolesFrom(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
