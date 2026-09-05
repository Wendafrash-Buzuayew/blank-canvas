package com.qrserve.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetMenuTemplateRequest {
    /** A MenuTemplateEntity key — validated against the template-definitions table in MenuService.setTemplate. */
    @NotBlank
    @Size(max = 20)
    private String templateStyle;
}
