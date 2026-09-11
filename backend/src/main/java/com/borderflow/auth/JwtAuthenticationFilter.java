package com.borderflow.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads `Authorization: Bearer <token>`, hands it to JwtService, and --
 * if it validates -- populates the SecurityContext with a single
 * granted authority: ROLE_OPERATOR or ROLE_AUDITOR, exactly matching
 * how JwtService classified the token (never trusting the raw `role`
 * claim directly; see JwtService.validate's javadoc on why the two are
 * kept separate). A missing or invalid token simply leaves the context
 * empty -- Spring Security's own filter chain is what turns that into
 * a 401/403 for endpoints that require authentication, this filter
 * doesn't need to know which endpoints those are.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Optional<AuthenticatedPrincipal> principal = jwtService.validate(token);

            principal.ifPresent(p -> {
                var authority = new SimpleGrantedAuthority("ROLE_" + p.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(p, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
