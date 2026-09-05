package com.qrserve.menu.dto;

import com.qrserve.menu.entity.MenuTemplateEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuTemplateRequest {
    @NotBlank
    @Size(max = 60)
    private String displayName;

    @NotNull
    private MenuTemplateEntity.BackgroundMode backgroundMode;

    @NotNull
    private MenuTemplateEntity.AccentToken accentToken;
}
