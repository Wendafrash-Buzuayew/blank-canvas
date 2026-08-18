import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { QueryKey, useQueryClient } from '@tanstack/react-query';
import { realtime, StompDestinations, NotificationEnvelope, ConnectionStatus } from '../lib/realtime';

/** Subscribe to a single STOMP destination. */
export function useStompSubscription(
  destination: string | null,
  handler: (env: NotificationEnvelope) => void
) {
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    if (!destination) return;
    return realtime.subscribe(destination, (env) => handlerRef.current(env));
  }, [destination]);
}

export function useRealtimeStatus(): ConnectionStatus {
  const [status, setStatus] = useState<ConnectionStatus>(realtime.getStatus());
  useEffect(() => realtime.onStatus(setStatus), []);
  return status;
}

/**
 * Refetch the given queries whenever the socket comes back.
 *
 * The broker does not replay missed messages to a new subscription, so every
 * event published during an outage is lost. Re-subscribing alone therefore
 * leaves the cache holding pre-outage data until the next poll — on a kitchen
 * board that is a silently wrong ticket list.
 */
function useReconnectResync(keys: QueryKey[]) {
  const queryClient = useQueryClient();
  const serialized = JSON.stringify(keys);

  useEffect(() => {
    const parsed: QueryKey[] = JSON.parse(serialized);
    return realtime.onReconnect(() => {
      parsed.forEach((queryKey) => queryClient.invalidateQueries({ queryKey }));
    });
  }, [queryClient, serialized]);
}

/**
 * Stable identity for an event, used to drop duplicates.
 *
 * Kafka delivers at-least-once and the Redis fan-out can deliver the same
 * envelope to more than one pod, so the same logical event can arrive twice.
 * Without this a single "table 4 needs a waiter" can raise two alerts.
 */
function eventKey(env: NotificationEnvelope): string {
  const p: Record<string, unknown> = (env.payload ?? {}) as Record<string, unknown>;
  const id =
    p.alertId ??
    p.eventId ??
    // An order event is identified by the order plus the state it moved to;
    // the same order legitimately appears many times as it progresses.
    (p.orderId ? `${p.orderId}:${p.newStatus ?? p.status ?? env.eventType}` : null) ??
    (p.requestId ? `req:${p.requestId}:${p.status ?? env.eventType}` : null);

  return id ? String(id) : `${env.eventType}:${env.traceId ?? ''}`;
}

export interface LiveEvent {
  id: string;
  eventType: string;
  payload: any;
  receivedAt: string;
}

function useEventBuffer(limit = 50) {
  const [events, setEvents] = useState<LiveEvent[]>([]);
  const seen = useRef<Set<string>>(new Set());
  const order = useRef<string[]>([]);

  const push = useCallback(
    (env: NotificationEnvelope): boolean => {
      const key = eventKey(env);
      if (seen.current.has(key)) return false;

      seen.current.add(key);
      order.current.push(key);
      // Bound the dedupe window so a long shift does not grow it without limit.
      if (order.current.length > limit * 4) {
        const evicted = order.current.shift();
        if (evicted) seen.current.delete(evicted);
      }

      setEvents((prev) =>
        [
          {
            id: key,
            eventType: env.eventType || 'EVENT',
            payload: env.payload,
            receivedAt: new Date().toISOString(),
          },
          ...prev,
        ].slice(0, limit)
      );
      return true;
    },
    [limit]
  );

  const clear = useCallback(() => {
    setEvents([]);
    seen.current.clear();
    order.current = [];
  }, []);

  return { events, push, clear };
}

/**
 * Live kitchen board stream. Invalidates the kitchen order queries so the
 * REST-backed list stays in sync with pushed events.
 */
export function useKitchenStream(merchantId?: string | null, branchId?: number | null) {
  const queryClient = useQueryClient();
  const { events, push, clear } = useEventBuffer();
  const destination = merchantId && branchId != null ? StompDestinations.kitchen(merchantId, branchId) : null;

  const keys = useMemo<QueryKey[]>(() => [['kitchen-orders']], []);
  useReconnectResync(keys);

  useStompSubscription(destination, (env) => {
    // A duplicate must not trigger a refetch either — it is the same news.
    if (!push(env)) return;
    queryClient.invalidateQueries({ queryKey: ['kitchen-orders'] });
    // Only reconcile the general order list for events that change it. Previously
    // every kitchen event invalidated ['orders'] too, doubling refetches on a
    // board that already polls.
    if (env.eventType === 'ORDER_CREATED' || env.eventType === 'ORDER_STATUS_UPDATED') {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    }
  });

  return { events, clear, status: useRealtimeStatus(), destination };
}

/** Live waiter alert stream (ORDER_READY, CALL_WAITER, REQUEST_BILL, ...). */
export function useWaiterStream(merchantId?: string | null, branchId?: number | null) {
  const queryClient = useQueryClient();
  const { events, push, clear } = useEventBuffer();
  const destination = merchantId && branchId != null ? StompDestinations.waiters(merchantId, branchId) : null;

  const keys = useMemo<QueryKey[]>(
    () => [['waiter-tasks'], ['customer-requests'], ['kitchen-orders']],
    []
  );
  useReconnectResync(keys);

  useStompSubscription(destination, (env) => {
    if (!push(env)) return;
    queryClient.invalidateQueries({ queryKey: ['waiter-tasks'] });
    queryClient.invalidateQueries({ queryKey: ['customer-requests'] });
    // ORDER_READY originates in the kitchen; other alert types do not touch it.
    if (env.eventType === 'ORDER_READY') {
      queryClient.invalidateQueries({ queryKey: ['kitchen-orders'] });
    }
  });

  return { events, clear, status: useRealtimeStatus(), destination };
}

/**
 * Live status stream for one order (used by the customer order tracker).
 *
 * @param orderId     the order to follow
 * @param streamToken anonymous token returned by POST /api/orders. A guest has no
 *                    JWT, so without it the WebSocket handshake is rejected and
 *                    this stream never connects. Logged-in staff can omit it —
 *                    their access token takes precedence.
 */
export function useOrderStream(orderId?: string | null, streamToken?: string | null) {
  const [status, setStatus] = useState<string | null>(null);
  const { events, push } = useEventBuffer(20);
  const destination = orderId ? StompDestinations.order(orderId) : null;

  useEffect(() => {
    if (!streamToken) return;
    realtime.setGuestToken(streamToken);
    return () => realtime.setGuestToken(null);
  }, [streamToken]);

  useStompSubscription(destination, (env) => {
    if (!push(env)) return;
    const next = (env.payload as any)?.status ?? (env.payload as any)?.newStatus;
    if (next) setStatus(String(next));
  });

  return { status, events, connection: useRealtimeStatus() };
}
