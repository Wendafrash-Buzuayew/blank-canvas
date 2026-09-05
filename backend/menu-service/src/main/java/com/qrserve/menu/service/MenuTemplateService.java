package com.qrserve.menu.service;

import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the three curated template DEFINITIONS a merchant picks from
 * (MenuService.setTemplate references these by key, unchanged by this
 * class). SUPER_ADMIN edits displayName/backgroundMode/accentToken here;
 * there is deliberately no create/delete - three fixed slots, matching
 * {@link com.qrserve.menu.entity.MenuEntity.TemplateStyle}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuTemplateService {

    private final MenuTemplateRepository repository;

    /**
     * Seeds the three defaults on first boot against a fresh database, and is
     * a no-op on every boot after (idempotent by design, not one-off like
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
                        .build()));
    }

    @Transactional(readOnly = true)
    public List<MenuTemplateEntity> getAll() {
        return repository.findAll();
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
}
