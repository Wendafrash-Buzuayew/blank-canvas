package com.qrserve.menu.controller;

import com.qrserve.menu.dto.CreateMenuTemplateRequest;
import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.service.MenuTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuTemplateControllerTest {

    private MenuTemplateService service;
    private MenuTemplateController controller;

    @BeforeEach
    void setUp() {
        service = mock(MenuTemplateService.class);
        controller = new MenuTemplateController(service);
    }

    @Test
    void getAllDelegatesToTheService() {
        List<MenuTemplateEntity> all = List.of(
                MenuTemplateEntity.builder().key("CLASSIC").displayName("Classic")
                        .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                        .accentToken(MenuTemplateEntity.AccentToken.BRAND).build());
        when(service.getAll()).thenReturn(all);

        var response = controller.getAll();

        assertEquals(all, response.getBody());
    }

    @Test
    void updateDelegatesToTheService() {
        UpdateMenuTemplateRequest request = UpdateMenuTemplateRequest.builder()
                .displayName("Sunset").backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.WARN).build();
        MenuTemplateEntity updated = MenuTemplateEntity.builder().key("VIBRANT").displayName("Sunset")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.WARN).build();
        when(service.update("VIBRANT", request)).thenReturn(updated);

        var response = controller.update("VIBRANT", request);

        assertEquals(updated, response.getBody());
    }

    @Test
    void createDelegatesToTheServiceAndReturns201() {
        CreateMenuTemplateRequest request = CreateMenuTemplateRequest.builder()
                .key("RUSTIC").displayName("Rustic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.INFO).build();
        MenuTemplateEntity created = MenuTemplateEntity.builder().key("RUSTIC").displayName("Rustic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.INFO).build();
        when(service.create(request)).thenReturn(created);

        var response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(created, response.getBody());
    }

    @Test
    void deleteDelegatesToTheServiceAndReturns204() {
        var response = controller.delete("RUSTIC");

        verify(service).delete("RUSTIC");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
