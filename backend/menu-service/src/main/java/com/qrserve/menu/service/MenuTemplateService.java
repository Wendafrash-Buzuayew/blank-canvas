package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateMenuTemplateRequest;
import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Owns the curated template DEFINITIONS a merchant picks from
 * (MenuService.setTemplate references these by key). SUPER_ADMIN edits the
 * colour pair and the structural fields here, and can also create new
 * definitions or delete ones no branch is using - see create/delete below.
 * Every field is still a constrained choice (an enum or a boolean), never a
 * raw hex value or CSS string, so no admin-created combination can render
 * illegible text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuTemplateService {

    private final MenuTemplateRepository repository;
    private final MenuRepository menuRepository;

    /**
     * Seeds the starter defaults on first boot against a fresh database, and
     * is a no-op on every boot after (idempotent by design, not one-off like
     * BranchMenuBackfillRunner - there is no flag to flip back off). Runs
     * after the context is fully up rather than at construction time so a
     * slow-starting datasource never delays application readiness.
     *
     * <p>The four starters deliberately differ in STRUCTURE as well as colour,
     * so a fresh install demonstrates the range the schema supports rather
     * than four recolourings of one layout.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (repository.count() > 0) {
            return;
        }
        log.info("Seeding default menu template definitions");
        repository.saveAll(List.of(
                // Classic: the reference look - a single readable column with a
                // thumbnail beside each dish.
                MenuTemplateEntity.builder()
                        .key("CLASSIC").displayName("Classic")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                        .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                        .layoutStructure(MenuTemplateEntity.LayoutStructure.SINGLE_COLUMN)
                        .itemCardStyle(MenuTemplateEntity.ItemCardStyle.CARD_BORDERED)
                        .showImages(true)
                        .imagePosition(MenuTemplateEntity.ImagePosition.LEFT)
                        .imageAspectRatio(MenuTemplateEntity.ImageAspectRatio.SQUARE_1_1)
                        .fontFamily(MenuTemplateEntity.FontFamily.SANS_SERIF)
                        .headerAlignment(MenuTemplateEntity.HeaderAlignment.CENTER)
                        .showCoverImage(true)
                        .build(),
                // Modern Dark: a photo-led two-up grid on a dark ground, images
                // on top so the food carries the page.
                MenuTemplateEntity.builder()
                        .key("MODERN_DARK").displayName("Modern Dark")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.DARK)
                        .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                        .layoutStructure(MenuTemplateEntity.LayoutStructure.GRID_2)
                        .itemCardStyle(MenuTemplateEntity.ItemCardStyle.ELEVATED_SHADOW)
                        .showImages(true)
                        .imagePosition(MenuTemplateEntity.ImagePosition.TOP)
                        .imageAspectRatio(MenuTemplateEntity.ImageAspectRatio.LANDSCAPE_16_9)
                        .fontFamily(MenuTemplateEntity.FontFamily.SANS_SERIF)
                        .headerAlignment(MenuTemplateEntity.HeaderAlignment.CENTER)
                        .showCoverImage(true)
                        .build(),
                // Vibrant: tinted ground, three-up grid, round avatars.
                MenuTemplateEntity.builder()
                        .key("VIBRANT").displayName("Vibrant")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                        .accentToken(MenuTemplateEntity.AccentToken.WARN)
                        .layoutStructure(MenuTemplateEntity.LayoutStructure.GRID_3)
                        .itemCardStyle(MenuTemplateEntity.ItemCardStyle.CARD_FLAT)
                        .showImages(true)
                        .imagePosition(MenuTemplateEntity.ImagePosition.TOP)
                        .imageAspectRatio(MenuTemplateEntity.ImageAspectRatio.ROUNDED_AVATAR)
                        .fontFamily(MenuTemplateEntity.FontFamily.SANS_SERIF)
                        .headerAlignment(MenuTemplateEntity.HeaderAlignment.LEFT)
                        .showCoverImage(true)
                        .build(),
                // Horeca: a warm, text-first printed-card look - no dish
                // photography, serif face, hairline dividers. This is the one
                // starter that proves the no-images path renders properly.
                MenuTemplateEntity.builder()
                        .key("HORECA").displayName("Horeca")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                        .accentToken(MenuTemplateEntity.AccentToken.WARN)
                        .layoutStructure(MenuTemplateEntity.LayoutStructure.COMPACT_LIST)
                        .itemCardStyle(MenuTemplateEntity.ItemCardStyle.MINIMAL_DIVIDER)
                        .showImages(false)
                        .imagePosition(MenuTemplateEntity.ImagePosition.NONE)
                        .imageAspectRatio(MenuTemplateEntity.ImageAspectRatio.SQUARE_1_1)
                        .fontFamily(MenuTemplateEntity.FontFamily.SERIF)
                        .headerAlignment(MenuTemplateEntity.HeaderAlignment.CENTER)
                        .showCoverImage(false)
                        .build()));
    }

    /**
     * Every read path normalises, so a row written before the structure
     * migration still reaches the customer page as a complete definition
     * rather than as a set of nulls the frontend has to guess at.
     */
    @Transactional(readOnly = true)
    public List<MenuTemplateEntity> getAll() {
        return repository.findAll().stream().map(MenuTemplateEntity::normalised).toList();
    }

    @Transactional
    public MenuTemplateEntity create(CreateMenuTemplateRequest request) {
        if (repository.existsById(request.getKey())) {
            throw new BusinessException("A template with key '" + request.getKey() + "' already exists");
        }
        // An omitted structural field falls back to the entity default rather
        // than persisting null - see CreateMenuTemplateRequest on why they are
        // not @NotNull.
        MenuTemplateEntity template = MenuTemplateEntity.builder()
                .key(request.getKey())
                .displayName(request.getDisplayName())
                .backgroundMode(request.getBackgroundMode())
                .accentToken(request.getAccentToken())
                .layoutStructure(Objects.requireNonNullElse(
                        request.getLayoutStructure(), MenuTemplateEntity.LayoutStructure.SINGLE_COLUMN))
                .itemCardStyle(Objects.requireNonNullElse(
                        request.getItemCardStyle(), MenuTemplateEntity.ItemCardStyle.CARD_BORDERED))
                .showImages(Objects.requireNonNullElse(request.getShowImages(), Boolean.TRUE))
                .imagePosition(Objects.requireNonNullElse(
                        request.getImagePosition(), MenuTemplateEntity.ImagePosition.LEFT))
                .imageAspectRatio(Objects.requireNonNullElse(
                        request.getImageAspectRatio(), MenuTemplateEntity.ImageAspectRatio.SQUARE_1_1))
                .fontFamily(Objects.requireNonNullElse(
                        request.getFontFamily(), MenuTemplateEntity.FontFamily.SANS_SERIF))
                .headerAlignment(Objects.requireNonNullElse(
                        request.getHeaderAlignment(), MenuTemplateEntity.HeaderAlignment.CENTER))
                .showCoverImage(Objects.requireNonNullElse(request.getShowCoverImage(), Boolean.TRUE))
                .build();
        return repository.save(template);
    }

    /**
     * A PUT replaces the colour pair and display name outright, but an omitted
     * STRUCTURAL field means "leave unchanged" rather than "set to null" - a
     * client that predates these fields must not be able to blank a
     * template's layout just by renaming it. See UpdateMenuTemplateRequest.
     */
    @Transactional
    public MenuTemplateEntity update(String key, UpdateMenuTemplateRequest request) {
        MenuTemplateEntity template = repository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("No template definition with key: " + key))
                .normalised();

        template.setDisplayName(request.getDisplayName());
        template.setBackgroundMode(request.getBackgroundMode());
        template.setAccentToken(request.getAccentToken());

        if (request.getLayoutStructure() != null) template.setLayoutStructure(request.getLayoutStructure());
        if (request.getItemCardStyle() != null) template.setItemCardStyle(request.getItemCardStyle());
        if (request.getShowImages() != null) template.setShowImages(request.getShowImages());
        if (request.getImagePosition() != null) template.setImagePosition(request.getImagePosition());
        if (request.getImageAspectRatio() != null) template.setImageAspectRatio(request.getImageAspectRatio());
        if (request.getFontFamily() != null) template.setFontFamily(request.getFontFamily());
        if (request.getHeaderAlignment() != null) template.setHeaderAlignment(request.getHeaderAlignment());
        if (request.getShowCoverImage() != null) template.setShowCoverImage(request.getShowCoverImage());

        return repository.save(template);
    }

    /**
     * Refuses to delete a template still assigned to any branch (would leave
     * that branch's menu pointing at a nonexistent definition) or the last
     * remaining definition (a merchant must always have at least one look to
     * pick).
     */
    @Transactional
    public void delete(String key) {
        MenuTemplateEntity template = repository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("No template definition with key: " + key));
        if (repository.count() <= 1) {
            throw new BusinessException("At least one template definition must remain");
        }
        long branchesUsingIt = menuRepository.countByTemplateStyle(key);
        if (branchesUsingIt > 0) {
            throw new BusinessException("Cannot delete '" + key + "': " + branchesUsingIt
                    + " branch" + (branchesUsingIt == 1 ? "" : "es") + " currently using it. Switch them to another template first.");
        }
        repository.delete(template);
    }
}
