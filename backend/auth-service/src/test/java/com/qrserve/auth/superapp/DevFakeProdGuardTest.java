package com.qrserve.auth.superapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevFakeProdGuardTest {

    private static MockEnvironment env(String profile, String flag) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) {
            env.setActiveProfiles(profile);
        }
        if (flag != null) {
            env.setProperty(DevFakeProdGuard.FLAG, flag);
        }
        return env;
    }

    @Test
    @DisplayName("prod profile + dev fake on refuses to start")
    void prodWithDevFakeFails() {
        assertThrows(IllegalStateException.class, () -> DevFakeProdGuard.check(env("prod", "true")));
    }

    @Test
    @DisplayName("prod profile with the flag off or unset starts")
    void prodWithoutDevFakeStarts() {
        assertDoesNotThrow(() -> DevFakeProdGuard.check(env("prod", "false")));
        assertDoesNotThrow(() -> DevFakeProdGuard.check(env("prod", null)));
    }

    @Test
    @DisplayName("dev fake is allowed outside prod")
    void devFakeOutsideProdStarts() {
        assertDoesNotThrow(() -> DevFakeProdGuard.check(env(null, "true")));
        assertDoesNotThrow(() -> DevFakeProdGuard.check(env("dev", "true")));
    }
}
