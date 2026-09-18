package com.qrserve.menu.dto;

import com.qrserve.menu.entity.MenuTemplateEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The structural fields are deliberately NOT {@code @NotNull}: they were added
 * after this endpoint shipped, and a client that predates them must still be
 * able to create a working template. MenuTemplateService fills any omitted
 * field with the entity default rather than persisting null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuTemplateRequest {
    /** Matches MenuTemplateEntity.key's column (length 20) — uppercase letters/digits/underscores, starting with a letter. */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,19}$", message = "key must start with an uppercase letter and contain only uppercase letters, digits, or underscores (max 20 characters)")
    private String key;

    @NotBlank
    @Size(max = 60)
    private String displayName;

    @NotNull
    private MenuTemplateEntity.BackgroundMode backgroundMode;

    @NotNull
    private MenuTemplateEntity.AccentToken accentToken;

    // ---- Structure (optional; defaulted by the service when omitted) ----

    private MenuTemplateEntity.LayoutStructure layoutStructure;

    private MenuTemplateEntity.ItemCardStyle itemCardStyle;

    private Boolean showImages;

    private MenuTemplateEntity.ImagePosition imagePosition;

    private MenuTemplateEntity.ImageAspectRatio imageAspectRatio;

    private MenuTemplateEntity.FontFamily fontFamily;

    private MenuTemplateEntity.HeaderAlignment headerAlignment;

    private Boolean showCoverImage;
}
