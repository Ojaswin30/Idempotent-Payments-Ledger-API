export interface Product {
  id: string;
  name: string;
  tagline: string;
  price: number;
  currency: string;
  rating: number;
  reviewsCount: number;
  image: string;
  category: string;
  badge?: string;
  inStock: boolean;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface CreatePaymentPayload {
  customerId: string;
  amount: number;
  currency: string;
  description: string;
}

export interface LedgerEntry {
  id: string;
  accountId: string;
  entryType: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  createdAt: string;
}

export interface PaymentResponse {
  paymentId: string;
  customerId: string;
  amount: number;
  currency: string;
  description: string;
  status: 'CREATED' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';
  version: number;
  createdAt: string;
  updatedAt: string;
  ledgerEntries: LedgerEntry[];
  isReplay?: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  traceId?: string;
  details?: string[];
}
