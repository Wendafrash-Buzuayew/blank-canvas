package com.qrserve.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Newer Super App handshake shape: msisdn/shortCode arrive as discrete
 * fields, alongside a signature/superAppToken pair meant to let the server
 * cryptographically verify the payload actually came from the Super App.
 *
 * <p>signature/superAppToken are accepted here but NOT verified yet — no
 * real signing key or algorithm has been supplied by the Super App team
 * (the same "no real contract exists" situation as every other Super App
 * integration point in this codebase; see SuperAppAuthPort's own Javadoc).
 * See SuperAppProvisioningService#exchangeAndLoginFromSuperApp for exactly
 * what happens with them today, and the fail-closed gate this endpoint
 * shares with the existing dev-fake exchange path.
 */
@Data
public class SuperAppLoginRequest {
    @NotBlank(message = "msisdn is required")
    private String msisdn;

    @NotBlank(message = "shortCode is required")
    private String shortCode;

    /** Not yet verified — see the class Javadoc. */
    private String signature;

    /** Not yet verified — see the class Javadoc. */
    private String superAppToken;
}
