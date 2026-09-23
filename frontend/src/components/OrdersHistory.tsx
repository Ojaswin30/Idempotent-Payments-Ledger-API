import React, { useEffect, useState } from 'react';
import { Clock, RefreshCw, XCircle, ShieldCheck, Scale, AlertCircle } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { listPaymentsApi, cancelPaymentApi } from '../services/api';
import { PaymentResponse } from '../types/payment';

export const OrdersHistory: React.FC = () => {
  const { customerId } = useCart();
  const [orders, setOrders] = useState<PaymentResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const fetchOrders = async () => {
    setIsLoading(true);
    setFeedback(null);
    try {
      const response = await listPaymentsApi(customerId);
      setOrders(response.content || []);
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Failed to load order history' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, [customerId]);

  const handleCancelOrder = async (paymentId: string) => {
    if (!confirm('Are you sure you want to cancel this order? An instant double-entry reversal ledger transaction will be recorded.')) {
      return;
    }

    setCancellingId(paymentId);
    try {
      await cancelPaymentApi(paymentId, 'Customer requested refund via NovaStore portal');
      setFeedback({
        type: 'success',
        message: `Order ${paymentId.substring(0, 8)}... was successfully cancelled and refunded!`,
      });
      fetchOrders();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: err.message || 'Failed to cancel order',
      });
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-6 sm:p-8 flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl">
        <div className="space-y-1">
          <div className="flex items-center space-x-2">
            <Clock className="w-5 h-5 text-indigo-400" />
            <h2 className="text-xl font-bold text-white">Order History & Refunds</h2>
          </div>
          <p className="text-xs text-slate-400">
            Live orders for customer account <code className="font-mono text-indigo-300 font-bold">{customerId}</code>
          </p>
        </div>

        <button
          onClick={fetchOrders}
          disabled={isLoading}
          className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 text-xs font-semibold transition flex items-center justify-center gap-2 border border-slate-700 self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </button>
      </div>

      {/* Alert Banner */}
      {feedback && (
        <div
          className={`p-4 rounded-2xl text-xs flex items-center gap-2.5 font-medium border ${
            feedback.type === 'success'
              ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-300'
              : 'bg-rose-500/10 border-rose-500/20 text-rose-300'
          }`}
        >
          {feedback.type === 'success' ? (
            <ShieldCheck className="w-4 h-4 text-emerald-400 flex-shrink-0" />
          ) : (
            <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
          )}
          <span>{feedback.message}</span>
        </div>
      )}

      {/* Orders List */}
      <div className="space-y-4">
        {isLoading ? (
          <div className="py-16 text-center text-slate-500 text-sm">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-indigo-400" />
            <span>Loading orders from PostgreSQL ledger...</span>
          </div>
        ) : orders.length === 0 ? (
          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-12 text-center text-slate-500 space-y-3">
            <Clock className="w-10 h-10 stroke-1 mx-auto text-slate-600" />
            <p className="text-sm font-medium">No order history found for {customerId}.</p>
          </div>
        ) : (
          orders.map((order) => (
            <div
              key={order.paymentId}
              className="bg-slate-900/60 border border-slate-800 rounded-3xl p-6 space-y-4 shadow-lg hover:border-slate-700 transition"
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-800/80">
                <div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-mono font-bold text-indigo-300">
                      ID: {order.paymentId}
                    </span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-mono text-slate-400 bg-slate-800">
                      v{order.version} (Lock)
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1">
                    Placed on {new Date(order.createdAt).toLocaleString()}
                  </p>
                </div>

                <div className="flex items-center space-x-3 self-end sm:self-auto">
                  <span
                    className={`px-3 py-1 rounded-full text-xs font-bold font-mono ${
                      order.status === 'CREATED' || order.status === 'COMPLETED'
                        ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                        : order.status === 'CANCELLED'
                        ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                        : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    {order.status}
                  </span>

                  {order.status !== 'CANCELLED' && order.status !== 'FAILED' && (
                    <button
                      disabled={cancellingId === order.paymentId}
                      onClick={() => handleCancelOrder(order.paymentId)}
                      className="px-3 py-1 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 text-xs font-semibold transition flex items-center gap-1.5 disabled:opacity-50"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      <span>{cancellingId === order.paymentId ? 'Cancelling...' : 'Cancel & Refund'}</span>
                    </button>
                  )}
                </div>
              </div>

              {/* Order Info */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <span className="text-slate-500 uppercase tracking-wider block font-medium">Order Description</span>
                  <p className="text-slate-200 mt-0.5">{order.description || 'NovaStore Order'}</p>
                </div>

                <div className="sm:text-right">
                  <span className="text-slate-500 uppercase tracking-wider block font-medium">Total Billed</span>
                  <p className="text-base font-extrabold font-mono text-white mt-0.5">
                    ₹{order.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })} {order.currency}
                  </p>
                </div>
              </div>

              {/* Ledger Entries Accordion */}
              {order.ledgerEntries && order.ledgerEntries.length > 0 && (
                <div className="pt-3 border-t border-slate-800/80">
                  <div className="flex items-center gap-1.5 text-[11px] font-bold text-slate-400 mb-2">
                    <Scale className="w-3.5 h-3.5 text-indigo-400" />
                    <span>Associated Double-Entry Bookkeeping Entries:</span>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {order.ledgerEntries.map((le) => (
                      <div
                        key={le.id}
                        className="bg-slate-950/60 border border-slate-800/80 rounded-xl p-2.5 font-mono text-[11px] flex items-center justify-between"
                      >
                        <span className="text-slate-400 truncate mr-2">
                          {le.entryType} ({le.accountId})
                        </span>
                        <span className={le.entryType === 'DEBIT' ? 'text-amber-400 font-bold' : 'text-emerald-400 font-bold'}>
                          ₹{le.amount}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
};
