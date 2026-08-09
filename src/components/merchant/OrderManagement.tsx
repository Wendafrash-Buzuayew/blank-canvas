import React, { useState } from 'react';
import { 
  ShoppingBag, 
  Clock, 
  CheckCircle2, 
  XCircle, 
  ChefHat, 
  Printer, 
  Volume2, 
  VolumeX, 
  Search, 
  Filter, 
  AlertCircle,
  Table as TableIcon,
  ChevronRight,
  Sparkles,
  DollarSign
} from 'lucide-react';
import { Order, OrderStatus, Merchant } from '../../types';
import { formatMoney, timeAgo } from '../../lib/utils';

interface OrderManagementProps {
  merchant: Merchant;
  orders: Order[];
  onUpdateOrderStatus: (orderId: string, status: OrderStatus, paymentStatus?: 'Unpaid' | 'Paid') => void;
}

export const OrderManagement: React.FC<OrderManagementProps> = ({
  merchant,
  orders,
  onUpdateOrderStatus,
}) => {
  const [selectedStatus, setSelectedStatus] = useState<OrderStatus | 'All'>('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [ticketOrder, setTicketOrder] = useState<Order | null>(null);

  const merchantOrders = orders.filter(o => o.merchantId === merchant.id);

  const filteredOrders = merchantOrders.filter(o => {
    const matchesStatus = selectedStatus === 'All' || o.status === selectedStatus;
    const matchesSearch = o.orderNumber.includes(searchQuery) ||
                          o.tableNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          (o.customerName && o.customerName.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesStatus && matchesSearch;
  });

  const getStatusBadgeClass = (status: OrderStatus) => {
    switch (status) {
      case 'Pending': return 'bg-red-50 text-[#E60028] border-red-200 animate-pulse';
      case 'Accepted': return 'bg-blue-50 text-blue-700 border-blue-200';
      case 'Preparing': return 'bg-amber-50 text-amber-700 border-amber-200';
      case 'Ready': return 'bg-[#FFB000]/20 text-amber-900 border-amber-400 font-black';
      case 'Served': return 'bg-emerald-50 text-emerald-700 border-emerald-200';
      case 'Paid': return 'bg-gray-100 text-gray-800 border-gray-300';
      case 'Cancelled': return 'bg-gray-50 text-gray-400 border-gray-200';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  return (
    <div className="space-y-6">

      {/* Header Controls Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-gray-100 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-black text-gray-900 flex items-center gap-2">
              <ChefHat className="w-6 h-6 text-[#E60028]" />
              Kitchen Order Management (KDS)
            </h2>
            <span className="px-2 py-0.5 rounded-full bg-red-100 text-[#E60028] text-xs font-bold">
              {merchantOrders.filter(o => o.status === 'Pending').length} Pending
            </span>
          </div>
          <p className="text-xs text-gray-500 mt-0.5">Real-time incoming customer table orders</p>
        </div>

        <div className="flex items-center gap-2">
          {/* Sound Toggle */}
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            className={`px-3 py-2 rounded-xl text-xs font-bold border flex items-center gap-1.5 transition-colors ${
              soundEnabled ? 'bg-emerald-50 border-emerald-200 text-emerald-700' : 'bg-gray-50 border-gray-200 text-gray-500'
            }`}
          >
            {soundEnabled ? <Volume2 className="w-4 h-4 text-emerald-600" /> : <VolumeX className="w-4 h-4" />}
            {soundEnabled ? 'Order Sound ON' : 'Muted'}
          </button>

          {/* Search Box */}
          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-gray-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search table or order #..."
              className="pl-8 pr-3 py-1.5 bg-gray-50 border border-gray-200 rounded-xl text-xs focus:outline-none focus:ring-2 focus:ring-[#E60028]/20 focus:border-[#E60028]"
            />
          </div>
        </div>
      </div>

      {/* Status Filter Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar text-xs">
        {['All', 'Pending', 'Accepted', 'Preparing', 'Ready', 'Served', 'Paid', 'Cancelled'].map((st) => (
          <button
            key={st}
            onClick={() => setSelectedStatus(st as any)}
            className={`px-3.5 py-2 rounded-xl font-bold whitespace-nowrap border transition-all ${
              selectedStatus === st
                ? 'bg-gray-900 text-white border-gray-900 shadow-xs'
                : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300'
            }`}
          >
            {st}
            {st !== 'All' && (
              <span className="ml-1.5 px-1.5 py-0.2 rounded-full text-[10px] bg-gray-100 text-gray-600">
                {merchantOrders.filter(o => o.status === st).length}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Orders Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredOrders.length === 0 ? (
          <div className="col-span-full bg-white p-12 rounded-3xl border border-gray-100 text-center text-gray-400 space-y-2">
            <ShoppingBag className="w-12 h-12 mx-auto stroke-1 text-gray-300" />
            <p className="text-sm font-semibold">No orders found matching filter criteria.</p>
          </div>
        ) : (
          filteredOrders.map((order) => (
            <div 
              key={order.id}
              className={`bg-white rounded-3xl border shadow-xs p-5 flex flex-col justify-between space-y-4 transition-all hover:shadow-md ${
                order.status === 'Pending' ? 'border-red-300 ring-2 ring-red-500/20' : 'border-gray-100'
              }`}
            >
              {/* Card Top Info */}
              <div>
                <div className="flex items-center justify-between border-b border-gray-100 pb-3">
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-xl bg-gray-900 text-white text-xs font-black flex items-center justify-center">
                      #{order.orderNumber}
                    </span>
                    <div>
                      <h4 className="text-sm font-black text-gray-900 flex items-center gap-1">
                        <TableIcon className="w-3.5 h-3.5 text-[#E60028]" />
                        Table {order.tableNumber}
                      </h4>
                      <span className="text-[11px] text-gray-400">{timeAgo(order.createdAt)}</span>
                    </div>
                  </div>

                  <span className={`px-2.5 py-1 rounded-full text-xs font-extrabold border ${getStatusBadgeClass(order.status)}`}>
                    {order.status}
                  </span>
                </div>

                {/* Customer name & Payment mode */}
                <div className="flex items-center justify-between text-xs py-2 text-gray-500">
                  <span>Customer: <strong className="text-gray-900">{order.customerName || 'Guest'}</strong></span>
                  <span className="px-2 py-0.5 rounded bg-gray-100 text-gray-700 font-medium">
                    {order.paymentMethod || 'Pay at Counter'} ({order.paymentStatus || 'Unpaid'})
                  </span>
                </div>

                {/* Items List */}
                <div className="space-y-2 bg-gray-50 p-3 rounded-2xl border border-gray-100 my-2">
                  {order.items.map((it) => (
                    <div key={it.id} className="text-xs flex items-start justify-between gap-2">
                      <div>
                        <span className="font-extrabold text-gray-900">{it.quantity}x</span>{' '}
                        <span className="font-bold text-gray-800">{it.name}</span>
                        {it.notes && (
                          <div className="text-[11px] font-bold text-amber-700 bg-amber-100 px-1.5 py-0.5 rounded mt-0.5 inline-block">
                            ⚠️ Note: {it.notes}
                          </div>
                        )}
                      </div>
                      <span className="font-semibold text-gray-600">
                        {formatMoney(it.price * it.quantity, merchant.currencySymbol)}
                      </span>
                    </div>
                  ))}
                  {order.notes && (
                    <div className="pt-2 border-t border-gray-200 text-xs text-red-600 font-bold">
                      Order Note: "{order.notes}"
                    </div>
                  )}
                </div>
              </div>

              {/* Bottom Total & Actions */}
              <div className="space-y-3 pt-2">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-bold text-gray-500">Total Bill</span>
                  <span className="text-base font-black text-[#E60028]">
                    {formatMoney(order.totalPrice, merchant.currencySymbol)}
                  </span>
                </div>

                <div className="flex items-center gap-2 pt-1 border-t border-gray-100">
                  {/* Status workflow transitions */}
                  {order.status === 'Pending' && (
                    <button
                      onClick={() => onUpdateOrderStatus(order.id, 'Accepted')}
                      className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors"
                    >
                      Accept Order
                    </button>
                  )}
                  {order.status === 'Accepted' && (
                    <button
                      onClick={() => onUpdateOrderStatus(order.id, 'Preparing')}
                      className="flex-1 py-2 bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors"
                    >
                      Start Cooking
                    </button>
                  )}
                  {order.status === 'Preparing' && (
                    <button
                      onClick={() => onUpdateOrderStatus(order.id, 'Ready')}
                      className="flex-1 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors"
                    >
                      Mark Ready
                    </button>
                  )}
                  {order.status === 'Ready' && (
                    <button
                      onClick={() => onUpdateOrderStatus(order.id, 'Served')}
                      className="flex-1 py-2 bg-gray-900 hover:bg-black text-white font-bold text-xs rounded-xl shadow-xs transition-colors"
                    >
                      Mark Served
                    </button>
                  )}
                  {order.status === 'Served' && (
                    <button
                      onClick={() => onUpdateOrderStatus(order.id, 'Paid', 'Paid')}
                      className="flex-1 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors"
                    >
                      Settle Payment
                    </button>
                  )}

                  {/* Print Kitchen Ticket Button */}
                  <button
                    onClick={() => setTicketOrder(order)}
                    title="Print Kitchen Ticket"
                    className="p-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl transition-colors"
                  >
                    <Printer className="w-4 h-4" />
                  </button>
                  
                  {order.status !== 'Cancelled' && order.status !== 'Paid' && (
                    <button
                      onClick={() => onUpdateOrderStatus(order.id, 'Cancelled')}
                      title="Cancel Order"
                      className="p-2 text-red-500 hover:bg-red-50 rounded-xl transition-colors"
                    >
                      <XCircle className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Kitchen Ticket Printable Modal */}
      {ticketOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="bg-white max-w-sm w-full p-6 rounded-3xl shadow-2xl space-y-4 font-mono text-xs">
            <div className="text-center border-b border-gray-200 pb-3 space-y-1">
              <h3 className="font-extrabold text-base tracking-tight">{merchant.name}</h3>
              <p className="text-[10px] text-gray-500">KITCHEN DUPLICATE TICKET</p>
              <div className="text-sm font-black text-gray-900 pt-1">
                ORDER #{ticketOrder.orderNumber} • TABLE {ticketOrder.tableNumber}
              </div>
              <p className="text-[10px] text-gray-400">{new Date(ticketOrder.createdAt).toLocaleString()}</p>
            </div>

            <div className="space-y-2 py-2">
              {ticketOrder.items.map(it => (
                <div key={it.id} className="flex justify-between font-bold">
                  <span>{it.quantity}x {it.name}</span>
                  <span>{formatMoney(it.price * it.quantity, merchant.currencySymbol)}</span>
                </div>
              ))}
            </div>

            {ticketOrder.notes && (
              <div className="p-2 bg-yellow-50 border border-yellow-200 rounded font-bold text-yellow-900">
                NOTE: {ticketOrder.notes}
              </div>
            )}

            <div className="border-t border-gray-200 pt-3 text-right text-sm font-black">
              TOTAL: {formatMoney(ticketOrder.totalPrice, merchant.currencySymbol)}
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => window.print()}
                className="flex-1 py-2.5 bg-[#E60028] text-white font-bold rounded-xl"
              >
                Print Ticket
              </button>
              <button
                onClick={() => setTicketOrder(null)}
                className="py-2.5 px-4 bg-gray-100 text-gray-700 font-bold rounded-xl"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
