package com.borderflow.auth;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the trust model directly, with real (but throwaway,
 * in-memory) RSA keypairs -- no classpath PEM files involved, see
 * JwtKeyProvider's test-only constructor. This is the most
 * security-critical logic in the project: getting the OPERATOR/AUDITOR
 * trust boundary wrong here would mean either locking legitimate users
 * out or, worse, granting an authority a token was never meant to
 * carry. See JwtService's class javadoc for the trust model these
 * tests are verifying.
 */
class JwtServiceTest {

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JwtProperties properties() {
        JwtProperties props = new JwtProperties();
        props.setExpirationMinutes(60);
        return props;
    }

    @Test
    void operatorTokenIsValidAtItsOwnIssuingSite() {
        KeyPair siteKeys = generateKeyPair();
        JwtKeyProvider keys = new JwtKeyProvider(siteKeys.getPrivate(), siteKeys.getPublic(), siteKeys.getPublic());
        JwtService service = new JwtService(keys, properties(), "border");

        String token = service.issueOperatorToken("liaison-01");
        Optional<AuthenticatedPrincipal> result = service.validate(token);

        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("liaison-01");
        assertThat(result.get().role()).isEqualTo(Role.OPERATOR);
        assertThat(result.get().crossSite()).isFalse();
    }

    @Test
    void operatorTokenSignedByOneSiteIsRejectedWhenVerifiedAgainstAnotherSitesKeys() {
        KeyPair borderKeys = generateKeyPair();
        KeyPair portKeys = generateKeyPair();
        KeyPair depotKeys = generateKeyPair();

        // Token minted at Border, using Border's own keys.
        JwtService borderService = new JwtService(
                new JwtKeyProvider(borderKeys.getPrivate(), borderKeys.getPublic(), depotKeys.getPublic()),
                properties(), "border");
        String tokenFromBorder = borderService.issueOperatorToken("liaison-01");

        // Verified at Port, which has its own key and Depot's public key configured -- neither matches Border's signature.
        JwtService portService = new JwtService(
                new JwtKeyProvider(portKeys.getPrivate(), portKeys.getPublic(), depotKeys.getPublic()),
                properties(), "port");

        assertThat(portService.validate(tokenFromBorder)).isEmpty();
    }

    @Test
    void auditorTokenIssuedAtDepotIsValidAtEverySite() {
        KeyPair depotKeys = generateKeyPair();
        KeyPair portKeys = generateKeyPair();

        // Depot issues the auditor token with its own key.
        JwtService depotService = new JwtService(
                new JwtKeyProvider(depotKeys.getPrivate(), depotKeys.getPublic(), depotKeys.getPublic()),
                properties(), "depot");
        String auditorToken = depotService.issueAuditorToken("inspector-01");

        // Port trusts Depot's public key for cross-site verification, even though Port has a different key of its own.
        JwtService portService = new JwtService(
                new JwtKeyProvider(portKeys.getPrivate(), portKeys.getPublic(), depotKeys.getPublic()),
                properties(), "port");

        Optional<AuthenticatedPrincipal> result = portService.validate(auditorToken);

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo(Role.AUDITOR);
        assertThat(result.get().crossSite()).isTrue();
    }

    @Test
    void depotCanValidateItsOwnAuditorTokenDespiteSiteKeyAndDepotKeyBeingIdentical() {
        // Regression test for a real bug found while building this: at
        // Depot, "this site's own key" and "Depot's key" are literally
        // the same key. An earlier version of validate() picked
        // whichever check happened to run first and used THAT to decide
        // crossSite, which incorrectly rejected Depot's own AUDITOR
        // tokens. See JwtService.validate's javadoc for the fix.
        KeyPair depotKeys = generateKeyPair();
        JwtService depotService = new JwtService(
                new JwtKeyProvider(depotKeys.getPrivate(), depotKeys.getPublic(), depotKeys.getPublic()),
                properties(), "depot");

        String auditorToken = depotService.issueAuditorToken("inspector-01");
        String operatorToken = depotService.issueOperatorToken("depot-staff-01");

        assertThat(depotService.validate(auditorToken)).isPresent();
        assertThat(depotService.validate(auditorToken).get().role()).isEqualTo(Role.AUDITOR);

        assertThat(depotService.validate(operatorToken)).isPresent();
        assertThat(depotService.validate(operatorToken).get().role()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void garbageTokenIsRejected() {
        KeyPair keys = generateKeyPair();
        JwtService service = new JwtService(
                new JwtKeyProvider(keys.getPrivate(), keys.getPublic(), keys.getPublic()),
                properties(), "depot");

        assertThat(service.validate("not-a-real-token")).isEmpty();
    }
}
