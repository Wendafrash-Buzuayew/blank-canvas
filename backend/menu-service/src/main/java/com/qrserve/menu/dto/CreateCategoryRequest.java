package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CreateCategoryRequest {
    @NotNull
    private UUID merchantId;

    @NotNull
    private Long branchId;

    @NotBlank
    private String name;

    private Integer displayOrder;
}
