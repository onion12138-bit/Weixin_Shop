import type {
  OrderResponse,
  OrderStatus,
} from '../services/orders'

const STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  IN_PRODUCTION: '制作中',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const PROGRESS_INDEX: Record<OrderStatus, number> = {
  PENDING_PAYMENT: 0,
  PAID: 1,
  IN_PRODUCTION: 2,
  SHIPPED: 3,
  COMPLETED: 4,
  CANCELLED: 0,
}

/**
 * 把后端订单转换为列表卡片需要的稳定显示字段。
 */
export function toOrderCard(order: OrderResponse) {
  const beadCount = order.items.reduce(
    (sum, item) => sum + item.quantity,
    0,
  )
  return {
    ...order,
    statusText: STATUS_TEXT[order.status],
    statusClass: order.status.toLowerCase().replace('_', '-'),
    amountText: money(order.totalAmount),
    beadCountText: `共 ${beadCount} 颗`,
    itemSummary: order.items
      .map((item) => `${item.name} ×${item.quantity}`)
      .join(' · '),
    createdAtText: displayDate(order.createdAt),
  }
}

export type OrderCard = ReturnType<typeof toOrderCard>

/**
 * 生成订单详情页字段，并集中决定哪些状态允许模拟支付或取消。
 */
export function toOrderDetail(order: OrderResponse) {
  return {
    ...toOrderCard(order),
    canMockPay: order.status === 'PENDING_PAYMENT',
    canCancel: order.status === 'PENDING_PAYMENT',
    progressIndex: PROGRESS_INDEX[order.status],
    items: order.items.map((item) => ({
      ...item,
      quantityText: `${item.quantity} 颗`,
      unitPriceText: money(item.unitPrice),
      totalPriceText: money(item.totalPrice),
    })),
    beadAmountText: money(order.beadAmount),
    craftFeeText: money(order.craftFee),
    cordFeeText: money(order.cordFee),
  }
}

export type OrderDetail = ReturnType<typeof toOrderDetail>

function money(value: number): string {
  return `¥${Number(value).toFixed(2)}`
}

function displayDate(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}
