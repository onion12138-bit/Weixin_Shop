import { request } from './http'
import type { PageResponse } from './catalog'

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'IN_PRODUCTION'
  | 'PENDING_SHIPMENT'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'AFTER_SALE'
  | 'REFUNDED'

export interface AdminOrderItem {
  skuId: number
  skuCode: string
  name: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface AdminOrderProduct {
  name: string
  schemeNo: string
  wristCircumferenceMm: number | null
  fitType: string | null
  cordName: string
  beadCount: number
  estimatedMinutes: number
  previewUrl: string | null
}

export interface PaymentSummary {
  channel: string
  status: string
  amount: number
  paidAt: string | null
}

export interface FulfillmentSummary {
  id: number
  status: 'IN_PRODUCTION' | 'PENDING_SHIPMENT' | 'SHIPPED' | 'COMPLETED'
  logisticsCompany: string | null
  trackingNo: string | null
  productionStartedAt: string | null
  productionCompletedAt: string | null
  shippedAt: string | null
  completedAt: string | null
}

export interface OrderOperation {
  id: number
  actionCode: string
  operatorType: 'USER' | 'SYSTEM' | 'ADMIN'
  operatorName: string
  content: string
  occurredAt: string
}

export interface AdminOrder {
  id: number
  orderNo: string
  status: OrderStatus
  schemeId: number
  beadAmount: number
  craftFee: number
  cordFee: number
  discountAmount: number
  shippingFee: number
  totalAmount: number
  couponName: string | null
  contactName: string
  contactPhone: string
  addressDetail: string
  orderNote: string | null
  product: AdminOrderProduct
  items: AdminOrderItem[]
  payment: PaymentSummary | null
  fulfillment: FulfillmentSummary | null
  operations: OrderOperation[]
  paidAt: string | null
  cancelledAt: string | null
  shippedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminOrderQuery {
  orderNo?: string
  contactName?: string
  contactPhone?: string
  status?: OrderStatus | ''
  startDate?: string
  endDate?: string
  minAmount?: string
  maxAmount?: string
  paymentMethod?: string
  sortDirection?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface OrderStatistics {
  total: number
  pendingPayment: number
  inProduction: number
  pendingShipment: number
  shipped: number
  completed: number
  cancelled: number
  afterSale: number
  refunded: number
}

function queryString(query: AdminOrderQuery, includePage = true): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  if (includePage) {
    params.set('page', String(query.page || 1))
    params.set('size', String(query.size || 10))
  } else {
    params.delete('page')
    params.delete('size')
    params.delete('status')
    params.delete('sortDirection')
  }
  return params.toString()
}

export function listAdminOrders(
  query: AdminOrderQuery = {},
): Promise<PageResponse<AdminOrder>> {
  return request<PageResponse<AdminOrder>>(`/admin/orders?${queryString(query)}`)
}

export function getAdminOrder(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}`)
}

export function getOrderStatistics(
  query: AdminOrderQuery = {},
): Promise<OrderStatistics> {
  return request<OrderStatistics>(
    `/admin/orders/statistics?${queryString(query, false)}`,
  )
}

export function completeOrderProduction(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/complete-production`, {
    method: 'POST',
  })
}

export function shipAdminOrder(
  id: number,
  logisticsCompany: string,
  trackingNo: string,
): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/ship`, {
    method: 'POST',
    body: JSON.stringify({ logisticsCompany, trackingNo }),
  })
}

export function completeAdminOrder(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/complete`, {
    method: 'POST',
  })
}

export function remindAdminOrderPayment(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/remind-payment`, {
    method: 'POST',
  })
}

export function cancelAdminOrder(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/cancel`, {
    method: 'POST',
  })
}

export interface UpdateOrderAddressPayload {
  contactName: string
  contactPhone: string
  addressDetail: string
  orderNote?: string
}

export function updateAdminOrderAddress(
  id: number,
  payload: UpdateOrderAddressPayload,
): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/address`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}
