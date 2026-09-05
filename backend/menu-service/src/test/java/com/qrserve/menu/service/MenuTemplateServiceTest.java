package com.qrserve.menu.service;

import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuTemplateServiceTest {

    private MenuTemplateRepository repository;
    private MenuTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(MenuTemplateRepository.class);
        service = new MenuTemplateService(repository);
    }

    @Test
    void seedDefaultsInsertsAllThreeWhenTableIsEmpty() {
        when(repository.count()).thenReturn(0L);

        service.seedDefaults();

        ArgumentCaptor<List<MenuTemplateEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<MenuTemplateEntity> seeded = captor.getValue();
        assertEquals(3, seeded.size());
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("CLASSIC")));
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("MODERN_DARK")));
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("VIBRANT")));
    }

    @Test
    void seedDefaultsDoesNothingWhenRowsAlreadyExist() {
        when(repository.count()).thenReturn(3L);

        service.seedDefaults();

        verify(repository, never()).saveAll(any());
    }

    @Test
    void getAllReturnsEveryDefinition() {
        List<MenuTemplateEntity> all = List.of(
                MenuTemplateEntity.builder().key("CLASSIC").displayName("Classic")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                        .accentToken(MenuTemplateEntity.AccentToken.BRAND).build());
        when(repository.findAll()).thenReturn(all);

        assertEquals(all, service.getAll());
    }

    @Test
    void updateChangesDisplayNameBackgroundModeAndAccent() {
        MenuTemplateEntity existing = MenuTemplateEntity.builder()
                .key("VIBRANT").displayName("Vibrant")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.WARN)
                .build();
        when(repository.findById("VIBRANT")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity updated = service.update("VIBRANT", UpdateMenuTemplateRequest.builder()
                .displayName("Sunset").backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.DANGER).build());

        assertEquals("Sunset", updated.getDisplayName());
        assertEquals(MenuTemplateEntity.BackgroundMode.LIGHT, updated.getBackgroundMode());
        assertEquals(MenuTemplateEntity.AccentToken.DANGER, updated.getAccentToken());
    }

    @Test
    void updateThrowsForAnUnknownKey() {
        when(repository.findById("BOGUS")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update("BOGUS", UpdateMenuTemplateRequest.builder()
                .displayName("X").backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND).build()));
    }
}
