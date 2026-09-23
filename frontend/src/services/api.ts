import { CreatePaymentPayload, PaymentResponse } from '../types/payment';

const API_BASE = '/api/v1/payments';

export async function createPaymentApi(
  idempotencyKey: string,
  payload: CreatePaymentPayload
): Promise<{ data: PaymentResponse; isReplay: boolean; status: number }> {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(payload)
  });

  const replayHeader = response.headers.get('X-Idempotent-Replay');
  const isReplay = replayHeader === 'true' || response.status === 200;
  const data = await response.json();

  if (!response.ok) {
    throw {
      status: response.status,
      message: data.message || 'Payment processing failed',
      error: data.error || 'API_ERROR',
      details: data.details
    };
  }

  return { data, isReplay, status: response.status };
}

export async function getPaymentApi(paymentId: string): Promise<PaymentResponse> {
  const response = await fetch(`${API_BASE}/${paymentId}`);
  if (!response.ok) {
    throw new Error('Failed to fetch payment details');
  }
  return response.json();
}

export async function listPaymentsApi(
  customerId: string,
  page = 0,
  size = 20
): Promise<{ content: PaymentResponse[]; totalElements: number }> {
  const response = await fetch(`${API_BASE}?customerId=${encodeURIComponent(customerId)}&page=${page}&size=${size}`);
  if (!response.ok) {
    throw new Error('Failed to fetch payment history');
  }
  return response.json();
}

export async function cancelPaymentApi(paymentId: string, reason?: string): Promise<PaymentResponse> {
  const url = reason
    ? `${API_BASE}/${paymentId}/cancel?reason=${encodeURIComponent(reason)}`
    : `${API_BASE}/${paymentId}/cancel`;

  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || 'Failed to cancel payment');
  }
  return response.json();
}

export async function checkBackendHealth(): Promise<boolean> {
  try {
    const response = await fetch('/actuator/health');
    const data = await response.json();
    return data.status === 'UP';
  } catch {
    return false;
  }
}
