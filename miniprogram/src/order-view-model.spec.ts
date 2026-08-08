import {
  toOrderCard,
  toOrderDetail,
} from '../miniprogram/utils/order-view-model'

const pendingOrder = {
  id: 41,
  orderNo: 'ORD202607110001',
  status: 'PENDING_PAYMENT' as const,
  schemeId: 12,
  beadAmount: 304.8,
  craftFee: 10,
  cordFee: 0,
  couponId: null,
  discountAmount: 0,
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
  logisticsCompany: null,
  trackingNo: null,
  paidAt: null,
  shippedAt: null,
  completedAt: null,
  createdAt: '2026-07-11T05:00:00',
  updatedAt: '2026-07-11T05:00:00',
}

describe('order view model', () => {
  it('formats an order card with status, amount and BOM summary', () => {
    expect(toOrderCard(pendingOrder)).toEqual(expect.objectContaining({
      id: 41,
      orderNo: 'ORD202607110001',
      statusText: '待付款',
      amountText: '¥314.80',
      beadCountText: '共 21 颗',
      itemSummary: '白水晶 8mm ×12 · 紫水晶 8mm ×9',
      createdAtText: '2026-07-11 05:00',
    }))
  })

  it('exposes payment actions only for pending orders', () => {
    expect(toOrderDetail(pendingOrder)).toEqual(expect.objectContaining({
      canMockPay: true,
      canCancel: true,
      progressIndex: 0,
      items: [
        expect.objectContaining({ quantityText: '12 颗' }),
        expect.objectContaining({ quantityText: '9 颗' }),
      ],
    }))

    expect(toOrderDetail({
      ...pendingOrder,
      status: 'SHIPPED',
      logisticsCompany: '顺丰速运',
      trackingNo: 'SF001',
      paidAt: '2026-07-11T05:02:00',
      shippedAt: '2026-07-12T05:00:00',
    })).toEqual(expect.objectContaining({
      statusText: '待收货',
      canMockPay: false,
      canCancel: false,
      canConfirmReceipt: true,
      progressIndex: 3,
    }))
  })
})
