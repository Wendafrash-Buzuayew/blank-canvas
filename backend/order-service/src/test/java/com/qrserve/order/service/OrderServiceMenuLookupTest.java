package com.qrserve.order.service;

import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the menu lookup that replaced the per-item {@code GET /api/products/{id}}
 * call.
 *
 * <p>That call was the cause of the customer-facing 503: the endpoint is not
 * public and order-service forwards no token when a guest orders, so every
 * anonymous order failed. These tests exercise the pure replacement logic —
 * flattening the menu payload and resolving an ordered item — without needing a
 * running service.
 */
class OrderServiceMenuLookupTest {

    /** Mirrors a real MenuResponse payload as it arrives from menu-service. */
    private Map<String, Object> menuPayload() {
        return Map.of("categories", List.of(
                Map.of("id", 1, "name", "Pizza", "items", List.of(
                        Map.of("id", 10, "name", "Margherita", "price", 420,
                                "preparationTime", 15, "available", true),
                        Map.of("id", 11, "name", "Diavola", "price", 480.50,
                                "preparationTime", 16, "available", true),
                        Map.of("id", 12, "name", "Sold Out Special", "price", 200,
                                "preparationTime", 5, "available", false)
                )),
                Map.of("id", 2, "name", "Coffee", "items", List.of(
                        Map.of("id", 20, "name", "Cappuccino", "price", 120,
                                "preparationTime", 4, "available", true)
                ))
        ));
    }

    @Test
    @DisplayName("flattens every category's items into one lookup")
    void indexesAcrossCategories() {
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(menuPayload());

        assertEquals(4, index.size());
        assertNotNull(index.get(10L));
        assertNotNull(index.get(20L), "items in the second category must be indexed too");
    }

    @Test
    @DisplayName("maps price and preparation time correctly")
    void mapsPriceAndPrepTime() {
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(menuPayload());

        OrderService.ProductInfo diavola = index.get(11L);
        assertEquals("Diavola", diavola.getName());
        assertEquals(0, new BigDecimal("480.50").compareTo(diavola.getPrice()),
                "a decimal price must survive the round trip without precision loss");
        assertEquals(16, diavola.getPreparationTime());
    }

    @Test
    @DisplayName("resolves an available product")
    void resolvesAvailableProduct() {
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(menuPayload());
        assertEquals("Margherita", OrderService.resolveProduct(index, 10L).getName());
    }

    @Test
    @DisplayName("an id outside this merchant's menu is not-found, not a server error")
    void unknownProductIsNotFound() {
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(menuPayload());
        assertThrows(ResourceNotFoundException.class,
                () -> OrderService.resolveProduct(index, 9999L));
    }

    @Test
    @DisplayName("a sold-out item is rejected as a business error")
    void unavailableProductIsRejected() {
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(menuPayload());
        BusinessException e = assertThrows(BusinessException.class,
                () -> OrderService.resolveProduct(index, 12L));
        assertTrue(e.getMessage().contains("Sold Out Special"),
                "the message should name the item so the guest knows which one");
    }

    @Test
    @DisplayName("a missing available flag defaults to orderable")
    void missingAvailableFlagDefaultsTrue() {
        Map<String, Object> payload = Map.of("categories", List.of(
                Map.of("items", List.of(
                        Map.of("id", 30, "name", "No Flag", "price", 99, "preparationTime", 3)
                ))
        ));
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(payload);
        // Defaulting to false would make an entire menu unorderable on a payload change.
        assertTrue(index.get(30L).isAvailable());
        assertEquals("No Flag", OrderService.resolveProduct(index, 30L).getName());
    }

    @Test
    @DisplayName("a null preparation time is tolerated")
    void nullPrepTimeTolerated() {
        Map<String, Object> payload = Map.of("categories", List.of(
                Map.of("items", List.of(
                        Map.of("id", 40, "name", "No Prep", "price", 50, "available", true)
                ))
        ));
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(payload);
        assertEquals(null, index.get(40L).getPreparationTime());
    }

    @Test
    @DisplayName("one malformed item does not discard the rest of the menu")
    void malformedItemIsSkippedNotFatal() {
        Map<String, Object> payload = Map.of("categories", List.of(
                Map.of("items", List.of(
                        Map.of("name", "No Id", "price", 10),              // no id
                        Map.of("id", 50, "name", "Good", "price", 75, "available", true)
                ))
        ));
        Map<Long, OrderService.ProductInfo> index = OrderService.indexMenu(payload);

        assertEquals(1, index.size(), "the malformed entry is skipped");
        assertNotNull(index.get(50L), "the valid entry survives");
    }

    @Test
    @DisplayName("an empty or unexpected payload yields an empty lookup, not an exception")
    void emptyPayloadIsSafe() {
        assertTrue(OrderService.indexMenu(Map.of()).isEmpty());
        assertTrue(OrderService.indexMenu(Map.of("categories", "not-a-list")).isEmpty());
        assertTrue(OrderService.indexMenu(Map.of("categories", List.of())).isEmpty());
        // The order then fails per-item with a precise not-found rather than a 500.
        assertThrows(ResourceNotFoundException.class,
                () -> OrderService.resolveProduct(OrderService.indexMenu(Map.of()), 1L));
    }
}
