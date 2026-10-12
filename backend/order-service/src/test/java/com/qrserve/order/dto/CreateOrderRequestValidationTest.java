package com.qrserve.order.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nested item constraints must actually run, so a guest cannot order -5 pizzas. */
class CreateOrderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static CreateOrderRequest request(int quantity) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(quantity);
        CreateOrderRequest r = new CreateOrderRequest();
        r.setTableId(7L);
        r.setSignature("sig");
        r.setItems(List.of(item));
        return r;
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 0, 100})
    @DisplayName("out-of-range quantities are rejected")
    void badQuantityRejected(int quantity) {
        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(request(quantity));
        assertEquals(1, v.size());
        assertTrue(v.iterator().next().getPropertyPath().toString().contains("quantity"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 99})
    @DisplayName("quantities 1..99 are accepted")
    void goodQuantityAccepted(int quantity) {
        assertTrue(validator.validate(request(quantity)).isEmpty());
    }

    @Test
    @DisplayName("a missing signature is rejected")
    void missingSignatureRejected() {
        CreateOrderRequest r = request(1);
        r.setSignature(null);
        assertEquals(1, validator.validate(r).size());
    }
}
