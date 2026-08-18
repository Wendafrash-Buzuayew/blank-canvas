package com.qrserve.order.dto;

import com.qrserve.shared.common.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Bound to the {@link OrderStatus} enum rather than a free string.
 *
 * <p>As a {@code @NotBlank String} this accepted anything — including status names
 * the backend never emits, which is how the kitchen display came to send
 * {@code SERVED}. Binding to the enum makes Spring reject an unknown value with a
 * 400 before any persistence happens, and makes the valid set discoverable from
 * the OpenAPI schema.
 */
@Data
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required and must be one of the OrderStatus values")
    private OrderStatus status;
}
