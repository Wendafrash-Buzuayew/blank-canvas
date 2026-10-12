package com.qrserve.auth.superapp;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start auth-service when the Super App dev fake is switched on
 * under the {@code prod} profile. The fake accepts ANY caller who names a
 * merchant short code as that merchant's owner, so a single stray
 * SUPERAPP_DEV_FAKE_ENABLED=true in a deployed environment is a passwordless
 * takeover of every merchant. Failing the boot is discovered by whoever ran
 * the deploy; the open endpoint would not be.
 */
@Component
public class DevFakeProdGuard {

    static final String FLAG = "superapp.auth.dev-fake-enabled";

    public DevFakeProdGuard(Environment environment) {
        check(environment);
    }

    static void check(Environment environment) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        boolean devFake = environment.getProperty(FLAG, Boolean.class, false);
        if (prod && devFake) {
            throw new IllegalStateException(
                    "SUPERAPP_DEV_FAKE_ENABLED=true is not allowed under the 'prod' profile: "
                            + "the dev fake accepts any caller as any merchant owner.");
        }
    }
}
