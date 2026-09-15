package com.borderflow.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates the two kinds of token this system uses. Both
 * are RS256 -- asymmetric so that verifying a token never requires the
 * private key, only ever the public one, which is what makes
 * cross-site verification "offline": a site checks an incoming
 * AUDITOR token against Depot's public key baked into its own config,
 * with no network call back to Depot at request time.
 *
 * Signing ALWAYS uses this site's own private key -- even for an
 * AUDITOR token, since only Depot's instance is ever configured (by
 * policy, not by code) to call issueAuditorToken. Every other site's
 * AuthController only ever calls issueOperatorToken.
 */
@Service
public class JwtService {

    private final JwtKeyProvider keys;
    private final JwtProperties properties;
    private final String thisSiteId;

    public JwtService(JwtKeyProvider keys, JwtProperties properties, @Value("${site.id}") String thisSiteId) {
        this.keys = keys;
        this.properties = properties;
        this.thisSiteId = thisSiteId;
    }

    /** Site-local operational token. Only ever verifiable at THIS site. */
    public String issueOperatorToken(String username) {
        return buildToken(username, Role.OPERATOR, thisSiteId);
    }

    /**
     * Cross-site auditor token. Only meaningful when issued by Depot --
     * AuthController on non-Depot sites never calls this; there is no
     * code-level restriction preventing it, the restriction is
     * deployment policy (Border/Port/Destination's app_users tables are
     * never seeded with AUDITOR rows -- see db/migrations). Every site
     * still verifies the `iss` claim ends up matching a trusted key
     * regardless, so a misconfigured non-Depot site issuing one of
     * these would simply produce tokens nobody else accepts, since
     * they're signed with that site's own key, not Depot's.
     */
    public String issueAuditorToken(String username) {
        return buildToken(username, Role.AUDITOR, "any-site");
    }

    private String buildToken(String username, Role role, String homeSiteId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuer(thisSiteId)
                .claim("role", role.name())
                .claim("site", homeSiteId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getExpirationMinutes() * 60)))
                .signWith(keys.sitePrivateKey())
                .compact();
    }

    /**
     * Checks the token's signature against BOTH this site's own public
     * key and Depot's public key independently (not "try local, then
     * fall back to Depot") -- at Depot itself those two keys are the
     * exact same key, so a naive first-match-wins check would always
     * classify Depot's own tokens as "local," incorrectly rejecting
     * Depot's own AUDITOR tokens (see the role/crossSite consistency
     * check below). Returns empty if neither key verifies it, or if
     * it's expired/malformed. Callers should treat empty exactly like
     * "not authenticated" -- there is deliberately no partial-trust
     * state.
     */
    public Optional<AuthenticatedPrincipal> validate(String token) {
        Optional<Claims> viaSiteKey = tryParse(token, keys.sitePublicKey());
        Optional<Claims> viaDepotKey = tryParse(token, keys.depotPublicKey());

        Claims claims = viaSiteKey.or(() -> viaDepotKey).orElse(null);
        if (claims == null) {
            return Optional.empty();
        }

        try {
            Role role = Role.valueOf(claims.get("role", String.class));
            String homeSiteId = claims.get("site", String.class);

            // OPERATOR tokens are only trusted when they verify against
            // THIS site's own key -- that's what "site-local" means.
            // AUDITOR tokens are only trusted when they verify against
            // Depot's key -- that's what makes them a cross-site
            // credential in the first place. A token that verifies but
            // claims the "wrong" role for how it was signed is rejected
            // rather than silently granted an unintended authority.
            if (role == Role.OPERATOR && viaSiteKey.isPresent()) {
                return Optional.of(new AuthenticatedPrincipal(claims.getSubject(), role, homeSiteId, false));
            }
            if (role == Role.AUDITOR && viaDepotKey.isPresent()) {
                return Optional.of(new AuthenticatedPrincipal(claims.getSubject(), role, homeSiteId, true));
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Claims> tryParse(String token, PublicKey key) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
