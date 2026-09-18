package com.qrserve.gateway.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the multi-DC/GSLB health signal: this is what a global load
 * balancer polls per site to decide whether to route there at all, so a
 * false UP (a site claiming to be fine when a critical service has no
 * registered instances) would send live traffic into a black hole, and a
 * false DOWN would needlessly pull a healthy site out of rotation.
 */
class SiteReadinessHealthIndicatorTest {

    private DiscoveryClient discoveryClient;
    private SiteReadinessHealthIndicator indicator;

    private static final List<String> CRITICAL =
            List.of("auth-service", "merchant-service", "menu-service", "qr-service");

    @BeforeEach
    void setUp() {
        discoveryClient = mock(DiscoveryClient.class);
        indicator = new SiteReadinessHealthIndicator(discoveryClient);
        ReflectionTestUtils.setField(indicator, "minInstancesPerService", 1);
    }

    private static ServiceInstance instance(String serviceId) {
        return new DefaultServiceInstance(serviceId + "-1", serviceId, "10.0.0.1", 8080, false);
    }

    private void everyCriticalServiceHasOneInstance() {
        for (String serviceId : CRITICAL) {
            when(discoveryClient.getInstances(serviceId)).thenReturn(List.of(instance(serviceId)));
        }
    }

    @Test
    @DisplayName("UP when every critical service has at least the required instance count")
    void upWhenEveryCriticalServicePresent() {
        everyCriticalServiceHasOneInstance();

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    @DisplayName("DOWN when a critical service has no registered instances at all")
    void downWhenACriticalServiceIsAbsent() {
        everyCriticalServiceHasOneInstance();
        when(discoveryClient.getInstances("merchant-service")).thenReturn(List.of());

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) health.getDetails().get("missingOrUnderReplicatedServices");
        assertIterableEquals(List.of("merchant-service"), missing);
    }

    @Test
    @DisplayName("DOWN when a critical service is registered but under the required minimum instance count")
    void downWhenUnderReplicated() {
        everyCriticalServiceHasOneInstance();
        ReflectionTestUtils.setField(indicator, "minInstancesPerService", 2);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    @DisplayName("non-critical services (order/notification/analytics/back-office) are never consulted")
    void doesNotCareAboutNonCriticalServices() {
        everyCriticalServiceHasOneInstance();
        when(discoveryClient.getInstances("order-service")).thenReturn(List.of());

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }
}
