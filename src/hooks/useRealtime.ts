import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
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

export interface LiveEvent {
  id: string;
  eventType: string;
  payload: any;
  receivedAt: string;
}

function useEventBuffer(limit = 50) {
  const [events, setEvents] = useState<LiveEvent[]>([]);
  const push = useCallback(
    (env: NotificationEnvelope) => {
      setEvents((prev) =>
        [
          {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            eventType: env.eventType || 'EVENT',
            payload: env.payload,
            receivedAt: new Date().toISOString(),
          },
          ...prev,
        ].slice(0, limit)
      );
    },
    [limit]
  );
  const clear = useCallback(() => setEvents([]), []);
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

  useStompSubscription(destination, (env) => {
    push(env);
    queryClient.invalidateQueries({ queryKey: ['kitchen-orders'] });
    queryClient.invalidateQueries({ queryKey: ['orders'] });
  });

  return { events, clear, status: useRealtimeStatus(), destination };
}

/** Live waiter alert stream (ORDER_READY, CALL_WAITER, REQUEST_BILL, ...). */
export function useWaiterStream(merchantId?: string | null, branchId?: number | null) {
  const queryClient = useQueryClient();
  const { events, push, clear } = useEventBuffer();
  const destination = merchantId && branchId != null ? StompDestinations.waiters(merchantId, branchId) : null;

  useStompSubscription(destination, (env) => {
    push(env);
    queryClient.invalidateQueries({ queryKey: ['waiter-tasks'] });
    queryClient.invalidateQueries({ queryKey: ['customer-requests'] });
    queryClient.invalidateQueries({ queryKey: ['kitchen-orders'] });
  });

  return { events, clear, status: useRealtimeStatus(), destination };
}

/** Live status stream for one order (used by the customer order tracker). */
export function useOrderStream(orderId?: string | null) {
  const [status, setStatus] = useState<string | null>(null);
  const { events, push } = useEventBuffer(20);
  const destination = orderId ? StompDestinations.order(orderId) : null;

  useStompSubscription(destination, (env) => {
    push(env);
    const next = env.payload?.status || env.payload?.newStatus;
    if (next) setStatus(String(next));
  });

  return { status, events, connection: useRealtimeStatus() };
}
