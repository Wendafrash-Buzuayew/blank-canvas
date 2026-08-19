package com.qrserve.gateway.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the dependency {@link TenantSlugResolver} needs to be constructible.
 *
 * <p>Boot 4 split auto-configuration into per-technology modules.
 * {@code spring-boot-starter-webflux} now carries only the server side, so the
 * {@code WebClient.Builder} bean this resolver injects arrives from
 * {@code spring-boot-starter-webclient} and nothing else on the gateway's
 * classpath. Drop that starter and the whole gateway fails to start with
 * "required a bean of type 'WebClient$Builder' that could not be found" — a
 * failure that only shows up when the container boots, which is why it is
 * asserted here instead.
 *
 * <p>The check reads the classpath's auto-configuration candidates rather than
 * starting a context: the gateway's own context needs Redis and Eureka, so
 * booting it is not something a unit test can do.
 */
class TenantSlugResolverWiringTest {

    @Test
    @DisplayName("the classpath contributes WebClient auto-configuration")
    void webClientAutoConfigurationIsOnTheClasspath() {
        List<String> candidates = ImportCandidates
                .load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        // Matched by simple name so a future package move inside Boot does not
        // turn a passing wiring check into a false alarm.
        assertTrue(
                candidates.stream().anyMatch(c -> c.endsWith(".WebClientAutoConfiguration")),
                "No WebClient auto-configuration on the gateway classpath: TenantSlugResolver "
                        + "cannot be constructed. Restore spring-boot-starter-webclient.");
    }
}
