package com.borderflow.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * The only unauthenticated endpoint in the system (see SecurityConfig).
 * Issues whatever kind of token this user's stored `role` calls for --
 * OPERATOR (site-local) for ordinary staff, or AUDITOR (cross-site) for
 * accounts specifically provisioned that way. In practice only Depot's
 * app_users table is ever seeded with an AUDITOR row (see
 * db/migrations/depot/V6) -- see JwtService.issueAuditorToken's javadoc
 * for what happens if a non-Depot site tried to anyway.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = switch (user.getRole()) {
            case OPERATOR -> jwtService.issueOperatorToken(user.getUsername());
            case AUDITOR -> jwtService.issueAuditorToken(user.getUsername());
        };

        return new LoginResponse(token, user.getUsername(), user.getRole());
    }
}
