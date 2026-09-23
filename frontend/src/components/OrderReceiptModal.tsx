import React from 'react';
import { CheckCircle2, ShieldCheck, Copy, Clock, Scale } from 'lucide-react';
import { PaymentResponse } from '../types/payment';

interface OrderReceiptModalProps {
  receipt: PaymentResponse | null;
  isReplay: boolean;
  onClose: () => void;
  onViewOrders: () => void;
}

export const OrderReceiptModal: React.FC<OrderReceiptModalProps> = ({
  receipt,
  isReplay,
  onClose,
  onViewOrders,
}) => {
  if (!receipt) return null;

  const copyPaymentId = () => {
    navigator.clipboard.writeText(receipt.paymentId);
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl space-y-6">
        
        {/* Top Success Banner */}
        <div className="p-8 text-center bg-gradient-to-b from-emerald-950/40 via-slate-900 to-slate-900 border-b border-slate-800/80 space-y-3">
          <div className="w-16 h-16 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/20">
            <CheckCircle2 className="w-8 h-8" />
          </div>

          <h3 className="text-xl font-bold text-white">Payment Successful!</h3>
          <p className="text-xs text-slate-400">Your order has been recorded in the distributed ledger.</p>

          {isReplay && (
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-mono font-medium">
              <ShieldCheck className="w-3.5 h-3.5 text-indigo-400" />
              <span>X-Idempotent-Replay: true (Zero duplicate charges)</span>
            </div>
          )}
        </div>

        {/* Receipt Key Information */}
        <div className="px-6 space-y-4">
          <div className="bg-slate-950/80 border border-slate-800 rounded-2xl p-4 space-y-3">
            <div className="flex items-center justify-between text-xs pb-3 border-b border-slate-800">
              <span className="text-slate-400 font-medium">Payment ID</span>
              <div className="flex items-center space-x-1.5 font-mono text-indigo-300 font-semibold">
                <span>{receipt.paymentId.substring(0, 16)}...</span>
                <button
                  onClick={copyPaymentId}
                  className="p-1 hover:text-white rounded transition text-slate-500"
                  title="Copy Full UUID"
                >
                  <Copy className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400 font-medium">Customer Account</span>
              <span className="font-mono text-slate-200">{receipt.customerId}</span>
            </div>

            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400 font-medium">Amount Deducted</span>
              <span className="font-mono font-bold text-emerald-400 text-sm">
                ₹{receipt.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })} {receipt.currency}
              </span>
            </div>

            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400 font-medium">Status</span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                {receipt.status}
              </span>
            </div>
          </div>

          {/* Double-Entry Ledger Summary Card */}
          {receipt.ledgerEntries && receipt.ledgerEntries.length > 0 && (
            <div className="bg-indigo-950/20 border border-indigo-500/20 rounded-2xl p-4 space-y-2">
              <div className="flex items-center gap-1.5 text-xs font-bold text-indigo-300">
                <Scale className="w-4 h-4 text-indigo-400" />
                <span>Double-Entry Ledger Audit Record</span>
              </div>
              <div className="space-y-1 font-mono text-[11px] text-slate-300">
                {receipt.ledgerEntries.map((entry) => (
                  <div key={entry.id} className="flex justify-between items-center py-0.5">
                    <span className="text-slate-400">
                      {entry.entryType} ({entry.accountId})
                    </span>
                    <span className={entry.entryType === 'DEBIT' ? 'text-amber-400' : 'text-emerald-400'}>
                      {entry.amount} {entry.currency}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="p-6 border-t border-slate-800 bg-slate-950/40 grid grid-cols-2 gap-3">
          <button
            onClick={onClose}
            className="py-3 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold transition"
          >
            Continue Shopping
          </button>

          <button
            onClick={() => {
              onClose();
              onViewOrders();
            }}
            className="py-3 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition flex items-center justify-center gap-1.5 shadow-lg shadow-indigo-600/30"
          >
            <Clock className="w-4 h-4" />
            <span>View My Orders</span>
          </button>
        </div>
      </div>
    </div>
  );
};
