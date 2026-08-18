package com.qrserve.notification.interceptor;

import com.qrserve.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Extracts and validates the JWT during the WebSocket handshake.
 *
 * <p>The token may be supplied either as the query-string parameter
 * {@code ?token=...} (browsers cannot set headers on a WebSocket
 * handshake) or as a standard {@code Authorization: Bearer ...}
 * header. On success the authenticated identity is stored in the
 * WebSocket session attributes so {@link StompAuthInterceptor} can
 * promote it to the STOMP principal.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USERNAME = "ws.username";
    public static final String ATTR_MERCHANT_ID = "ws.merchantId";
    public static final String ATTR_ROLE = "ws.role";
    /** Set only for anonymous guest sessions; scopes the session to one order. */
    public static final String ATTR_ORDER_ID = "ws.orderId";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String token = resolveToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("Rejected WebSocket handshake: missing or invalid token");
            return false;
        }

        // Anonymous guest tracking one order. Carries no role and no merchant, so
        // StompAuthInterceptor will permit only /topic/orders/{that id}.
        if (jwtTokenProvider.isOrderStreamToken(token)) {
            UUID orderId = jwtTokenProvider.getOrderIdFromToken(token);
            if (orderId == null) {
                log.warn("Rejected WebSocket handshake: order-stream token without an orderId");
                return false;
            }
            attributes.put(ATTR_USERNAME, "order:" + orderId);
            attributes.put(ATTR_ORDER_ID, orderId.toString());
            return true;
        }

        // Staff session. Refresh tokens are explicitly not accepted here.
        if (!jwtTokenProvider.isAccessToken(token)) {
            log.warn("Rejected WebSocket handshake: token type is not ACCESS");
            return false;
        }

        attributes.put(ATTR_USERNAME, jwtTokenProvider.getUsernameFromToken(token));
        attributes.put(ATTR_ROLE, jwtTokenProvider.getRoleFromToken(token).name());
        if (jwtTokenProvider.getMerchantIdFromToken(token) != null) {
            attributes.put(ATTR_MERCHANT_ID, jwtTokenProvider.getMerchantIdFromToken(token).toString());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (header != null && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
    }
}
