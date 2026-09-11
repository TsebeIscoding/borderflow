package com.borderflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs the `borderflow.jwt.*` keys in application.yml. Every site is
 * configured with three things:
 *
 *  - its OWN keypair, used to sign tokens issued by ITS OWN /api/auth/login
 *    (site-local operational roles -- only ever trusted at this site)
 *  - DEPOT's public key, used to verify tokens issued by Depot for
 *    cross-site roles (offline-verifiable: no network call to Depot is
 *    needed at request time, just the public key baked into config)
 *
 * At Depot itself, "own keypair" and "Depot's public key" are the same
 * key -- Depot signs both its own operators' tokens and every
 * cross-site auditor token with one keypair.
 */
@ConfigurationProperties(prefix = "borderflow.jwt")
public class JwtProperties {

    private String sitePrivateKeyPath;
    private String sitePublicKeyPath;
    private String depotPublicKeyPath;
    private long expirationMinutes = 60;

    public String getSitePrivateKeyPath() {
        return sitePrivateKeyPath;
    }

    public void setSitePrivateKeyPath(String sitePrivateKeyPath) {
        this.sitePrivateKeyPath = sitePrivateKeyPath;
    }

    public String getSitePublicKeyPath() {
        return sitePublicKeyPath;
    }

    public void setSitePublicKeyPath(String sitePublicKeyPath) {
        this.sitePublicKeyPath = sitePublicKeyPath;
    }

    public String getDepotPublicKeyPath() {
        return depotPublicKeyPath;
    }

    public void setDepotPublicKeyPath(String depotPublicKeyPath) {
        this.depotPublicKeyPath = depotPublicKeyPath;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
