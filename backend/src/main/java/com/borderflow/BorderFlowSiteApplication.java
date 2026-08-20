package com.borderflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for a single site's BorderFlow service instance.
 * Which site this instance represents is set via the SITE_ID environment
 * variable / application.yml (see application.yml) — the same JAR is
 * deployed once per site (Depot, Border, Port, Destination), each against
 * that site's own local Postgres, per the offline-first design.
 */
@SpringBootApplication
public class BorderFlowSiteApplication {
    public static void main(String[] args) {
        SpringApplication.run(BorderFlowSiteApplication.class, args);
    }
}
