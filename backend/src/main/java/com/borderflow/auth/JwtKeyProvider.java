package com.borderflow.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA keys named in JwtProperties from the classpath at
 * startup and hands back usable java.security key objects. Kept
 * separate from JwtService so the "read PEM off disk" concern doesn't
 * get mixed into the "sign/verify a token" concern.
 *
 * PEM files under src/main/resources/keys/ are LOCAL DEV ONLY, checked
 * in for convenience of running this project out of the box -- see the
 * warning in application.yml and backend/README.md. A real deployment
 * would load these from a secrets manager or mounted volume, never
 * from the built JAR.
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyProvider {

    private final PrivateKey sitePrivateKey;
    private final PublicKey sitePublicKey;
    private final PublicKey depotPublicKey;

    @Autowired
    public JwtKeyProvider(JwtProperties properties) {
        try {
            this.sitePrivateKey = loadPrivateKey(properties.getSitePrivateKeyPath());
            this.sitePublicKey = loadPublicKey(properties.getSitePublicKeyPath());
            this.depotPublicKey = loadPublicKey(properties.getDepotPublicKeyPath());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not load JWT keys from " + properties.getSitePrivateKeyPath() +
                            " / " + properties.getSitePublicKeyPath() +
                            " / " + properties.getDepotPublicKeyPath() +
                            " -- check borderflow.jwt.* in application.yml", e);
        }
    }

    /** Test-only: bypasses PEM/classpath loading entirely for unit tests that generate their own keys in memory. */
    JwtKeyProvider(PrivateKey sitePrivateKey, PublicKey sitePublicKey, PublicKey depotPublicKey) {
        this.sitePrivateKey = sitePrivateKey;
        this.sitePublicKey = sitePublicKey;
        this.depotPublicKey = depotPublicKey;
    }

    public PrivateKey sitePrivateKey() {
        return sitePrivateKey;
    }

    public PublicKey sitePublicKey() {
        return sitePublicKey;
    }

    public PublicKey depotPublicKey() {
        return depotPublicKey;
    }

    private static PrivateKey loadPrivateKey(String classpathLocation)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readPem(classpathLocation, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static PublicKey loadPublicKey(String classpathLocation)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readPem(classpathLocation, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String readPem(String classpathLocation, String marker) throws IOException {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return raw
                    .replace("-----BEGIN " + marker + "-----", "")
                    .replace("-----END " + marker + "-----", "")
                    .replaceAll("\\s", "");
        }
    }
}
