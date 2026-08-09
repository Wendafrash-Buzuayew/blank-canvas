import React, { useState, useEffect } from 'react';
import { 
  X, 
  CheckCircle2, 
  Clock, 
  ChefHat, 
  CookingPot, 
  Bell, 
  Receipt, 
  Sparkles, 
  PhoneCall, 
  Check, 
  AlertCircle 
} from 'lucide-react';
import { Order, Merchant } from '../../types';
import { formatMoney, timeAgo } from '../../lib/utils';

interface OrderStatusModalProps {
  order: Order | null;
  onClose: () => void;
  merchant: Merchant;
}

export const OrderStatusModal: React.FC<OrderStatusModalProps> = ({
  order,
  onClose,
  merchant,
}) => {
  if (!order) return null;

  const [waiterCalled, setWaiterCalled] = useState(false);
  const [minutesLeft, setMinutesLeft] = useState(order.estimatedPrepMinutes || 12);

  useEffect(() => {
    const timer = setInterval(() => {
      setMinutesLeft((prev) => (prev > 1 ? prev - 1 : 1));
    }, 60000);
    return () => clearInterval(timer);
  }, []);

  const handleCallWaiter = () => {
    setWaiterCalled(true);
    setTimeout(() => setWaiterCalled(false), 5000);
  };

  const steps = [
    { key: 'Pending', label: 'Order Received', desc: 'Sent to kitchen' },
    { key: 'Accepted', label: 'Accepted', desc: 'Chef confirmed' },
    { key: 'Preparing', label: 'Preparing', desc: 'Cooking in progress' },
    { key: 'Ready', label: 'Ready to Serve', desc: 'Hot & ready' },
    { key: 'Served', label: 'Served', desc: 'Enjoy your meal!' },
  ];

  const getStepStatus = (stepKey: string) => {
    const orderStatuses = ['Pending', 'Accepted', 'Preparing', 'Ready', 'Served', 'Paid'];
    const currentIdx = orderStatuses.indexOf(order.status);
    const stepIdx = orderStatuses.indexOf(stepKey);

    if (currentIdx > stepIdx) return 'completed';
    if (currentIdx === stepIdx) return 'current';
    return 'upcoming';
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4 animate-in fade-in duration-200">
      <div 
        className="w-full max-w-lg bg-white rounded-3xl overflow-hidden shadow-2xl flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Banner */}
        <div className="bg-gradient-to-r from-gray-900 to-gray-800 text-white p-5 relative">
          <button 
            onClick={onClose}
            className="absolute top-4 right-4 text-gray-400 hover:text-white p-1 rounded-full hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="flex items-center gap-2 mb-2">
            <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-[#E60028] text-white uppercase tracking-wider">
              Live Order #{order.orderNumber}
            </span>
            <span className="text-xs text-gray-300 font-medium">
              Table {order.tableNumber}
            </span>
          </div>

          <h3 className="text-xl font-black">
            {order.status === 'Ready' 
              ? '🎉 Order is Ready!' 
              : order.status === 'Served' 
              ? '✨ Served & Enjoy!' 
              : '🍳 Kitchen is Preparing Your Meal'}
          </h3>
          <p className="text-xs text-gray-300 mt-1">
            Placed {timeAgo(order.createdAt)} • {merchant.name}
          </p>
        </div>

        {/* Content Body */}
        <div className="p-5 overflow-y-auto space-y-6">

          {/* Prep Timer Card */}
          {order.status !== 'Served' && order.status !== 'Paid' && (
            <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-amber-500 text-white flex items-center justify-center font-bold animate-pulse">
                  <Clock className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-[10px] font-extrabold uppercase text-amber-800 tracking-wider">Estimated Wait Time</span>
                  <div className="text-lg font-black text-amber-950">~{minutesLeft} Minutes</div>
                </div>
              </div>
              <div className="text-right">
                <span className="text-xs font-bold text-amber-700 block">Staff on Duty</span>
                <span className="text-[11px] text-amber-600">Table {order.tableNumber} Priority</span>
              </div>
            </div>
          )}

          {/* Status Stepper */}
          <div className="space-y-4">
            <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider">
              Order Progress Tracker
            </h4>

            <div className="relative pl-6 border-l-2 border-gray-200 space-y-6">
              {steps.map((step) => {
                const status = getStepStatus(step.key);
                return (
                  <div key={step.key} className="relative flex items-start justify-between">
                    {/* Circle Node */}
                    <div className={`absolute -left-[31px] top-0 w-6 h-6 rounded-full flex items-center justify-center text-xs transition-colors ${
                      status === 'completed'
                        ? 'bg-emerald-500 text-white'
                        : status === 'current'
                        ? 'bg-[#E60028] text-white animate-bounce'
                        : 'bg-gray-200 text-gray-400'
                    }`}>
                      {status === 'completed' ? '✓' : ''}
                    </div>

                    <div>
                      <div className={`text-sm font-bold ${
                        status === 'current' ? 'text-[#E60028]' : status === 'completed' ? 'text-gray-900' : 'text-gray-400'
                      }`}>
                        {step.label}
                      </div>
                      <div className="text-xs text-gray-500">{step.desc}</div>
                    </div>

                    {status === 'current' && (
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-[#E60028]">
                        Active Now
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Ordered Items Summary */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider flex items-center gap-1.5">
              <Receipt className="w-3.5 h-3.5" />
              Receipt & Item Details
            </h4>
            
            <div className="bg-gray-50 rounded-2xl p-4 border border-gray-100 space-y-2.5">
              {order.items.map((it) => (
                <div key={it.id} className="flex justify-between text-xs">
                  <div>
                    <span className="font-bold text-gray-900">{it.quantity}x </span>
                    <span className="text-gray-800">{it.name}</span>
                    {it.notes && (
                      <div className="text-[11px] text-amber-700 italic">Note: {it.notes}</div>
                    )}
                  </div>
                  <span className="font-bold text-gray-900">
                    {formatMoney(it.price * it.quantity, merchant.currencySymbol)}
                  </span>
                </div>
              ))}

              <div className="pt-3 border-t border-gray-200 flex justify-between font-extrabold text-sm text-gray-900">
                <span>Total Billed</span>
                <span className="text-[#E60028]">
                  {formatMoney(order.totalPrice, merchant.currencySymbol)}
                </span>
              </div>
            </div>
          </div>

          {/* Call Waiter Action */}
          <div className="bg-gray-50 p-4 rounded-2xl border border-gray-100 flex items-center justify-between gap-4">
            <div>
              <div className="text-xs font-bold text-gray-900">Need Service at Table {order.tableNumber}?</div>
              <div className="text-[11px] text-gray-500">Notify waiter or cashier for assistance</div>
            </div>

            <button
              onClick={handleCallWaiter}
              disabled={waiterCalled}
              className={`px-4 py-2.5 rounded-xl font-bold text-xs flex items-center gap-1.5 transition-all ${
                waiterCalled
                  ? 'bg-emerald-500 text-white'
                  : 'bg-gray-900 hover:bg-black text-white shadow-md'
              }`}
            >
              <Bell className="w-3.5 h-3.5" />
              {waiterCalled ? 'Waiter Notified!' : 'Call Waiter'}
            </button>
          </div>

        </div>

        {/* Footer - Order Something Else (outline variant) */}
        <div className="p-4 bg-gray-50 border-t border-gray-100">
          <button
            onClick={onClose}
            className="w-full py-3 bg-transparent border-2 border-gray-300 hover:border-[#E60028] hover:text-[#E60028] text-gray-700 text-xs font-bold rounded-xl transition-all"
          >
            Order Something Else
          </button>
        </div>

      </div>
    </div>
  );
};
