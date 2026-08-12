import React, { useMemo, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { Bell, Droplets, Receipt, ShoppingCart, Minus, Plus, CheckCircle2, Radio, ScanLine, X } from 'lucide-react';
import { Spinner, ErrorState } from '../components/ui/States';
import { usePublicMenuResolution, usePublicMenu, useCreateOrder, useCreateTableRequest } from '../hooks/useApiData';
import { useOrderStream } from '../hooks/useRealtime';
import type { WaiterRequestType } from '../lib/api';

interface CartLine {
  productId: number;
  name: string;
  price: number;
  quantity: number;
}

const REQUEST_BUTTONS: { type: WaiterRequestType; label: string; icon: React.ElementType }[] = [
  { type: 'CALL_WAITER', label: 'Call waiter', icon: Bell },
  { type: 'REQUEST_WATER', label: 'Water', icon: Droplets },
  { type: 'REQUEST_BILL', label: 'Bill', icon: Receipt },
];

export const CustomerMenuPage: React.FC = () => {
  const { merchantSlug, branchSlug, tableNumber } = useParams<{
    merchantSlug: string;
    branchSlug?: string;
    tableNumber: string;
  }>();
  const [searchParams] = useSearchParams();
  const signature = searchParams.get('signature') || undefined;
  const isQrDemo = searchParams.get('demo') === 'qr';
  const [showQrOverlay, setShowQrOverlay] = useState(isQrDemo);

  // Legacy QR links omit the branch slug; the backend contract expects one.
  const effectiveBranchSlug = branchSlug || 'main';

  const {
    data: resolution,
    isLoading: resolving,
    error: resolveError,
  } = usePublicMenuResolution(merchantSlug, effectiveBranchSlug, tableNumber, signature);

  const { data: menu, isLoading: menuLoading, error: menuError } = usePublicMenu(resolution?.merchantId);

  const [cart, setCart] = useState<CartLine[]>([]);
  const [placedOrderId, setPlacedOrderId] = useState<string | null>(null);
  const [placedOrderNumber, setPlacedOrderNumber] = useState<string | null>(null);
  const [requestSent, setRequestSent] = useState<string | null>(null);

  const createOrder = useCreateOrder();
  const createRequest = useCreateTableRequest();
  const { status: liveStatus } = useOrderStream(placedOrderId);

  const total = useMemo(() => cart.reduce((sum, l) => sum + l.price * l.quantity, 0), [cart]);

  const changeQty = (item: { id: number; name: string; price: number }, delta: number) => {
    setCart((prev) => {
      const existing = prev.find((l) => l.productId === item.id);
      if (!existing) return delta > 0 ? [...prev, { productId: item.id, name: item.name, price: item.price, quantity: 1 }] : prev;
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
          setPlacedOrderId(res.id);
          setPlacedOrderNumber(res.orderNumber);
          setCart([]);
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
        merchantId: resolution.merchantId,
        branchId: resolution.branchId,
      },
      { onSuccess: () => setRequestSent(requestType) }
    );
  };

  if (resolving) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <Spinner label="Loading menu..." />
      </div>
    );
  }

  if (resolveError || !resolution) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <div className="max-w-md w-full">
          <ErrorState
            message={`This QR code could not be resolved (${merchantSlug}/${effectiveBranchSlug}/${tableNumber}). ${
              (resolveError as Error | undefined)?.message || ''
            }`}
          />
        </div>
      </div>
    );
  }

  if (showQrOverlay) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center p-6">
        <div className="max-w-sm w-full text-center space-y-6">
          <div className="w-24 h-24 mx-auto rounded-3xl bg-white/10 border-2 border-white/20 flex items-center justify-center">
            <ScanLine className="w-12 h-12 text-emerald-400 animate-pulse" />
          </div>
          <div>
            <h2 className="text-xl font-black text-white">QR Code Scanned!</h2>
            <p className="text-sm text-slate-400 mt-2">
              Simulating a customer scanning the QR stand at <strong className="text-white">Table {resolution.tableNumber}</strong>
            </p>
          </div>
          <div className="bg-white/5 border border-white/10 rounded-2xl p-4 text-left space-y-2">
            <div className="text-xs font-bold text-slate-300">Detected:</div>
            <div className="text-sm font-black text-white">{resolution.merchantName || merchantSlug}</div>
            <div className="text-xs text-slate-400">{resolution.branchName || effectiveBranchSlug} · Table {resolution.tableNumber}</div>
          </div>
          <button
            onClick={() => setShowQrOverlay(false)}
            className="w-full py-3.5 bg-[#E60028] hover:bg-[#CC0024] text-white font-bold text-sm rounded-2xl shadow-lg flex items-center justify-center gap-2 transition-colors"
          >
            <X className="w-4 h-4" />
            Open Digital Menu
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 pb-32">
      <header className="bg-gradient-to-r from-slate-900 to-indigo-900 text-white px-5 py-6">
        <h1 className="text-xl font-black">{resolution.merchantName || merchantSlug}</h1>
        <p className="text-xs text-slate-300 mt-1">
          {resolution.branchName || effectiveBranchSlug} · Table {resolution.tableNumber}
        </p>
      </header>

      {/* Service requests */}
      <section className="px-5 -mt-4">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 grid grid-cols-3 gap-2">
          {REQUEST_BUTTONS.map(({ type, label, icon: Icon }) => (
            <button
              key={type}
              onClick={() => sendRequest(type)}
              disabled={createRequest.isPending}
              className="flex flex-col items-center gap-1 py-3 rounded-xl border border-slate-200 hover:bg-slate-50 disabled:opacity-50"
            >
              <Icon className="w-4 h-4 text-[#E60028]" />
              <span className="text-[11px] font-bold text-slate-700">{label}</span>
            </button>
          ))}
        </div>
        {requestSent && (
          <p className="text-[11px] text-emerald-700 font-bold mt-2 flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5" /> A waiter has been notified.
          </p>
        )}
      </section>

      {/* Live order tracker */}
      {placedOrderId && (
        <section className="px-5 mt-4">
          <div className="bg-emerald-50 border border-emerald-200 rounded-2xl p-4">
            <div className="flex items-center gap-2 text-xs font-black text-emerald-800">
              <Radio className="w-3.5 h-3.5" />
              Order #{placedOrderNumber} — {liveStatus || 'CREATED'}
            </div>
            <p className="text-[11px] text-emerald-700 mt-1">Status updates arrive live from the kitchen.</p>
          </div>
        </section>
      )}

      {/* Menu */}
      <section className="px-5 mt-6 space-y-6">
        {menuLoading && <Spinner label="Loading dishes..." />}
        {menuError && <ErrorState message={`Menu unavailable: ${(menuError as Error).message}`} />}
        {menu?.categories?.map((cat) => (
          <div key={cat.id}>
            <h2 className="text-sm font-black text-slate-900 mb-2">{cat.name}</h2>
            <div className="space-y-2">
              {cat.items.map((item) => {
                const line = cart.find((l) => l.productId === item.id);
                return (
                  <article
                    key={item.id}
                    className="flex items-center gap-3 bg-white rounded-2xl border border-slate-200 p-3"
                  >
                    {item.image && (
                      <img src={item.image} alt={item.name} loading="lazy" className="w-14 h-14 rounded-xl object-cover" />
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-bold text-slate-900 truncate">{item.name}</div>
                      <p className="text-[11px] text-slate-500 line-clamp-2">{item.description}</p>
                      <div className="text-xs font-black text-slate-900 mt-1">
                        {Number(item.price).toLocaleString()} {resolution.currency || 'ETB'}
                      </div>
                    </div>
                    {item.available === false ? (
                      <span className="text-[10px] font-bold text-slate-400">Sold out</span>
                    ) : (
                      <div className="flex items-center gap-2">
                        {line && (
                          <>
                            <button
                              onClick={() => changeQty(item, -1)}
                              aria-label={`Remove one ${item.name}`}
                              className="w-7 h-7 rounded-lg border border-slate-200 flex items-center justify-center"
                            >
                              <Minus className="w-3.5 h-3.5" />
                            </button>
                            <span className="text-xs font-black w-4 text-center">{line.quantity}</span>
                          </>
                        )}
                        <button
                          onClick={() => changeQty(item, 1)}
                          aria-label={`Add one ${item.name}`}
                          className="w-7 h-7 rounded-lg bg-slate-900 text-white flex items-center justify-center"
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          </div>
        ))}
      </section>

      {/* Cart bar */}
      {cart.length > 0 && (
        <div className="fixed bottom-0 inset-x-0 bg-white border-t border-slate-200 p-4">
          <button
            onClick={placeOrder}
            disabled={createOrder.isPending}
            className="w-full flex items-center justify-between bg-slate-900 text-white rounded-xl px-4 py-3 disabled:opacity-50"
          >
            <span className="flex items-center gap-2 text-sm font-bold">
              <ShoppingCart className="w-4 h-4" />
              {cart.reduce((n, l) => n + l.quantity, 0)} items
            </span>
            <span className="text-sm font-black">
              {createOrder.isPending ? 'Placing…' : `Order · ${total.toLocaleString()} ${resolution.currency || 'ETB'}`}
            </span>
          </button>
          {createOrder.isError && (
            <p className="text-[11px] text-red-600 mt-2">{(createOrder.error as Error).message}</p>
          )}
        </div>
      )}
    </div>
  );
};
