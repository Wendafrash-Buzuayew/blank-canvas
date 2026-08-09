import React, { useState } from 'react';
import { CookingPot, Clock, CheckCircle2, Volume2, VolumeX, AlertTriangle, ChefHat } from 'lucide-react';
import { Order, OrderStatus, Merchant } from '../../types';
import { formatMoney, timeAgo } from '../../lib/utils';

interface KitchenDisplaySystemProps {
  merchant: Merchant;
  orders: Order[];
  onUpdateOrderStatus: (orderId: string, status: OrderStatus) => void;
}

export const KitchenDisplaySystem: React.FC<KitchenDisplaySystemProps> = ({
  merchant,
  orders,
  onUpdateOrderStatus,
}) => {
  const [soundEnabled, setSoundEnabled] = useState(true);

  const activeOrders = orders.filter(
    o => o.merchantId === merchant.id && (o.status === 'Pending' || o.status === 'Accepted' || o.status === 'Preparing')
  );

  return (
    <div className="min-h-screen bg-gray-950 text-white p-6 space-y-6">

      {/* Header */}
      <div className="flex items-center justify-between border-b border-gray-800 pb-4">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-[#E60028] text-white flex items-center justify-center font-black">
            <ChefHat className="w-7 h-7" />
          </div>
          <div>
            <h1 className="text-2xl font-black text-white flex items-center gap-2">
              KITCHEN DISPLAY SYSTEM (KDS)
              <span className="px-2.5 py-0.5 rounded-full bg-red-600 text-white text-xs font-bold animate-pulse">
                {activeOrders.length} ACTIVE
              </span>
            </h1>
            <p className="text-xs text-gray-400 font-medium">{merchant.name} • Main Kitchen Screen</p>
          </div>
        </div>

        <button
          onClick={() => setSoundEnabled(!soundEnabled)}
          className={`px-4 py-2.5 rounded-xl text-xs font-bold border flex items-center gap-2 transition-colors ${
            soundEnabled ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400' : 'bg-gray-800 border-gray-700 text-gray-400'
          }`}
        >
          {soundEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
          {soundEnabled ? 'Kitchen Bell Active' : 'Muted'}
        </button>
      </div>

      {/* Orders Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {activeOrders.length === 0 ? (
          <div className="col-span-full py-20 text-center text-gray-500 space-y-3">
            <CookingPot className="w-16 h-16 mx-auto stroke-1 text-gray-700" />
            <h3 className="text-xl font-bold">Kitchen Queue Clear</h3>
            <p className="text-xs">All customer orders served!</p>
          </div>
        ) : (
          activeOrders.map(order => (
            <div 
              key={order.id}
              className={`rounded-3xl p-6 border shadow-2xl flex flex-col justify-between space-y-4 ${
                order.status === 'Pending' 
                  ? 'bg-red-950/40 border-red-600/80 ring-2 ring-red-500' 
                  : order.status === 'Preparing' 
                  ? 'bg-amber-950/40 border-amber-600/80' 
                  : 'bg-gray-900 border-gray-800'
              }`}
            >
              <div>
                <div className="flex items-center justify-between border-b border-gray-800 pb-3 mb-3">
                  <div>
                    <span className="text-2xl font-black text-white">#{order.orderNumber}</span>
                    <div className="text-xs font-bold text-amber-400">TABLE {order.tableNumber}</div>
                  </div>
                  <div className="text-right">
                    <span className="text-xs text-gray-400 block">{timeAgo(order.createdAt)}</span>
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-white/10 text-white">
                      {order.status}
                    </span>
                  </div>
                </div>

                {/* Items */}
                <div className="space-y-3">
                  {order.items.map(it => (
                    <div key={it.id} className="text-sm">
                      <div className="flex justify-between font-black text-white text-base">
                        <span>{it.quantity}x {it.name}</span>
                      </div>
                      {it.notes && (
                        <div className="text-xs font-bold text-amber-300 bg-amber-500/20 px-2 py-1 rounded mt-1 border border-amber-500/40">
                          ⚠️ {it.notes}
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                {order.notes && (
                  <div className="mt-4 p-3 rounded-xl bg-red-500/20 border border-red-500/40 text-xs font-bold text-red-300">
                    SPECIAL ORDER NOTE: "{order.notes}"
                  </div>
                )}
              </div>

              {/* Action */}
              <div className="pt-3 border-t border-gray-800">
                {order.status === 'Pending' && (
                  <button
                    onClick={() => onUpdateOrderStatus(order.id, 'Preparing')}
                    className="w-full py-4 bg-[#E60028] hover:bg-red-700 text-white text-base font-black rounded-2xl shadow-lg transition-transform active:scale-95"
                  >
                    START PREPARING 🍳
                  </button>
                )}
                {order.status === 'Preparing' && (
                  <button
                    onClick={() => onUpdateOrderStatus(order.id, 'Ready')}
                    className="w-full py-4 bg-emerald-600 hover:bg-emerald-700 text-white text-base font-black rounded-2xl shadow-lg transition-transform active:scale-95"
                  >
                    MARK READY FOR SERVING 🎉
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

    </div>
  );
};
