// 订单列表和详情共用状态、金额与手机号脱敏规则。
import type { AdminOrder, OrderStatus } from '../api/orders'
import { formatDateTime } from '../utils/date-time'

export const statusLabels: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待付款',
  IN_PRODUCTION: '制作中',
  PENDING_SHIPMENT: '待发货',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  AFTER_SALE: '售后中',
  REFUNDED: '退款成功',
}

export const normalStatusSteps: Array<{
  status: OrderStatus
  label: string
}> = [
  { status: 'PENDING_PAYMENT', label: '待付款' },
  { status: 'IN_PRODUCTION', label: '制作中' },
  { status: 'PENDING_SHIPMENT', label: '待发货' },
  { status: 'SHIPPED', label: '已发货' },
  { status: 'COMPLETED', label: '已完成' },
]

export function formatMoney(value: number): string {
  return `¥${Number(value || 0).toFixed(2)}`
}

export function formatDate(value: string | null): string {
  return formatDateTime(value)
}

export function maskPhone(value: string): string {
  if (value.length < 7) return value
  return `${value.slice(0, 3)}****${value.slice(-4)}`
}

export function beadSummary(order: AdminOrder): string {
  return order.items
    .map((item) => `${item.name}×${item.quantity}`)
    .join(' / ')
}

export function paymentLabel(order: AdminOrder): string {
  if (!order.payment) return '未支付'
  if (order.payment.channel === 'MOCK') return '模拟支付'
  if (order.payment.channel === 'WECHAT') {
    return '微信支付'
  }
  return order.payment.channel
}

export function statusHint(status: OrderStatus): string {
  const hints: Record<OrderStatus, string> = {
    PENDING_PAYMENT: '等待顾客付款；支付成功后自动进入制作中。',
    IN_PRODUCTION: '当前正在制作；完成制作后进入待发货。',
    PENDING_SHIPMENT: '制作已完成；填写物流信息后即可发货。',
    SHIPPED: '包裹已发出；等待顾客确认收货。',
    COMPLETED: '订单履约已全部完成。',
    CANCELLED: '订单已取消，不再继续正常履约。',
    AFTER_SALE: '订单正在售后处理中。',
    REFUNDED: '订单款项已退款。',
  }
  return hints[status]
}
