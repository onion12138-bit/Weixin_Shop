import { request } from './http'
import type { PageResponse } from './catalog'

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'IN_PRODUCTION'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface AdminOrderItem {
  skuId: number
  skuCode: string
  name: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface FulfillmentSummary {
  id: number
  status: 'PENDING' | 'IN_PRODUCTION' | 'SHIPPED' | 'COMPLETED'
  logisticsCompany: string | null
  trackingNo: string | null
  shippedAt: string | null
  completedAt: string | null
}

export interface AdminOrder {
  id: number
  orderNo: string
  status: OrderStatus
  schemeId: number
  totalAmount: number
  contactName: string
  contactPhone: string
  addressDetail: string
  items: AdminOrderItem[]
  fulfillment: FulfillmentSummary | null
  paidAt: string | null
  shippedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminOrderQuery {
  status?: OrderStatus | ''
  page?: number
  size?: number
}

/** 查询后台订单，默认按后端规则返回最新订单。 */
export function listAdminOrders(
  query: AdminOrderQuery = {},
): Promise<PageResponse<AdminOrder>> {
  const params = new URLSearchParams()
  if (query.status) params.set('status', query.status)
  params.set('page', String(query.page || 1))
  params.set('size', String(query.size || 20))
  return request<PageResponse<AdminOrder>>(
    `/admin/orders?${params.toString()}`,
  )
}

/** 把已支付订单切换为制作中。 */
export function startOrderProduction(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/start-production`, {
    method: 'POST',
  })
}

/** 发货并保存模拟物流信息。 */
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

/** 完成已发货订单。 */
export function completeAdminOrder(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/complete`, {
    method: 'POST',
  })
}
