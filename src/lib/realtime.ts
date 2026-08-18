/**
 * Real-time STOMP client for the QRServe notification-service.
 *
 * Backend contract (notification-service):
 *   - SockJS endpoint:            /ws        (JwtHandshakeInterceptor + StompAuthInterceptor)
 *   - Broker prefix:              /topic
 *   - Kitchen board:              /topic/merchant/{merchantId}/branch/{branchId}/kitchen
 *   - Waiter alerts:              /topic/merchant/{merchantId}/branch/{branchId}/waiters
 *   - Per-order status stream:    /topic/orders/{orderId}
 *
 * Every message is a NotificationEnvelope:
 *   { destination, eventType, payload, traceId, spanId }
 */
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getAuthToken } from './api';

export interface NotificationEnvelope<T = any> {
  destination: string;
  eventType: string;
  payload: T;
  traceId?: string;
  spanId?: string;
}

export const StompDestinations = {
  kitchen: (merchantId: string, branchId: number | string) =>
    `/topic/merchant/${merchantId}/branch/${branchId}/kitchen`,
  waiters: (merchantId: string, branchId: number | string) =>
    `/topic/merchant/${merchantId}/branch/${branchId}/waiters`,
  order: (orderId: string) => `/topic/orders/${orderId}`,
};

export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error';

type StatusListener = (status: ConnectionStatus) => void;

function resolveWsUrl(): string {
  const explicit = import.meta.env.VITE_WS_URL as string | undefined;
  if (explicit) return explicit;
  // Same-origin: the Vite dev server proxies /ws to the API gateway.
  return `${window.location.origin}/ws`;
}

class RealtimeClient {
  private client: Client | null = null;
  private status: ConnectionStatus = 'idle';
  private statusListeners = new Set<StatusListener>();
  /** destination -> set of handlers */
  private handlers = new Map<string, Set<(env: NotificationEnvelope) => void>>();
  private subscriptions = new Map<string, StompSubscription>();
  private reconnectListeners = new Set<() => void>();
  /** Set once we have connected, so the first connect does not count as a reconnect. */
  private hasConnectedOnce = false;
  /**
   * Anonymous order-stream token, used when there is no logged-in user. A guest
   * who scanned a QR code has no JWT, so without this the customer order tracker
   * cannot complete the handshake at all.
   */
  private guestToken: string | null = null;

  getStatus(): ConnectionStatus {
    return this.status;
  }

  onStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.status);
    return () => this.statusListeners.delete(listener);
  }

  /**
   * Fires after the socket comes back, not on the first connect.
   *
   * <p>Re-subscribing does not replay what was missed, so anything cached while
   * the socket was down is stale. Listeners should refetch. Without this the UI
   * silently showed pre-outage data until the next poll.
   */
  onReconnect(listener: () => void): () => void {
    this.reconnectListeners.add(listener);
    return () => this.reconnectListeners.delete(listener);
  }

  /**
   * Supplies the anonymous token for a guest session. Pass null on teardown.
   * Changing the token drops the connection so the next handshake uses it.
   */
  setGuestToken(token: string | null) {
    if (this.guestToken === token) return;
    this.guestToken = token;
    // Tear down the socket but KEEP handlers, so existing subscriptions are
    // re-established under the new token. Calling disconnect() here would drop
    // them and silently stop delivering to already-mounted components.
    const client = this.client;
    if (client) {
      this.subscriptions.clear();
      this.client = null;
      this.hasConnectedOnce = false;
      client.deactivate().catch(() => {});
      if (this.handlers.size > 0) {
        this.setStatus('connecting');
        this.ensureClient().activate();
      } else {
        this.setStatus('idle');
      }
    }
  }

  /** Auth token wins: a logged-in waiter must not connect as a guest. */
  private resolveToken(): string | null {
    return getAuthToken() || this.guestToken;
  }

  private setStatus(status: ConnectionStatus) {
    this.status = status;
    this.statusListeners.forEach((l) => l(status));
  }

  private ensureClient(): Client {
    if (this.client) return this.client;

    const url = resolveWsUrl();

    const client = new Client({
      webSocketFactory: () => {
        const token = this.resolveToken();
        return new SockJS(token ? `${url}?token=${encodeURIComponent(token)}` : url) as any;
      },
      beforeConnect: () => {
        const token = this.resolveToken();
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    client.onConnect = () => {
      this.setStatus('connected');
      this.handlers.forEach((_set, destination) => this.subscribeNative(destination));

      // Events published while we were disconnected are gone: the broker does not
      // replay to a new subscription. Tell listeners to refetch so the cache is
      // reconciled with reality instead of waiting for the next poll interval.
      if (this.hasConnectedOnce) {
        this.reconnectListeners.forEach((l) => {
          try {
            l();
          } catch (err) {
            console.error('[realtime] reconnect listener failed', err);
          }
        });
      }
      this.hasConnectedOnce = true;
    };
    client.onStompError = () => this.setStatus('error');
    client.onWebSocketClose = () => {
      this.subscriptions.clear();
      if (this.status !== 'idle') this.setStatus('disconnected');
    };

    this.client = client;
    return client;
  }

  private subscribeNative(destination: string) {
    if (!this.client?.connected || this.subscriptions.has(destination)) return;
    const sub = this.client.subscribe(destination, (message: IMessage) => {
      let envelope: NotificationEnvelope;
      try {
        envelope = JSON.parse(message.body);
      } catch {
        envelope = { destination, eventType: 'UNKNOWN', payload: message.body };
      }
      this.handlers.get(destination)?.forEach((h) => {
        try {
          h(envelope);
        } catch (err) {
          console.error('[realtime] handler failed for', destination, err);
        }
      });
    });
    this.subscriptions.set(destination, sub);
  }

  subscribe(destination: string, handler: (env: NotificationEnvelope) => void): () => void {
    const client = this.ensureClient();

    if (!this.handlers.has(destination)) this.handlers.set(destination, new Set());
    this.handlers.get(destination)!.add(handler);

    if (!client.active) {
      this.setStatus('connecting');
      client.activate();
    } else {
      this.subscribeNative(destination);
    }

    return () => {
      const set = this.handlers.get(destination);
      set?.delete(handler);
      if (set && set.size === 0) {
        this.handlers.delete(destination);
        this.subscriptions.get(destination)?.unsubscribe();
        this.subscriptions.delete(destination);
      }
      if (this.handlers.size === 0) this.disconnect();
    };
  }

  /** Drop the connection — call on logout so a new JWT is used next time. */
  disconnect() {
    this.subscriptions.clear();
    this.handlers.clear();
    const client = this.client;
    this.client = null;
    this.hasConnectedOnce = false;
    this.setStatus('idle');
    client?.deactivate().catch(() => {});
  }
}

export const realtime = new RealtimeClient();
