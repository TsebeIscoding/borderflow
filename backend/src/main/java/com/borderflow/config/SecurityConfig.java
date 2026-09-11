package com.borderflow.config;

import com.borderflow.auth.JwtAuthenticationFilter;
import com.borderflow.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateless JWT auth. No sessions, no cookies -- every request carries
 * its own token, which matches an offline-first design where a site
 * might have zero connectivity to anywhere that could hold session
 * state for it.
 *
 * `/api/auth/login` is the one open endpoint (see AuthController).
 * Everything else under `/api/**` requires authentication; specific
 * role requirements (OPERATOR vs AUDITOR) are declared with
 * `@PreAuthorize` directly on the controller methods that need them
 * (see HandoverController, TripController) rather than centralized
 * here, so the rule lives next to the code it protects.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        // Missing/invalid/expired token -- no principal at all.
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJsonError(response, 401, "Unauthorized", "A valid token is required for this request."))
                        // Valid token, but the role doesn't match @PreAuthorize
                        // on the endpoint (e.g. an AUDITOR calling handover).
                        .accessDeniedHandler((AccessDeniedHandler) (request, response, ex) ->
                                writeJsonError(response, 403, "Forbidden", "Your role does not permit this action."))
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String error, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
