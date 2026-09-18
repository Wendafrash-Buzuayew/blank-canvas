package com.qrserve.menu.dto;

import com.qrserve.menu.entity.MenuTemplateEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The structural fields are deliberately NOT {@code @NotNull}. A PUT is a full
 * replace, so a client that predates these fields would otherwise blank a
 * template's entire layout simply by saving its name - MenuTemplateService
 * therefore treats an omitted field as "leave unchanged", not as "set to
 * null". See MenuTemplateService.update.
 */
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

    // ---- Structure (optional; omitted means "leave unchanged") ----

    private MenuTemplateEntity.LayoutStructure layoutStructure;

    private MenuTemplateEntity.ItemCardStyle itemCardStyle;

    private Boolean showImages;

    private MenuTemplateEntity.ImagePosition imagePosition;

    private MenuTemplateEntity.ImageAspectRatio imageAspectRatio;

    private MenuTemplateEntity.FontFamily fontFamily;

    private MenuTemplateEntity.HeaderAlignment headerAlignment;

    private Boolean showCoverImage;
}
