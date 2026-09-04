package com.qrserve.menu.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A menu is created DRAFT and carries no publishedAt until an explicit
 * publish — this is the invariant the whole "only Published menus are
 * public" contract rests on (HLD 6.2).
 */
class MenuEntityTest {

    @Test
    void newMenuDefaultsToDraftWithNoPublishedAt() {
        MenuEntity menu = MenuEntity.builder()
                .branchId(5L)
                .merchantId(UUID.randomUUID())
                .build();
        menu.prePersist();

        assertEquals(MenuEntity.Status.DRAFT, menu.getStatus());
        assertNull(menu.getPublishedAt());
        assertNotNull(menu.getCreatedAt());
    }

    @Test
    void explicitStatusIsNotOverwrittenByPrePersist() {
        MenuEntity menu = MenuEntity.builder()
                .branchId(5L)
                .merchantId(UUID.randomUUID())
                .status(MenuEntity.Status.PUBLISHED)
                .build();
        menu.prePersist();

        assertEquals(MenuEntity.Status.PUBLISHED, menu.getStatus());
    }
}
