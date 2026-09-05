package com.qrserve.backoffice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAuditLogRequest {
    private UUID actorUserId;
    private String actorRole;

    @NotBlank
    private String action;

    @NotBlank
    private String entityType;

    private String entityId;
    private UUID merchantId;
    private String details;
}
