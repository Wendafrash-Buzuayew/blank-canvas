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

/**
 * Owns the curated template DEFINITIONS a merchant picks from
 * (MenuService.setTemplate references these by key). SUPER_ADMIN edits
 * displayName/backgroundMode/accentToken here, and can also create new
 * definitions or delete ones no branch is using - see create/delete below.
 * Every field is still a constrained choice (an enum), never a raw hex
 * value, so no admin-created combination can render illegible text.
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
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (repository.count() > 0) {
            return;
        }
        log.info("Seeding default menu template definitions");
        repository.saveAll(List.of(
                MenuTemplateEntity.builder()
                        .key("CLASSIC").displayName("Classic")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                        .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                        .build(),
                MenuTemplateEntity.builder()
                        .key("MODERN_DARK").displayName("Modern Dark")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.DARK)
                        .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                        .build(),
                MenuTemplateEntity.builder()
                        .key("VIBRANT").displayName("Vibrant")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                        .accentToken(MenuTemplateEntity.AccentToken.WARN)
                        .build(),
                // Warm, HORECA-style (hotel/restaurant/cafe) starter look: a light
                // canvas (distinct ground from Vibrant's tinted one) with an amber
                // accent, suited to food-service branding.
                MenuTemplateEntity.builder()
                        .key("HORECA").displayName("Horeca")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                        .accentToken(MenuTemplateEntity.AccentToken.WARN)
                        .build()));
    }

    @Transactional(readOnly = true)
    public List<MenuTemplateEntity> getAll() {
        return repository.findAll();
    }

    @Transactional
    public MenuTemplateEntity create(CreateMenuTemplateRequest request) {
        if (repository.existsById(request.getKey())) {
            throw new BusinessException("A template with key '" + request.getKey() + "' already exists");
        }
        MenuTemplateEntity template = MenuTemplateEntity.builder()
                .key(request.getKey())
                .displayName(request.getDisplayName())
                .backgroundMode(request.getBackgroundMode())
                .accentToken(request.getAccentToken())
                .build();
        return repository.save(template);
    }

    @Transactional
    public MenuTemplateEntity update(String key, UpdateMenuTemplateRequest request) {
        MenuTemplateEntity template = repository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("No template definition with key: " + key));
        template.setDisplayName(request.getDisplayName());
        template.setBackgroundMode(request.getBackgroundMode());
        template.setAccentToken(request.getAccentToken());
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
