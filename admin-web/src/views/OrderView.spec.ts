import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrderView from './OrderView.vue'
import {
  completeOrderProduction,
  getAdminOrder,
  getOrderStatistics,
  listAdminOrders,
} from '../api/orders'

vi.mock('../api/orders', () => ({
  listAdminOrders: vi.fn(),
  getOrderStatistics: vi.fn(),
  getAdminOrder: vi.fn(),
  completeOrderProduction: vi.fn(),
  shipAdminOrder: vi.fn(),
  completeAdminOrder: vi.fn(),
  remindAdminOrderPayment: vi.fn(),
  cancelAdminOrder: vi.fn(),
  updateAdminOrderAddress: vi.fn(),
}))

const productionOrder = {
  id: 41,
  orderNo: 'ORD202608080001',
  status: 'IN_PRODUCTION' as const,
  schemeId: 12,
  beadAmount: 304.8,
  craftFee: 10,
  cordFee: 0,
  discountAmount: 20,
  totalAmount: 294.8,
  couponName: '满200减20元',
  contactName: '张三',
  contactPhone: '13800000000',
  addressDetail: '上海市浦东新区演示路 1 号',
  orderNote: '周末签收',
  product: {
    name: '暖光粉晶手串',
    schemeNo: '#12',
    wristCircumferenceMm: 160,
    fitType: 'STANDARD',
    cordName: '透明弹力绳',
    beadCount: 21,
    estimatedMinutes: 24,
    previewUrl: null,
  },
  items: [{
    skuId: 2001,
    skuCode: 'BEAD-CRYSTAL-001',
    name: '白水晶 8mm',
    quantity: 21,
    unitPrice: 12.8,
    totalPrice: 268.8,
  }],
  payment: {
    channel: 'MOCK', status: 'SUCCESS', amount: 294.8,
    paidAt: '2026-08-08T10:24:00',
  },
  fulfillment: {
    id: 51,
    status: 'IN_PRODUCTION' as const,
    logisticsCompany: null,
    trackingNo: null,
    productionStartedAt: '2026-08-08T10:24:00',
    productionCompletedAt: null,
    shippedAt: null,
    completedAt: null,
  },
  operations: [{
    id: 1, actionCode: 'ORDER_CREATED', operatorType: 'USER' as const,
    operatorName: '用户', content: '提交订单', occurredAt: '2026-08-08T10:21:00',
  }],
  paidAt: '2026-08-08T10:24:00',
  cancelledAt: null,
  shippedAt: null,
  completedAt: null,
  createdAt: '2026-08-08T10:21:00',
  updatedAt: '2026-08-08T10:24:00',
}

describe('OrderView', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      items: [productionOrder], total: 1, page: 1, size: 10,
    })
    vi.mocked(getOrderStatistics).mockResolvedValue({
      total: 1, pendingPayment: 0, inProduction: 1, pendingShipment: 0,
      shipped: 0, completed: 0, cancelled: 0, afterSale: 0, refunded: 0,
    })
    vi.mocked(getAdminOrder).mockResolvedValue(productionOrder)
    vi.mocked(completeOrderProduction).mockResolvedValue({
      ...productionOrder,
      status: 'PENDING_SHIPMENT',
      fulfillment: {
        ...productionOrder.fulfillment,
        status: 'PENDING_SHIPMENT',
        productionCompletedAt: '2026-08-08T11:00:00',
      },
    })
  })

  it('renders a compact order table and opens the detail page', async () => {
    const wrapper = mount(OrderView)
    await flushPromises()

    expect(wrapper.text()).toContain('订单列表')
    expect(wrapper.text()).toContain('ORD202608080001')
    expect(wrapper.text()).toContain('白水晶 8mm×21')
    expect(wrapper.text()).toContain('制作中')
    expect(wrapper.findAll('.statistic-icon')).toHaveLength(8)
    expect(wrapper.text()).toContain('138****0000')

    await wrapper.get('[data-test="toggle-order-phones"]').trigger('click')
    expect(wrapper.text()).toContain('13800000000')

    await wrapper.get('[data-test="open-order"]').trigger('click')
    await flushPromises()

    expect(getAdminOrder).toHaveBeenCalledWith(41)
    expect(wrapper.text()).toContain('订单详情')
    expect(wrapper.text()).toContain('顾客与收货信息')
    expect(wrapper.text()).toContain('珠材 BOM 明细')
    expect(wrapper.findAll('.step-check')).toHaveLength(1)
    expect(wrapper.get('.step-check').attributes('src')).toContain('check-line.svg')
  })

  it('renders every completed progress node as a check mark', async () => {
    vi.mocked(getAdminOrder).mockResolvedValueOnce({
      ...productionOrder,
      status: 'COMPLETED',
      shippedAt: '2026-08-08T11:30:00',
      completedAt: '2026-08-08T12:00:00',
      fulfillment: {
        ...productionOrder.fulfillment,
        status: 'COMPLETED',
        productionCompletedAt: '2026-08-08T11:00:00',
        shippedAt: '2026-08-08T11:30:00',
        completedAt: '2026-08-08T12:00:00',
      },
    })

    const wrapper = mount(OrderView)
    await flushPromises()
    await wrapper.get('[data-test="open-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.step-check')).toHaveLength(5)
  })

  it('completes production from the list and refreshes the table', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(OrderView)
    await flushPromises()

    await wrapper.get('[data-test="complete-production"]').trigger('click')
    await flushPromises()

    expect(completeOrderProduction).toHaveBeenCalledWith(41)
    expect(wrapper.text()).toContain('已进入待发货')
  })
})
