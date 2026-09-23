import React, { useEffect, useState } from 'react';
import { ShoppingBag, ShieldCheck, Clock, ExternalLink, Activity } from 'lucide-react';
import { useCart } from '../context/CartContext';
import { checkBackendHealth } from '../services/api';

interface NavbarProps {
  currentTab: 'store' | 'orders';
  setCurrentTab: (tab: 'store' | 'orders') => void;
}

export const Navbar: React.FC<NavbarProps> = ({ currentTab, setCurrentTab }) => {
  const { itemsCount, setIsCartOpen } = useCart();
  const [isBackendHealthy, setIsBackendHealthy] = useState<boolean>(true);

  useEffect(() => {
    const ping = async () => {
      const healthy = await checkBackendHealth();
      setIsBackendHealthy(healthy);
    };
    ping();
    const timer = setInterval(ping, 10000);
    return () => clearInterval(timer);
  }, []);

  return (
    <header className="sticky top-0 z-40 bg-slate-950/80 backdrop-blur-md border-b border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between">
        
        {/* Brand Logo & Tag */}
        <div className="flex items-center space-x-4 cursor-pointer" onClick={() => setCurrentTab('store')}>
          <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-violet-500 flex items-center justify-center shadow-lg shadow-indigo-500/25">
            <ShieldCheck className="w-6 h-6 text-white" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="text-xl font-extrabold tracking-tight text-white">NovaStore</span>
              <span className="px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-[10px] font-mono font-semibold">
                Idempotent API
              </span>
            </div>
            <p className="text-xs text-slate-400 hidden sm:block">Zero-Duplicate Financial Commerce</p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="flex items-center space-x-1 sm:space-x-2 bg-slate-900/90 border border-slate-800 p-1.5 rounded-2xl">
          <button
            onClick={() => setCurrentTab('store')}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition flex items-center gap-2 ${
              currentTab === 'store'
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <ShoppingBag className="w-4 h-4" />
            <span>Store</span>
          </button>

          <button
            onClick={() => setCurrentTab('orders')}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition flex items-center gap-2 ${
              currentTab === 'orders'
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <Clock className="w-4 h-4" />
            <span>My Orders & Refunds</span>
          </button>
        </nav>

        {/* Right Actions */}
        <div className="flex items-center space-x-3">
          {/* Developer Lab Switcher */}
          <a
            href="http://localhost:8080/index.html"
            target="_blank"
            rel="noreferrer"
            className="hidden lg:flex items-center gap-2 px-3 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white text-xs font-medium transition"
            title="Open Developer Testing Console & Double-Entry Ledger Inspector"
          >
            <Activity className="w-4 h-4 text-indigo-400" />
            <span>Dev Testing Lab</span>
            <ExternalLink className="w-3 h-3 text-slate-500" />
          </a>

          {/* Health status indicator */}
          <div className="hidden sm:flex items-center space-x-2 px-2.5 py-1.5 rounded-xl bg-slate-900 border border-slate-800">
            <span
              className={`w-2 h-2 rounded-full ${
                isBackendHealthy ? 'bg-emerald-400 animate-pulse' : 'bg-rose-500'
              }`}
            />
            <span className="text-[11px] font-mono text-slate-400">
              {isBackendHealthy ? 'API Connected' : 'Offline'}
            </span>
          </div>

          {/* Cart Icon Trigger */}
          <button
            onClick={() => setIsCartOpen(true)}
            className="relative p-3 rounded-2xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 hover:bg-indigo-600/20 transition flex items-center justify-center"
            aria-label="View Shopping Cart"
          >
            <ShoppingBag className="w-5 h-5" />
            {itemsCount > 0 && (
              <span className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-indigo-600 text-white text-[11px] font-bold flex items-center justify-center shadow-lg shadow-indigo-600/50">
                {itemsCount}
              </span>
            )}
          </button>
        </div>
      </div>
    </header>
  );
};
