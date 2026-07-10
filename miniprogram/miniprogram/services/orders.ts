import { request } from './request'

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'IN_PRODUCTION'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface CreateOrderPayload {
  schemeId: number
  contactName: string
  contactPhone: string
  addressDetail: string
}

export interface OrderItem {
  skuId: number
  skuCode: string
  name: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface OrderResponse {
  id: number
  orderNo: string
  status: OrderStatus
  schemeId: number
  beadAmount: number
  craftFee: number
  cordFee: number
  totalAmount: number
  contactName: string
  contactPhone: string
  addressDetail: string
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

/** 创建待支付订单，价格由后端重新计算。 */
export function createOrder(
  payload: CreateOrderPayload,
): Promise<OrderResponse> {
  return request<OrderResponse>('/app/orders', {
    method: 'POST',
    data: payload,
  })
}

/** 查询当前安装身份拥有的全部订单。 */
export function listOrders(): Promise<OrderResponse[]> {
  return request<OrderResponse[]>('/app/orders')
}

/** 查询当前安装身份拥有的订单详情。 */
export function getOrder(id: number): Promise<OrderResponse> {
  return request<OrderResponse>(`/app/orders/${id}`)
}

/** 演示环境模拟支付，不调用真实微信支付。 */
export function mockPayOrder(id: number): Promise<OrderResponse> {
  return request<OrderResponse>(`/app/orders/${id}/mock-pay`, {
    method: 'POST',
  })
}

/** 取消待支付订单并释放预占库存。 */
export function cancelOrder(id: number): Promise<OrderResponse> {
  return request<OrderResponse>(`/app/orders/${id}/cancel`, {
    method: 'POST',
  })
}
