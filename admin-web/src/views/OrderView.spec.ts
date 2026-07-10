import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrderView from './OrderView.vue'
import {
  listAdminOrders,
  startOrderProduction,
} from '../api/orders'

vi.mock('../api/orders', () => ({
  listAdminOrders: vi.fn(),
  startOrderProduction: vi.fn(),
  shipAdminOrder: vi.fn(),
  completeAdminOrder: vi.fn(),
}))

const paidOrder = {
  id: 41,
  orderNo: 'ORD202607110001',
  status: 'PAID' as const,
  schemeId: 12,
  totalAmount: 314.8,
  contactName: '张三',
  contactPhone: '13800000000',
  addressDetail: '上海市浦东新区演示路 1 号',
  items: [
    {
      skuId: 2001,
      skuCode: 'BEAD-CRYSTAL-001',
      name: '白水晶 8mm',
      quantity: 12,
      unitPrice: 12.8,
      totalPrice: 153.6,
    },
    {
      skuId: 2002,
      skuCode: 'BEAD-AMETHYST-001',
      name: '紫水晶 8mm',
      quantity: 9,
      unitPrice: 16.8,
      totalPrice: 151.2,
    },
  ],
  fulfillment: {
    id: 51,
    status: 'PENDING' as const,
    logisticsCompany: null,
    trackingNo: null,
    shippedAt: null,
    completedAt: null,
  },
  paidAt: '2026-07-11T05:00:00',
  shippedAt: null,
  completedAt: null,
  createdAt: '2026-07-11T04:59:00',
  updatedAt: '2026-07-11T05:00:00',
}

describe('OrderView', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      items: [paidOrder],
      total: 1,
      page: 1,
      size: 20,
    })
    vi.mocked(startOrderProduction).mockResolvedValue({
      ...paidOrder,
      status: 'IN_PRODUCTION',
      fulfillment: {
        ...paidOrder.fulfillment,
        status: 'IN_PRODUCTION' as const,
      },
    })
  })

  it('renders order amount, receiver, BOM and available action', async () => {
    const wrapper = mount(OrderView)
    await flushPromises()

    expect(wrapper.text()).toContain('ORD202607110001')
    expect(wrapper.text()).toContain('¥314.80')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('白水晶 8mm')
    expect(wrapper.text()).toContain('12 颗')
    expect(wrapper.get('[data-test="start-production"]').text())
      .toContain('开始制作')
  })

  it('starts production and replaces the order state', async () => {
    const wrapper = mount(OrderView)
    await flushPromises()

    await wrapper.get('[data-test="start-production"]').trigger('click')
    await flushPromises()

    expect(startOrderProduction).toHaveBeenCalledWith(41)
    expect(wrapper.text()).toContain('制作中')
  })
})
