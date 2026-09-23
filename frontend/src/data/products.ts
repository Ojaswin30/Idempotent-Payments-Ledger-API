import { Product } from '../types/payment';

export const FEATURED_PRODUCTS: Product[] = [
  {
    id: 'prod_audio_01',
    name: 'AeroPulse Wireless ANC Headphones',
    tagline: 'Custom 40mm drivers with 48dB active hybrid noise cancellation',
    price: 3499.00,
    currency: 'INR',
    rating: 4.9,
    reviewsCount: 428,
    category: 'Audio',
    badge: 'Best Seller',
    inStock: true,
    image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80'
  },
  {
    id: 'prod_keyboard_02',
    name: 'ApexCraft 75% Mechanical Keyboard',
    tagline: 'Gasket-mounted hot-swap RGB keyboard with lubed linear switches',
    price: 2499.00,
    currency: 'INR',
    rating: 4.8,
    reviewsCount: 312,
    category: 'Accessories',
    badge: 'Popular',
    inStock: true,
    image: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80'
  },
  {
    id: 'prod_watch_03',
    name: 'Veloce Titanium Smartwatch Ultra',
    tagline: 'Dual-frequency GPS, sapphire crystal glass, 100m water resistance',
    price: 4999.00,
    currency: 'INR',
    rating: 4.9,
    reviewsCount: 189,
    category: 'Wearables',
    badge: 'New Release',
    inStock: true,
    image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80'
  },
  {
    id: 'prod_mouse_04',
    name: 'Precision Ergonomic Gaming Mouse',
    tagline: 'PAW3395 optical sensor with 26,000 DPI and ultra-low latency wireless',
    price: 1299.00,
    currency: 'INR',
    rating: 4.7,
    reviewsCount: 540,
    category: 'Accessories',
    inStock: true,
    image: 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=800&q=80'
  },
  {
    id: 'prod_lens_05',
    name: 'Prime Cinema 50mm f/1.2 Lens',
    tagline: 'Ultra-fast aperture portrait lens with nano-anti-reflective multi-coating',
    price: 8999.00,
    currency: 'INR',
    rating: 5.0,
    reviewsCount: 94,
    category: 'Photography',
    badge: 'Pro Tier',
    inStock: true,
    image: 'https://images.unsplash.com/photo-1617005082133-548c4dd27f35?auto=format&fit=crop&w=800&q=80'
  },
  {
    id: 'prod_speaker_06',
    name: 'SoundSphere 360° Studio Speaker',
    tagline: 'High-fidelity spatial audio with multi-room mesh network sync',
    price: 1999.00,
    currency: 'INR',
    rating: 4.6,
    reviewsCount: 220,
    category: 'Audio',
    inStock: true,
    image: 'https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=80'
  }
];
