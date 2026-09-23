import React, { useState } from 'react';
import { Star, ShoppingCart, Zap, Check } from 'lucide-react';
import { FEATURED_PRODUCTS } from '../data/products';
import { Product } from '../types/payment';
import { useCart } from '../context/CartContext';

interface ProductCatalogProps {
  onDirectCheckout: (product: Product) => void;
}

export const ProductCatalog: React.FC<ProductCatalogProps> = ({ onDirectCheckout }) => {
  const { addToCart } = useCart();
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [recentlyAddedId, setRecentlyAddedId] = useState<string | null>(null);

  const categories = ['All', 'Audio', 'Accessories', 'Wearables', 'Photography'];

  const filteredProducts =
    selectedCategory === 'All'
      ? FEATURED_PRODUCTS
      : FEATURED_PRODUCTS.filter((p) => p.category === selectedCategory);

  const handleAddToCart = (product: Product) => {
    addToCart(product);
    setRecentlyAddedId(product.id);
    setTimeout(() => setRecentlyAddedId(null), 1500);
  };

  return (
    <div className="space-y-8">
      {/* Hero Banner with Idempotency Highlights */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-indigo-900/60 via-slate-900 to-slate-950 border border-indigo-500/20 p-8 sm:p-12 shadow-2xl">
        <div className="max-w-2xl space-y-4 relative z-10">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-semibold">
            <Zap className="w-3.5 h-3.5" />
            <span>Demonstrating Distributed Payment Safety</span>
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight leading-tight">
            Seamless Commerce with Guaranteed Idempotency.
          </h2>
          <p className="text-sm sm:text-base text-slate-300 leading-relaxed">
            Every transaction is backed by client-generated UUID keys, Redis atomic locking (<code className="text-indigo-400 font-mono text-xs">SETNX</code>), and balanced double-entry accounting. Double-clicks and network retries are 100% duplicate-proof.
          </p>
        </div>

        {/* Decorative Grid Glow */}
        <div className="absolute top-0 right-0 -mt-12 -mr-12 w-96 h-96 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />
      </div>

      {/* Category Pills */}
      <div className="flex items-center space-x-2 overflow-x-auto pb-2 custom-scroll">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-semibold whitespace-nowrap transition ${
              selectedCategory === cat
                ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/25'
                : 'bg-slate-900/80 text-slate-400 hover:text-white border border-slate-800'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Product Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredProducts.map((product) => (
          <div
            key={product.id}
            className="group relative bg-slate-900/60 border border-slate-800 rounded-3xl overflow-hidden hover:border-slate-700 transition duration-300 flex flex-col shadow-lg hover:shadow-2xl hover:shadow-indigo-500/5"
          >
            {/* Image & Badge */}
            <div className="relative aspect-[4/3] overflow-hidden bg-slate-950">
              <img
                src={product.image}
                alt={product.name}
                className="w-full h-full object-cover group-hover:scale-105 transition duration-500"
                loading="lazy"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-transparent to-transparent opacity-60" />

              {product.badge && (
                <span className="absolute top-4 left-4 px-3 py-1 rounded-full bg-indigo-600/90 backdrop-blur-md text-white text-[11px] font-bold shadow-md">
                  {product.badge}
                </span>
              )}
            </div>

            {/* Content Details */}
            <div className="p-6 flex-1 flex flex-col justify-between space-y-4">
              <div className="space-y-2">
                <div className="flex items-center space-x-2 text-amber-400 text-xs font-semibold">
                  <Star className="w-3.5 h-3.5 fill-current" />
                  <span>{product.rating}</span>
                  <span className="text-slate-500 font-normal">({product.reviewsCount} reviews)</span>
                </div>

                <h3 className="text-base font-bold text-white group-hover:text-indigo-400 transition line-clamp-1">
                  {product.name}
                </h3>

                <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                  {product.tagline}
                </p>
              </div>

              {/* Price & Actions */}
              <div className="pt-4 border-t border-slate-800 flex items-center justify-between">
                <div>
                  <span className="text-[10px] text-slate-500 uppercase tracking-wider block font-semibold">Price</span>
                  <span className="text-lg font-extrabold text-white font-mono">
                    ₹{product.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </span>
                </div>

                <div className="flex items-center space-x-2">
                  <button
                    onClick={() => handleAddToCart(product)}
                    className="p-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition flex items-center justify-center border border-slate-700"
                    title="Add to Cart"
                  >
                    {recentlyAddedId === product.id ? (
                      <Check className="w-4 h-4 text-emerald-400" />
                    ) : (
                      <ShoppingCart className="w-4 h-4" />
                    )}
                  </button>

                  <button
                    onClick={() => onDirectCheckout(product)}
                    className="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold shadow-md shadow-indigo-600/30 transition flex items-center gap-1.5"
                  >
                    <Zap className="w-3.5 h-3.5" />
                    <span>Buy Now</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
