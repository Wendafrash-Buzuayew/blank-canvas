package com.qrserve.gateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers "is this whole site fit to receive traffic," for a global load
 * balancer sitting in front of multiple sites/DCs to poll — NOT "is this one
 * pod healthy" (that's still plain pod-level readiness, unchanged). See
 * docs/superpowers/specs/2026-09-16-multi-dc-production-deployment-design.md
 * §5.
 *
 * <p>Deliberately reads THIS site's own local Eureka registry via {@link
 * DiscoveryClient} — this cluster's api-gateway is the one place that
 * already depends on that registry for real `lb://` routing, so it is also
 * the natural place to ask "does the registry this gateway is about to route
 * against actually contain every critical service." Eureka is never
 * consulted across sites (see the design doc for why that would be an
 * anti-pattern over a WAN link) — this indicator, and the site-level health
 * signal it feeds, are what stand in for that at the GSLB layer instead.
 *
 * <p>Deliberately NOT wired into the default `readiness` probe group: doing
 * so would pull this pod out of the in-cluster Service's endpoint list over
 * a transient Eureka blip that most in-cluster calls don't even depend on
 * (every direct service-to-service call in this codebase uses a plain
 * configured URL, not `lb://` — see e.g. auth-service's own
 * application.yml). It is wired into a separate `site` group instead — see
 * application.yml's `management.endpoint.health.group.site`.
 */
@Component
public class SiteReadinessHealthIndicator implements HealthIndicator {

    private final DiscoveryClient discoveryClient;

    /**
     * The services a customer-facing request can actually reach through this
     * gateway today (see application.yml's route list) — order-service,
     * notification-service, analytics-service and back-office-service are
     * deliberately excluded: none of them sit on the Phase 1 mini-app's
     * customer-facing path (menu browse, QR scan, merchant login/onboarding),
     * so their absence should not pull an otherwise-healthy site out of GSLB
     * rotation. Revisit this list if the mini-app's Phase 2 surface (orders,
     * live kitchen) is reactivated for the sites this indicator runs in.
     */
    private static final List<String> CRITICAL_SERVICES =
            List.of("auth-service", "merchant-service", "menu-service", "qr-service");

    /** Below this, a registered service is treated as effectively absent. */
    @Value("${site-health.min-instances-per-service:1}")
    private int minInstancesPerService;

    public SiteReadinessHealthIndicator(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Health health() {
        List<String> missing = new ArrayList<>();
        for (String serviceId : CRITICAL_SERVICES) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            if (instances.size() < minInstancesPerService) {
                missing.add(serviceId);
            }
        }

        if (!missing.isEmpty()) {
            return Health.down()
                    .withDetail("missingOrUnderReplicatedServices", missing)
                    .withDetail("requiredMinInstances", minInstancesPerService)
                    .build();
        }
        return Health.up().build();
    }
}
