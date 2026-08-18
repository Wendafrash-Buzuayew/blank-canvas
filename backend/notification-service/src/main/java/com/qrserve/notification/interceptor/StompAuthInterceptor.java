package com.qrserve.notification.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates and authorizes every inbound STOMP frame.
 *
 * <p>On {@code CONNECT} the identity established by
 * {@link JwtHandshakeInterceptor} is promoted to a STOMP {@link Principal}.
 *
 * <p>On {@code SUBSCRIBE} and {@code SEND} the destination is checked against
 * that identity. This is the part that used to be missing: the interceptor
 * verified only that <em>a</em> principal existed, so any authenticated user
 * could subscribe to {@code /topic/merchant/{anyMerchantId}/...} and read
 * another tenant's kitchen and waiter traffic.
 *
 * <p>Authorization is <strong>deny-by-default</strong>: a destination that
 * matches none of the known shapes is rejected. An allowlist that falls through
 * to "permit" is how the next topic family ships unprotected.
 */
@Component
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    /** /topic/merchant/{merchantUuid}/branch/{branchId}/{kitchen|waiters} */
    private static final Pattern MERCHANT_TOPIC = Pattern.compile(
            "^/topic/merchant/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                    + "/branch/(\\d+)/(kitchen|waiters)$");

    /** /topic/orders/{orderUuid} */
    private static final Pattern ORDER_TOPIC = Pattern.compile(
            "^/topic/orders/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            Map<String, Object> attributes = accessor.getSessionAttributes();
            String username = attributes != null
                    ? (String) attributes.get(JwtHandshakeInterceptor.ATTR_USERNAME)
                    : null;

            if (username == null) {
                throw new IllegalArgumentException("Unauthenticated WebSocket connection rejected");
            }
            accessor.setUser(new StompPrincipal(username));
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
            if (accessor.getUser() == null) {
                throw new IllegalArgumentException("Unauthenticated STOMP frame rejected: " + command);
            }
            authorizeDestination(accessor);
            return message;
        }

        if (StompCommand.ACK.equals(command) && accessor.getUser() == null) {
            throw new IllegalArgumentException("Unauthenticated STOMP frame rejected: ACK");
        }

        return message;
    }

    /**
     * Rejects any subscription the session's identity does not cover.
     *
     * <p>Three identity shapes:
     * <ul>
     *   <li>SUPER_ADMIN — any destination.</li>
     *   <li>Staff (has {@code ws.merchantId}) — only their own merchant's topics,
     *       and any single order (staff legitimately follow orders they serve).</li>
     *   <li>Guest (has {@code ws.orderId}, no role) — only that one order topic.</li>
     * </ul>
     */
    private void authorizeDestination(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String destination = accessor.getDestination();

        if (attrs == null || destination == null) {
            throw new IllegalArgumentException("STOMP frame without a destination rejected");
        }

        String role = (String) attrs.get(JwtHandshakeInterceptor.ATTR_ROLE);
        String ownMerchantId = (String) attrs.get(JwtHandshakeInterceptor.ATTR_MERCHANT_ID);
        String scopedOrderId = (String) attrs.get(JwtHandshakeInterceptor.ATTR_ORDER_ID);

        if (ROLE_SUPER_ADMIN.equals(role)) {
            return;
        }

        // Guest session: scoped to exactly one order, nothing else.
        if (scopedOrderId != null) {
            Matcher order = ORDER_TOPIC.matcher(destination);
            if (order.matches() && scopedOrderId.equalsIgnoreCase(order.group(1))) {
                return;
            }
            log.warn("Guest session for order {} denied destination {}", scopedOrderId, destination);
            throw new IllegalArgumentException("Not authorized for destination");
        }

        Matcher merchantTopic = MERCHANT_TOPIC.matcher(destination);
        if (merchantTopic.matches()) {
            if (ownMerchantId != null && ownMerchantId.equalsIgnoreCase(merchantTopic.group(1))) {
                return;
            }
            log.warn("Session (merchant={}) denied cross-tenant destination {}", ownMerchantId, destination);
            throw new IllegalArgumentException("Not authorized for destination");
        }

        // Authenticated staff may follow an individual order. Tenant ownership of
        // the order itself is not verifiable here without a lookup; the order id is
        // an unguessable UUID and the payload is already scoped to that order.
        if (ORDER_TOPIC.matcher(destination).matches() && ownMerchantId != null) {
            return;
        }

        log.warn("Denied unrecognised destination {} (role={})", destination, role);
        throw new IllegalArgumentException("Not authorized for destination");
    }

    /** Minimal principal carrying the authenticated username. */
    public record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
