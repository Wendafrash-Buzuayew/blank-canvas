package com.qrserve.menu.controller;

import com.qrserve.menu.dto.MenuResponse;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.service.MenuService;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuControllerDigitalMenuTest {

    private MenuService menuService;
    private MenuController controller;
    private static final Long BRANCH = 5L;
    private static final UUID MENU_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        menuService = mock(MenuService.class);
        controller = new MenuController(menuService);
    }

    @Test
    void getMenuForBranchReturnsContentWhenPublished() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuService.getMenuForBranch(BRANCH)).thenReturn(Optional.of(menu));
        when(menuService.getFullMenuByMenuId(MENU_ID)).thenReturn(MenuResponse.builder().build());

        MenuResponse body = controller.getMenuForBranch(BRANCH).getBody();

        assertNotNull(body);
    }

    @Test
    void getMenuForBranchRefusesADraftMenu() {
        MenuEntity menu = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuService.getMenuForBranch(BRANCH)).thenReturn(Optional.of(menu));

        assertThrows(ResourceNotFoundException.class, () -> controller.getMenuForBranch(BRANCH));
    }

    @Test
    void getMenuForBranchRefusesABranchWithNoMenuAtAll() {
        when(menuService.getMenuForBranch(BRANCH)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.getMenuForBranch(BRANCH));
    }

    @Test
    void publishDelegatesToTheService() {
        MenuEntity published = MenuEntity.builder().id(MENU_ID).branchId(BRANCH)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuService.publish(BRANCH)).thenReturn(published);

        MenuEntity body = controller.publish(BRANCH).getBody();

        assertEquals(MenuEntity.Status.PUBLISHED, body.getStatus());
    }
}
