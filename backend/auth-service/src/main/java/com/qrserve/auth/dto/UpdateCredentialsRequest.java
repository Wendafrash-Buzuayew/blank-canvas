package com.qrserve.auth.dto;

import lombok.Data;

/**
 * Optional fallback credentials a Super-App-provisioned merchant may set
 * during onboarding, so they can also sign in from a browser outside the
 * Mini App. Both fields are optional and independent - a merchant may set
 * just an email, just a password, both, or neither and keep using the Super
 * App token exchange exclusively. See AuthController#updateOwnCredentials.
 */
@Data
public class UpdateCredentialsRequest {
    private String email;
    private String password;

    /**
     * Required (and checked against the stored hash) once the account has
     * already completed onboarding - see AuthService#updateOwnCredentials.
     * Left null during the one-time onboarding window, where the stored
     * password is an unguessable UUID no one could ever supply.
     */
    private String currentPassword;
}
