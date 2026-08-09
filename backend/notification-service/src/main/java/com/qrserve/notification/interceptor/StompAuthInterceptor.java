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

/**
 * Validates every inbound STOMP frame against the identity established
 * during the handshake by {@link JwtHandshakeInterceptor}.
 *
 * <p>On {@code CONNECT} the session attributes are promoted to a STOMP
 * {@link Principal}. Any frame arriving on a session without a principal
 * is rejected.</p>
 */
@Component
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

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

        if (StompCommand.SUBSCRIBE.equals(command)
                || StompCommand.SEND.equals(command)
                || StompCommand.ACK.equals(command)) {
            if (accessor.getUser() == null) {
                throw new IllegalArgumentException("Unauthenticated STOMP frame rejected: " + command);
            }
        }

        return message;
    }

    /** Minimal principal carrying the authenticated username. */
    public record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
