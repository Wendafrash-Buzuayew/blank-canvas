package com.qrserve.notification.service;

import com.qrserve.notification.handler.OrderWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final OrderWebSocketHandler webSocketHandler;

    public void notifyNewOrder(String orderNumber, String tableNumber) {
        String payload = String.format("{\"event\":\"NEW_ORDER\",\"table\":\"%s\",\"orderNumber\":\"%s\"}", tableNumber, orderNumber);
        webSocketHandler.broadcastEvent("NEW_ORDER", payload);
    }

    public void notifyStatusChange(String orderNumber, String status) {
        String payload = String.format("{\"event\":\"ORDER_STATUS\",\"orderNumber\":\"%s\",\"status\":\"%s\"}", orderNumber, status);
        webSocketHandler.broadcastEvent("ORDER_STATUS", payload);
    }

    public void notifyTableOccupied(String tableNumber) {
        String payload = String.format("{\"event\":\"TABLE_OCCUPIED\",\"table\":\"%s\"}", tableNumber);
        webSocketHandler.broadcastEvent("TABLE_OCCUPIED", payload);
    }

    /**
     * Notify waiters/kitchen of a customer service request from a table.
     * requestType: CALL_WAITER, WATER_REQUEST, REQUEST_BILL, ASSISTANCE
     */
    public void notifyCustomerRequest(Long tableId, String tableNumber, String requestType, String note) {
        String payload = String.format(
            "{\"event\":\"CUSTOMER_REQUEST\",\"tableId\":%d,\"table\":\"%s\",\"requestType\":\"%s\",\"note\":\"%s\"}",
            tableId, tableNumber, requestType, note != null ? note.replace("\"", "'") : "");
        webSocketHandler.broadcastEvent("CUSTOMER_REQUEST", payload);
    }

    /**
     * Notify kitchen of an order ready for delivery.
     */
    public void notifyOrderReady(String orderNumber, String tableNumber) {
        String payload = String.format(
            "{\"event\":\"ORDER_READY\",\"orderNumber\":\"%s\",\"table\":\"%s\"}",
            orderNumber, tableNumber);
        webSocketHandler.broadcastEvent("ORDER_READY", payload);
    }

    /**
     * Notify waiter of order served confirmation.
     */
    public void notifyOrderServed(String orderNumber, String tableNumber) {
        String payload = String.format(
            "{\"event\":\"ORDER_SERVED\",\"orderNumber\":\"%s\",\"table\":\"%s\"}",
            orderNumber, tableNumber);
        webSocketHandler.broadcastEvent("ORDER_SERVED", payload);
    }

    /**
     * Notify branch/merchant announcement.
     */
    public void notifyAnnouncement(String title, String message) {
        String payload = String.format(
            "{\"event\":\"ANNOUNCEMENT\",\"title\":\"%s\",\"message\":\"%s\"}",
            title != null ? title.replace("\"", "'") : "",
            message != null ? message.replace("\"", "'") : "");
        webSocketHandler.broadcastEvent("ANNOUNCEMENT", payload);
    }
}
