package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateMenuTemplateRequest;
import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.shared.exceptions.BusinessException;
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
    private MenuRepository menuRepository;
    private MenuTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(MenuTemplateRepository.class);
        menuRepository = mock(MenuRepository.class);
        service = new MenuTemplateService(repository, menuRepository);
    }

    @Test
    void seedDefaultsInsertsAllFourWhenTableIsEmpty() {
        when(repository.count()).thenReturn(0L);

        service.seedDefaults();

        ArgumentCaptor<List<MenuTemplateEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<MenuTemplateEntity> seeded = captor.getValue();
        assertEquals(4, seeded.size());
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("CLASSIC")));
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("MODERN_DARK")));
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("VIBRANT")));
        assertTrue(seeded.stream().anyMatch(t -> t.getKey().equals("HORECA")));
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

    @Test
    void createSavesANewDefinition() {
        when(repository.existsById("RUSTIC")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity created = service.create(CreateMenuTemplateRequest.builder()
                .key("RUSTIC").displayName("Rustic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.INFO).build());

        assertEquals("RUSTIC", created.getKey());
        assertEquals("Rustic", created.getDisplayName());
    }

    @Test
    void createThrowsWhenTheKeyAlreadyExists() {
        when(repository.existsById("CLASSIC")).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.create(CreateMenuTemplateRequest.builder()
                .key("CLASSIC").displayName("Duplicate")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND).build()));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesAnUnusedDefinition() {
        MenuTemplateEntity existing = MenuTemplateEntity.builder().key("RUSTIC").displayName("Rustic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.INFO).build();
        when(repository.findById("RUSTIC")).thenReturn(Optional.of(existing));
        when(repository.count()).thenReturn(4L);
        when(menuRepository.countByTemplateStyle("RUSTIC")).thenReturn(0L);

        service.delete("RUSTIC");

        verify(repository).delete(existing);
    }

    @Test
    void deleteThrowsForAnUnknownKey() {
        when(repository.findById("BOGUS")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete("BOGUS"));
    }

    @Test
    void deleteThrowsWhenItIsTheLastRemainingDefinition() {
        MenuTemplateEntity existing = MenuTemplateEntity.builder().key("CLASSIC").displayName("Classic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND).build();
        when(repository.findById("CLASSIC")).thenReturn(Optional.of(existing));
        when(repository.count()).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.delete("CLASSIC"));
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteThrowsWhenBranchesAreStillUsingIt() {
        MenuTemplateEntity existing = MenuTemplateEntity.builder().key("VIBRANT").displayName("Vibrant")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.WARN).build();
        when(repository.findById("VIBRANT")).thenReturn(Optional.of(existing));
        when(repository.count()).thenReturn(4L);
        when(menuRepository.countByTemplateStyle("VIBRANT")).thenReturn(2L);

        assertThrows(BusinessException.class, () -> service.delete("VIBRANT"));
        verify(repository, never()).delete(any());
    }
}
