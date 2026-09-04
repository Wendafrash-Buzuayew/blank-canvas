package com.qrserve.menu.dto;

import com.qrserve.menu.entity.MenuEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetMenuTemplateRequest {
    @NotNull
    private MenuEntity.TemplateStyle templateStyle;
}
