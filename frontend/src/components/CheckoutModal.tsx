import React, { useState, useEffect } from 'react';
import { X, ShieldCheck, Lock, CreditCard, Zap, AlertTriangle, CheckCircle2, RotateCw } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { createPaymentApi } from '../services/api';
import { PaymentResponse } from '../types/payment';

interface CheckoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (response: PaymentResponse, isReplay: boolean) => void;
}

export const CheckoutModal: React.FC<CheckoutModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const { total, customerId, setCustomerId, clearCart, cart } = useCart();
  const [idempotencyKey, setIdempotencyKey] = useState<string>('');
  const [paymentMethod, setPaymentMethod] = useState<'card' | 'upi' | 'netbanking'>('upi');
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [simulationMode, setSimulationMode] = useState<'normal' | 'double-click' | 'tamper'>('normal');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Generate a fresh UUID Idempotency Key upon opening checkout
  useEffect(() => {
    if (isOpen) {
      setIdempotencyKey(crypto.randomUUID());
      setErrorMessage(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handlePay = async () => {
    setIsProcessing(true);
    setErrorMessage(null);

    const description = `NovaStore Order (${cart.length} items: ${cart.map((i) => i.product.name).join(', ')})`.substring(0, 250);

    const payload = {
      customerId: customerId.trim() || 'cust_123',
      amount: total,
      currency: 'INR',
      description,
    };

    try {
      if (simulationMode === 'double-click') {
        // Double-click scenario: fire 2 requests simultaneously with the same key
        const [res1, res2] = await Promise.allSettled([
          createPaymentApi(idempotencyKey, payload),
          createPaymentApi(idempotencyKey, payload),
        ]);

        const successful = res1.status === 'fulfilled' ? res1.value : (res2.status === 'fulfilled' ? res2.value : null);
        if (successful) {
          clearCart();
          onClose();
          onSuccess(successful.data, true);
        } else {
          throw new Error('Both requests encountered an issue');
        }
      } else if (simulationMode === 'tamper') {
        // Alter amount with same key to trigger 422 mismatch
        const tamperedPayload = { ...payload, amount: total + 1000 };
        await createPaymentApi(idempotencyKey, tamperedPayload);
      } else {
        // Standard normal checkout
        const result = await createPaymentApi(idempotencyKey, payload);
        clearCart();
        onClose();
        onSuccess(result.data, result.isReplay);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Payment processing failed');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl space-y-6">
        
        {/* Header */}
        <div className="p-6 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-600/10 text-indigo-400 flex items-center justify-center">
              <Lock className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Secure Checkout</h3>
              <p className="text-xs text-slate-400">Idempotency & Double-Entry Protected</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-white rounded-lg transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="px-6 space-y-5">
          
          {/* Amount Display */}
          <div className="bg-slate-950/80 border border-slate-800 rounded-2xl p-4 flex items-center justify-between">
            <div>
              <span className="text-xs text-slate-400 block font-medium">Payable Amount</span>
              <span className="text-2xl font-extrabold text-white font-mono">
                ₹{total.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </span>
            </div>
            <div className="text-right">
              <span className="text-[10px] text-slate-500 uppercase block font-semibold">Items</span>
              <span className="text-sm font-bold text-slate-300">{cart.length} Products</span>
            </div>
          </div>

          {/* Customer Identifier */}
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider">
              Customer Account ID
            </label>
            <input
              type="text"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm font-mono text-slate-100 focus:outline-none focus:border-indigo-500"
              placeholder="cust_123"
            />
          </div>

          {/* Payment Method Selector */}
          <div className="space-y-2">
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider">
              Payment Method
            </label>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => setPaymentMethod('upi')}
                className={`p-3 rounded-xl border text-xs font-bold transition flex flex-col items-center gap-1.5 ${
                  paymentMethod === 'upi'
                    ? 'border-indigo-500 bg-indigo-500/10 text-indigo-300'
                    : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-white'
                }`}
              >
                <Zap className="w-4 h-4 text-amber-400" />
                <span>Instant UPI</span>
              </button>

              <button
                type="button"
                onClick={() => setPaymentMethod('card')}
                className={`p-3 rounded-xl border text-xs font-bold transition flex flex-col items-center gap-1.5 ${
                  paymentMethod === 'card'
                    ? 'border-indigo-500 bg-indigo-500/10 text-indigo-300'
                    : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-white'
                }`}
              >
                <CreditCard className="w-4 h-4 text-cyan-400" />
                <span>Credit Card</span>
              </button>

              <button
                type="button"
                onClick={() => setPaymentMethod('netbanking')}
                className={`p-3 rounded-xl border text-xs font-bold transition flex flex-col items-center gap-1.5 ${
                  paymentMethod === 'netbanking'
                    ? 'border-indigo-500 bg-indigo-500/10 text-indigo-300'
                    : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-white'
                }`}
              >
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>NetBanking</span>
              </button>
            </div>
          </div>

          {/* Educational Idempotency Simulation Controls */}
          <div className="p-4 rounded-2xl bg-indigo-950/30 border border-indigo-500/20 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-indigo-300 flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4" /> Idempotency Mode
              </span>
              <span className="text-[10px] font-mono text-slate-400">
                Key: {idempotencyKey.substring(0, 8)}...
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
              <button
                type="button"
                onClick={() => setSimulationMode('normal')}
                className={`p-2 rounded-lg border font-semibold text-center transition ${
                  simulationMode === 'normal'
                    ? 'bg-indigo-600 text-white border-indigo-500'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                Standard Flow
              </button>

              <button
                type="button"
                onClick={() => setSimulationMode('double-click')}
                className={`p-2 rounded-lg border font-semibold text-center transition ${
                  simulationMode === 'double-click'
                    ? 'bg-amber-600 text-white border-amber-500'
                    : 'bg-slate-900 border-slate-800 text-amber-400/80 hover:text-amber-300'
                }`}
              >
                Simulate Double-Click
              </button>
            </div>

            <p className="text-[11px] text-slate-400 leading-normal">
              {simulationMode === 'double-click'
                ? '⚡ Dispatches 2 concurrent parallel requests with the identical key to demonstrate automatic lock replay.'
                : '🔒 Transparently transmits Idempotency-Key in the HTTP header.'}
            </p>
          </div>

          {/* Error display */}
          {errorMessage && (
            <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 flex-shrink-0 text-rose-400" />
              <span>{errorMessage}</span>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="p-6 border-t border-slate-800 bg-slate-950/40">
          <button
            type="button"
            disabled={isProcessing}
            onClick={handlePay}
            className="w-full py-3.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-bold text-sm shadow-lg shadow-indigo-600/30 transition flex items-center justify-center gap-2"
          >
            {isProcessing ? (
              <>
                <RotateCw className="w-4 h-4 animate-spin" />
                <span>Processing Transaction...</span>
              </>
            ) : (
              <>
                <CheckCircle2 className="w-4 h-4" />
                <span>Pay ₹{total.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
