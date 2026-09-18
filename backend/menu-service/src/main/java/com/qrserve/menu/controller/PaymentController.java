package com.qrserve.menu.controller;

import com.qrserve.menu.dto.EthQrResponse;
import com.qrserve.menu.service.SafaricomEthQrService;
import com.qrserve.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Safaricom ETHQR payment-QR generation (Pro tier)")
public class PaymentController {

    private final SafaricomEthQrService safaricomEthQrService;

    /**
     * No {@code amount} parameter: the ETHQR request body is
     * {@code accountNumber} alone (see SafaricomEthQrService), and a standee
     * QR is a reusable open-amount code by definition — the guest enters
     * what they owe.
     */
    @GetMapping("/ethqr")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_OWNER')")
    @Operation(summary = "Generate a Safaricom ETHQR payment QR for a branch's merchant")
    public ResponseEntity<EthQrResponse> generateEthQr(
            @RequestParam Long branchId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(safaricomEthQrService.generate(branchId, principal));
    }
}
