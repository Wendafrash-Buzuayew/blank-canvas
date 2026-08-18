package com.qrserve.merchant.dto;

import com.qrserve.shared.common.TableStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Replaces the raw {@code Map<String,String>} body on
 * {@code PATCH /api/tables/{id}/status}.
 *
 * <p>The map form persisted whatever string arrived and defaulted silently to
 * {@code AVAILABLE} when the {@code status} key was absent — so a typo, or a
 * client sending the wrong field name, quietly marked a table free.
 */
@Data
public class UpdateTableStatusRequest {

    @NotNull(message = "status is required and must be one of AVAILABLE, OCCUPIED, RESERVED")
    private TableStatus status;
}
