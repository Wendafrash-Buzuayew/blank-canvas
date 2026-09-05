import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import {
  ShoppingCart,
  Plus,
  Minus,
  ScanLine,
  X,
  Search,
  UtensilsCrossed,
  RefreshCw,
  MapPin,
  Sparkles,
} from 'lucide-react';
import {
  usePublicMenuResolution,
  usePublicMenu,
  useCreateOrder,
  useCreateTableRequest,
  usePublicOrderTracking,
} from '../hooks/useApiData';
import { useOrderStream } from '../hooks/useRealtime';
import { CartSheet, type CartLine } from '../components/customer/CartSheet';
import { OrderProgress } from '../components/customer/OrderProgress';
import { ServiceDock } from '../components/customer/ServiceDock';
import type { WaiterRequestType } from '../lib/api';
import { currentTenantSlug, resolveMenuTarget } from '../lib/tenant';
import { mostAdvancedStatus } from '../lib/orderStatus';
import {
  readTrackedOrder,
  writeTrackedOrder,
  type TrackedOrder,
} from '../lib/orderSession';
import { isPhase2Enabled } from '../lib/phase';
import { IdentityChip } from '../components/ui/Chip';

const MenuSkeleton: React.FC = () => (
  <div className="space-y-3" aria-hidden>
    {Array.from({ length: 5 }).map((_, i) => (
      <div key={i} className="flex gap-3 rounded-card bg-surface p-3 shadow-card">
        <div className="h-20 w-20 shrink-0 animate-pulse rounded-xl2 bg-line/70" />
        <div className="flex-1 space-y-2 py-1">
          <div className="h-3.5 w-2/5 animate-pulse rounded-pill bg-line/70" />
          <div className="h-3 w-4/5 animate-pulse rounded-pill bg-line/50" />
          <div className="h-3 w-1/4 animate-pulse rounded-pill bg-line/70" />
        </div>
      </div>
    ))}
  </div>
);

export const CustomerMenuPage: React.FC = () => {
  const params = useParams<{
    merchantSlug?: string;
    branchSlug?: string;
    tableNumber?: string;
  }>();
  const [searchParams] = useSearchParams();
  const signature = searchParams.get('signature') || undefined;
  const isQrDemo = searchParams.get('demo') === 'qr';
  const [showQrOverlay, setShowQrOverlay] = useState(isQrDemo);

  /**
   * The host label wins over the path parameter. On a tenant host the canonical
   * URL is /menu/{branchSlug}/{tableNumber}, which React Router matches against
   * /menu/:merchantSlug/:tableNumber - so without this the page would ask the
   * backend to resolve a merchant named after the branch.
   *
   * Legacy path-form links omit the branch slug; resolveMenuTarget defaults it.
   */
  const hostSlug = currentTenantSlug();
  const target = resolveMenuTarget(params, hostSlug);
  const effectiveBranchSlug = target?.branchSlug ?? 'main';

  const {
    data: resolution,
    isLoading: resolving,
    error: resolveError,
    refetch: refetchResolution,
  } = usePublicMenuResolution(target?.merchantSlug, effectiveBranchSlug, target?.tableNumber, signature);

  const {
    data: menu,
    isLoading: menuLoading,
    error: menuError,
    refetch: refetchMenu,
  } = usePublicMenu(resolution?.merchantId);

  const [cart, setCart] = useState<CartLine[]>([]);
  const [cartOpen, setCartOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [bumpKey, setBumpKey] = useState(0);
  /**
   * The order this guest is following, including the anonymous stream token that
   * the realtime handshake and the status poll both need (a guest has no JWT).
   *
   * Held in one record because it is persisted as one: it used to live in three
   * pieces of component state, so a browser refresh erased the tracker entirely
   * even though the token stays valid for hours.
   */
  const [tracked, setTracked] = useState<TrackedOrder | null>(null);
  const [requestSent, setRequestSent] = useState<WaiterRequestType | null>(null);

  const createOrder = useCreateOrder();
  const createRequest = useCreateTableRequest();

  // Restore only once the table is known, so a record saved at another table can
  // never flash onto this one.
  useEffect(() => {
    if (!resolution) return;
    const restored = readTrackedOrder({
      merchantId: resolution.merchantId,
      tableId: resolution.tableId,
    });
    setTracked((current) => (current && current.orderId === restored?.orderId ? current : restored));
  }, [resolution?.merchantId, resolution?.tableId]);

  const { data: trackedOrder } = usePublicOrderTracking(tracked?.orderId, tracked?.streamToken);
  const { status: pushedStatus, connection } = useOrderStream(tracked?.orderId, tracked?.streamToken);

  /**
   * Two channels report the same order, and either can be momentarily behind: the
   * poll is up to ten seconds stale, the socket misses anything published before
   * it subscribed. Take the furthest-along so the guest's progress only ever moves
   * forward. The stored status covers the third case — the first paint after a
   * refresh, before either channel has answered.
   */
  const liveStatus = mostAdvancedStatus(pushedStatus, trackedOrder?.status, tracked?.status);
  const placedOrderId = tracked?.orderId ?? null;
  const placedOrderNumber = trackedOrder?.orderNumber || tracked?.orderNumber || null;

  // Keep the stored copy current so the next refresh starts from the real status
  // rather than from "Received". savedAt is carried over untouched: the record
  // must expire with the token, not with the last update.
  useEffect(() => {
    if (!tracked || !liveStatus || liveStatus === tracked.status) return;
    writeTrackedOrder({ ...tracked, status: liveStatus });
  }, [liveStatus, tracked]);

  const currency = resolution?.currency || 'ETB';
  const sectionRefs = useRef<Record<number, HTMLElement | null>>({});

  const categories = useMemo(() => {
    const list = menu?.categories ?? [];
    if (!query.trim()) return list;
    const q = query.trim().toLowerCase();
    return list
      .map((c) => ({ ...c, items: c.items.filter((i) => `${i.name} ${i.description}`.toLowerCase().includes(q)) }))
      .filter((c) => c.items.length > 0);
  }, [menu, query]);

  const totalItems = useMemo(
    () => (menu?.categories ?? []).reduce((n, c) => n + c.items.length, 0),
    [menu]
  );

  // Scroll-spy for the sticky category rail.
  useEffect(() => {
    const ids = categories.map((c) => c.id);
    if (ids.length === 0) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
        if (visible) setActiveCategory(Number((visible.target as HTMLElement).dataset.categoryId));
      },
      { rootMargin: '-140px 0px -65% 0px', threshold: 0 }
    );
    ids.forEach((id) => {
      const el = sectionRefs.current[id];
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, [categories]);

  const cartCount = cart.reduce((n, l) => n + l.quantity, 0);
  const cartTotal = cart.reduce((s, l) => s + l.price * l.quantity, 0);

  const changeQty = (item: { id: number; name: string; price: number }, delta: number) => {
    setBumpKey((k) => k + 1);
    setCart((prev) => {
      const existing = prev.find((l) => l.productId === item.id);
      if (!existing) {
        return delta > 0
          ? [...prev, { productId: item.id, name: item.name, price: Number(item.price), quantity: 1 }]
          : prev;
      }
      const quantity = existing.quantity + delta;
      if (quantity <= 0) return prev.filter((l) => l.productId !== item.id);
      return prev.map((l) => (l.productId === item.id ? { ...l, quantity } : l));
    });
  };

  const placeOrder = () => {
    if (!resolution || cart.length === 0) return;
    createOrder.mutate(
      {
        tableId: resolution.tableId,
        items: cart.map((l) => ({ productId: l.productId, quantity: l.quantity })),
      },
      {
        onSuccess: (res) => {
          const record: TrackedOrder = {
            orderId: res.id,
            orderNumber: res.orderNumber,
            // Seed from the response instead of assuming the first step: the
            // tracker then shows the real status even if no event ever arrives.
            status: res.status ?? null,
            streamToken: res.streamToken ?? null,
            merchantId: resolution.merchantId,
            tableId: resolution.tableId,
            savedAt: Date.now(),
          };
          writeTrackedOrder(record);
          setTracked(record);
          setCart([]);
          setCartOpen(false);
          window.scrollTo({ top: 0, behavior: 'smooth' });
        },
      }
    );
  };

  const sendRequest = (requestType: WaiterRequestType) => {
    if (!resolution) return;
    createRequest.mutate(
      {
        tableId: resolution.tableId,
        requestType,
        signature,
      },
      {
        onSuccess: () => {
          setRequestSent(requestType);
          window.setTimeout(() => setRequestSent(null), 6000);
        },
      }
    );
  };

  const jumpTo = (id: number) => {
    const el = sectionRefs.current[id];
    if (!el) return;
    const top = el.getBoundingClientRect().top + window.scrollY - 132;
    window.scrollTo({ top, behavior: 'smooth' });
  };

  /* ---------------- states ---------------- */

  if (target?.hostMismatch) {
    // The link named one restaurant and the address bar another. Serving the host
    // silently would show the guest a different menu than the link promised, and
    // the backend 403s the request anyway.
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-6">
        <div className="w-full max-w-sm rounded-card bg-surface p-8 text-center shadow-card">
          <h1 className="text-lg font-semibold">This link is for a different restaurant</h1>
          <p className="mt-2 text-sm text-muted">
            Please scan the QR code on your table again.
          </p>
        </div>
      </div>
    );
  }

  if (resolving) {
    return (
      <div className="min-h-screen bg-canvas px-5 pt-16">
        <div className="mx-auto max-w-2xl space-y-6">
          <div className="h-28 animate-pulse rounded-card bg-line/60" />
          <MenuSkeleton />
          <p className="text-center text-xs text-muted">Finding your table…</p>
        </div>
      </div>
    );
  }

  if (resolveError || !resolution) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-6">
        <div className="w-full max-w-sm rounded-card bg-surface p-8 text-center shadow-card">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-xl2 bg-brand-soft">
            <ScanLine className="h-7 w-7 text-brand" aria-hidden />
          </div>
          <h1 className="mt-4 font-display text-xl font-extrabold">We couldn’t open this table</h1>
          <p className="mt-2 text-sm text-muted">
            {isPhase2Enabled() ? (
              <>
                The QR code for <strong className="text-ink">table {target?.tableNumber ?? '?'}</strong> didn’t resolve. It may have been
                replaced or the link is incomplete.
              </>
            ) : (
              'This QR code didn’t resolve. It may have been replaced or the link is incomplete.'
            )}
          </p>
          <button
            onClick={() => refetchResolution()}
            className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-control bg-brand-dark py-3.5 text-label-m text-brand-fg"
          >
            <RefreshCw className="h-4 w-4" aria-hidden /> Try again
          </button>
          <p className="mt-3 text-xs text-muted">Still stuck? Please ask a member of staff for help.</p>
        </div>
      </div>
    );
  }

  if (showQrOverlay) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-ink p-6">
        <div className="w-full max-w-sm space-y-6 text-center">
          <div className="mx-auto flex h-24 w-24 items-center justify-center rounded-card bg-white/10 ring-2 ring-white/15">
            <ScanLine className="h-12 w-12 animate-breathe text-brand" aria-hidden />
          </div>
          <div>
            <h1 className="font-display text-2xl font-extrabold text-on-ink">QR scanned</h1>
            <p className="mt-2 text-sm text-on-ink-muted">
              You’re at <strong className="text-on-ink">Table {resolution.tableNumber}</strong>
            </p>
          </div>
          <div className="rounded-card bg-white/5 p-4 text-left ring-1 ring-white/10">
            <p className="text-label-s font-semibold uppercase tracking-[0.14em] text-on-ink-muted">Detected</p>
            <p className="mt-1 font-display text-lg font-extrabold text-on-ink">
              {resolution.merchantName || target?.merchantSlug}
            </p>
            <p className="text-xs text-on-ink-muted">
              {resolution.branchName || effectiveBranchSlug} · Table {resolution.tableNumber}
            </p>
          </div>
          <button
            onClick={() => setShowQrOverlay(false)}
            className="flex w-full items-center justify-center gap-2 rounded-control bg-brand-dark py-4 text-label-m text-brand-fg shadow-[var(--shadow-lift)]"
          >
            <X className="h-4 w-4" aria-hidden /> Open the menu
          </button>
        </div>
      </div>
    );
  }

  /* ---------------- menu ---------------- */

  return (
    // data-view="customer" activates --customer-accent-warm, which is the third
    // stop of --gradient-brand. It is scoped rather than global because the value
    // is perceptually identical to --color-kds-prep.
    <div data-view="customer" className="min-h-screen bg-canvas pb-36">
      {/* Header */}
      <header className="bg-ink px-5 pb-8 pt-7 text-on-ink">
        <div className="mx-auto max-w-2xl">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h1 className="truncate font-display text-2xl font-extrabold">
                {resolution.merchantName || target?.merchantSlug}
              </h1>
              <p className="mt-1 flex items-center gap-1.5 text-xs text-on-ink-muted">
                <MapPin className="h-3.5 w-3.5" aria-hidden />
                {resolution.branchName || effectiveBranchSlug}
              </p>
            </div>
            {isPhase2Enabled() && (
              <span className="shrink-0 rounded-pill bg-white/10 px-3 py-1.5 text-xs font-bold ring-1 ring-white/15">
                Table {resolution.tableNumber}
              </span>
            )}
          </div>
          <p className="mt-3 flex items-center gap-1.5 text-xs text-on-ink-muted">
            <Sparkles className="h-3.5 w-3.5" aria-hidden />
            {isPhase2Enabled() ? 'Order from your table — no app, no queue.' : 'Browse our menu.'}
          </p>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-5">
        {/* Live tracker */}
        {isPhase2Enabled() && placedOrderId && (
          <div className="-mt-5">
            <OrderProgress orderNumber={placedOrderNumber} status={liveStatus} connection={connection} />
          </div>
        )}

        {/* Search + sticky category rail */}
        <div className={isPhase2Enabled() && placedOrderId ? 'mt-5' : '-mt-5'}>
          <label className="relative block">
            <span className="sr-only">Search the menu</span>
            <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search dishes…"
              className="w-full rounded-control bg-surface py-3.5 pl-11 pr-4 text-body-m shadow-card outline-none ring-1 ring-line placeholder:text-muted focus:ring-2 focus:ring-brand-dark"
            />
          </label>
        </div>

        {categories.length > 0 && (
          <nav
            aria-label="Menu categories"
            className="no-scrollbar sticky top-0 z-30 -mx-5 mt-4 flex gap-2 overflow-x-auto bg-canvas/90 px-5 py-3 backdrop-blur"
          >
            {categories.map((c) => (
              <button
                key={c.id}
                onClick={() => jumpTo(c.id)}
                aria-current={activeCategory === c.id}
                className={`shrink-0 rounded-pill px-4 py-2 text-label-m transition-colors ${
                  activeCategory === c.id
                    ? 'bg-ink text-on-ink'
                    : 'bg-surface text-ink ring-1 ring-line hover:bg-surface-2'
                }`}
              >
                {c.name}
              </button>
            ))}
          </nav>
        )}

        {/* Menu body */}
        <div className="mt-4 space-y-8">
          {menuLoading && <MenuSkeleton />}

          {menuError && !menuLoading && (
            <div className="rounded-card bg-surface p-8 text-center shadow-card">
              <UtensilsCrossed className="mx-auto h-10 w-10 stroke-[1.25] text-line" aria-hidden />
              <h2 className="mt-3 font-display text-lg font-extrabold">The menu didn’t load</h2>
              <p className="mt-1 text-sm text-muted">This is on us, not you. Give it another try.</p>
              <button
                onClick={() => refetchMenu()}
                className="mt-4 inline-flex items-center gap-2 rounded-control bg-brand-dark px-5 py-3 text-label-m text-brand-fg"
              >
                <RefreshCw className="h-4 w-4" aria-hidden /> Reload menu
              </button>
            </div>
          )}

          {!menuLoading && !menuError && totalItems === 0 && (
            <div className="rounded-card bg-surface p-8 text-center shadow-card">
              <UtensilsCrossed className="mx-auto h-10 w-10 stroke-[1.25] text-line" aria-hidden />
              <h2 className="mt-3 font-display text-lg font-extrabold">Nothing on the menu yet</h2>
              <p className="mt-1 text-sm text-muted">
                This table isn’t serving from the digital menu right now — please ask your waiter.
              </p>
            </div>
          )}

          {!menuLoading && !menuError && totalItems > 0 && categories.length === 0 && (
            <div className="rounded-card bg-surface p-8 text-center shadow-card">
              <Search className="mx-auto h-10 w-10 stroke-[1.25] text-line" aria-hidden />
              <h2 className="mt-3 font-display text-lg font-extrabold">No dishes match “{query}”</h2>
              <button onClick={() => setQuery('')} className="mt-4 text-sm font-bold text-brand underline">
                Clear search
              </button>
            </div>
          )}

          {categories.map((cat) => (
            <section
              key={cat.id}
              data-category-id={cat.id}
              ref={(el) => {
                sectionRefs.current[cat.id] = el;
              }}
              aria-labelledby={`cat-${cat.id}`}
            >
              <h2 id={`cat-${cat.id}`} className="mb-3 font-display text-lg font-extrabold">
                {cat.name}
                <span className="ml-2 text-xs font-semibold text-muted">{cat.items.length}</span>
              </h2>
              <div className="space-y-3">
                {cat.items.map((item) => {
                  const line = cart.find((l) => l.productId === item.id);
                  const soldOut = item.available === false;
                  return (
                    // DESIGN.md §6.11: unavailable items stay visible at 50%
                    // opacity with an Unavailable identity chip and no add
                    // control - not hidden, and not merely dimmed slightly.
                    <article
                      key={item.id}
                      className={`card-surface flex gap-3 p-3 transition-shadow ${
                        soldOut ? 'opacity-50' : ''
                      }`}
                    >
                      <div className="relative h-20 w-20 shrink-0 overflow-hidden rounded-xl2 bg-canvas">
                        {item.image ? (
                          <img
                            src={item.image}
                            alt={item.name}
                            loading="lazy"
                            className={`h-full w-full object-cover ${soldOut ? 'grayscale' : ''}`}
                          />
                        ) : (
                          <div className="flex h-full w-full items-center justify-center">
                            <UtensilsCrossed className="h-6 w-6 text-line" aria-hidden />
                          </div>
                        )}
                      </div>

                      <div className="flex min-w-0 flex-1 flex-col justify-between">
                        <div>
                          <h3 className="text-title-s leading-tight">{item.name}</h3>
                          {item.description && (
                            <p className="mt-0.5 line-clamp-2 text-body-m leading-relaxed text-muted">{item.description}</p>
                          )}
                        </div>
                        <div className="mt-2 flex items-end justify-between gap-2">
                          {/* brand-press, not brand-dark/brand: DESIGN.md §6.11
                              and §3.1 - both of those fail AA at this size. */}
                          <span className="font-display text-label-m text-brand-press [font-variant-numeric:tabular-nums]">
                            {Number(item.price).toLocaleString()}{' '}
                            <span className="text-label-s text-muted">{currency}</span>
                          </span>

                          {!isPhase2Enabled() ? null : soldOut ? (
                            <IdentityChip>Unavailable</IdentityChip>
                          ) : line ? (
                            <div className="flex items-center gap-1 rounded-pill bg-canvas p-1">
                              <button
                                onClick={() => changeQty(item, -1)}
                                aria-label={`Remove one ${item.name}`}
                                className="flex h-11 w-11 items-center justify-center rounded-pill bg-surface shadow-[var(--shadow-card)] transition-transform active:scale-90"
                              >
                                <Minus className="h-4 w-4" aria-hidden />
                              </button>
                              <span className="w-6 text-center text-label-m [font-variant-numeric:tabular-nums]">{line.quantity}</span>
                              <button
                                onClick={() => changeQty(item, 1)}
                                aria-label={`Add one ${item.name}`}
                                className="flex h-11 w-11 items-center justify-center rounded-pill bg-brand-dark text-brand-fg transition-transform active:scale-90"
                              >
                                <Plus className="h-4 w-4" aria-hidden />
                              </button>
                            </div>
                          ) : (
                            // DESIGN.md §6.11: add control is brand-dark fill
                            // with a brand-fg glyph, radius-pill, >=44.
                            <button
                              onClick={() => changeQty(item, 1)}
                              className="flex h-11 items-center gap-1.5 rounded-pill bg-brand-dark px-4 text-label-m text-brand-fg transition-transform active:scale-95"
                            >
                              <Plus className="h-4 w-4" aria-hidden /> Add
                            </button>
                          )}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      </main>

      {isPhase2Enabled() && (
        <ServiceDock
          onRequest={sendRequest}
          pending={createRequest.isPending}
          sentType={requestSent}
          failed={createRequest.isError}
          offsetClass={cartCount > 0 ? 'bottom-28' : 'bottom-6'}
        />
      )}

      {/* Cart bar */}
      {isPhase2Enabled() && cartCount > 0 && (
        <div className="safe-b fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 px-5 pt-3 backdrop-blur">
          <div className="mx-auto max-w-2xl">
            <button
              onClick={() => setCartOpen(true)}
              className="flex w-full items-center justify-between rounded-control bg-brand-dark px-5 py-4 text-brand-fg shadow-[var(--shadow-lift)] transition-transform active:scale-[0.99]"
            >
              <span className="flex items-center gap-2.5 text-label-m">
                <span key={bumpKey} className="relative flex animate-pop items-center">
                  <ShoppingCart className="h-5 w-5" aria-hidden />
                  <span className="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-pill bg-surface px-1 text-label-s text-brand-press">
                    {cartCount}
                  </span>
                </span>
                Review order
              </span>
              <span className="font-display text-label-m [font-variant-numeric:tabular-nums]">
                {cartTotal.toLocaleString()} {currency}
              </span>
            </button>
          </div>
        </div>
      )}

      {isPhase2Enabled() && (
        <CartSheet
          open={cartOpen}
          onClose={() => setCartOpen(false)}
          lines={cart}
          currency={currency}
          tableNumber={resolution.tableNumber}
          onChangeQty={(id, d) => {
            const l = cart.find((x) => x.productId === id);
            if (l) changeQty({ id, name: l.name, price: l.price }, d);
          }}
          onRemove={(id) => setCart((prev) => prev.filter((l) => l.productId !== id))}
          onConfirm={placeOrder}
          submitting={createOrder.isPending}
          errorMessage={
            createOrder.isError
              ? 'We couldn’t send your order. Check your connection and try again, or ask a waiter.'
              : null
          }
        />
      )}
    </div>
  );
};
