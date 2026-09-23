import React, { useState } from 'react';
import { CartProvider, useCart } from './context/CartContext';
import { Navbar } from './components/Navbar';
import { ProductCatalog } from './components/ProductCatalog';
import { CartDrawer } from './components/CartDrawer';
import { CheckoutModal } from './components/CheckoutModal';
import { OrderReceiptModal } from './components/OrderReceiptModal';
import { OrdersHistory } from './components/OrdersHistory';
import { Product, PaymentResponse } from './types/payment';

const AppContent: React.FC = () => {
  const [currentTab, setCurrentTab] = useState<'store' | 'orders'>('store');
  const [isCheckoutOpen, setIsCheckoutOpen] = useState<boolean>(false);
  const [lastReceipt, setLastReceipt] = useState<PaymentResponse | null>(null);
  const [isReceiptReplay, setIsReceiptReplay] = useState<boolean>(false);
  const { addToCart } = useCart();

  const handleDirectCheckout = (product: Product) => {
    addToCart(product);
    setIsCheckoutOpen(true);
  };

  const handlePaymentSuccess = (response: PaymentResponse, isReplay: boolean) => {
    setLastReceipt(response);
    setIsReceiptReplay(isReplay);
  };

  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
      <Navbar currentTab={currentTab} setCurrentTab={setCurrentTab} />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {currentTab === 'store' ? (
          <ProductCatalog onDirectCheckout={handleDirectCheckout} />
        ) : (
          <OrdersHistory />
        )}
      </main>

      <CartDrawer onOpenCheckout={() => setIsCheckoutOpen(true)} />

      <CheckoutModal
        isOpen={isCheckoutOpen}
        onClose={() => setIsCheckoutOpen(false)}
        onSuccess={handlePaymentSuccess}
      />

      <OrderReceiptModal
        receipt={lastReceipt}
        isReplay={isReceiptReplay}
        onClose={() => setLastReceipt(null)}
        onViewOrders={() => {
          setLastReceipt(null);
          setCurrentTab('orders');
        }}
      />

      {/* Modern Footer */}
      <footer className="border-t border-slate-900 bg-slate-950/60 py-8 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 space-y-2">
          <p>NovaStore Production Showcase — Powered by Spring Boot 3, Redis SETNX, PostgreSQL, and Apache Kafka.</p>
          <div className="flex items-center justify-center space-x-4 text-slate-400">
            <a href="http://localhost:8080/index.html" target="_blank" rel="noreferrer" className="hover:text-indigo-400 transition">
              Developer Testing Console
            </a>
            <span>•</span>
            <a href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noreferrer" className="hover:text-indigo-400 transition">
              OpenAPI Swagger
            </a>
            <span>•</span>
            <a href="http://localhost:8080/actuator/health" target="_blank" rel="noreferrer" className="hover:text-indigo-400 transition">
              Actuator Health
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <CartProvider>
      <AppContent />
    </CartProvider>
  );
};

export default App;
