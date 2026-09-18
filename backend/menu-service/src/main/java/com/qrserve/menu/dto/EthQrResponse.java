package com.qrserve.menu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response for GET /api/payment/ethqr.
 *
 * <p>These are the confirmed fields of the single response shape
 * qr.safaricom.et/api/qr/generate returns for an {@code accountNumber}-only
 * request, mapped one-to-one rather than passed through as opaque JSON:
 *
 * <pre>
 * {
 *   "qrImageUrl":    "data:image/png;base64,iVBORw0KGgo...",
 *   "merchantName":  "Muzemil Erichamo Anjilo",
 *   "accountNumber": "8319389",
 *   "mobileNumber":  "+251718788479",
 *   "city":          null
 * }
 * </pre>
 *
 * <p>This used to carry an untyped {@code providerResponse} blob plus an
 * {@code amount} echo, which pushed field-name guessing into the frontend
 * (it tried eight candidate keys for the image) and allowed two different
 * response shapes to reach the UI. With the contract pinned, the mapping
 * belongs here: one shape, named fields, and a hard failure at this boundary
 * if the image is missing — see SafaricomEthQrService#generate.
 *
 * <p>{@code merchantName}/{@code accountNumber} are the provider's own
 * values, not this app's merchant record, so a printed standee can only ever
 * show what Safaricom itself confirmed for that short code. {@code city} is
 * frequently null in real responses and is carried through as-is.
 */
@Data
@Builder
public class EthQrResponse {
    /** Always a usable {@code <img src>} — normalised to a data URL if the provider sent bare base64. */
    private String qrImageUrl;
    private String merchantName;
    private String accountNumber;
    private String mobileNumber;
    private String city;
}
