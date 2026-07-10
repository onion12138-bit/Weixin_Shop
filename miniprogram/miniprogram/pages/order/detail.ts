import {
  cancelOrder,
  getOrder,
  mockPayOrder,
} from '../../services/orders'
import { ApiError } from '../../services/request'
import {
  toOrderDetail,
  type OrderDetail,
} from '../../utils/order-view-model'

Page({
  data: {
    id: 0,
    loading: true,
    operating: false,
    errorMessage: '',
    order: null as OrderDetail | null,
    steps: ['已下单', '已支付', '制作中', '已发货', '已完成'],
  },

  onLoad(options: Record<string, string>) {
    this.setData({ id: Number(options.id || 0) })
    void this.loadOrder()
  },

  async loadOrder() {
    if (!this.data.id) {
      this.setData({ loading: false, errorMessage: '订单编号无效' })
      return
    }
    this.setData({ loading: true, errorMessage: '' })
    try {
      this.setData({ order: toOrderDetail(await getOrder(this.data.id)) })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '订单加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  async mockPay() {
    if (!await confirmAction(
      '模拟支付',
      '本操作仅用于产品演示，不会调用微信支付或产生真实扣款。是否继续？',
      '确认支付',
    )) return
    await this.runAction(
      () => mockPayOrder(this.data.id),
      '模拟支付成功',
    )
  },

  async cancel() {
    if (!await confirmAction(
      '取消订单',
      '取消后将释放已预占库存，是否继续？',
      '确认取消',
    )) return
    await this.runAction(() => cancelOrder(this.data.id), '订单已取消')
  },

  async runAction(
    action: () => ReturnType<typeof getOrder>,
    message: string,
  ) {
    this.setData({ operating: true, errorMessage: '' })
    try {
      this.setData({ order: toOrderDetail(await action()) })
      wx.showToast({ title: message, icon: 'success' })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '订单操作失败') })
    } finally {
      this.setData({ operating: false })
    }
  },
})

function confirmAction(
  title: string,
  content: string,
  confirmText: string,
): Promise<boolean> {
  return new Promise((resolve) => {
    wx.showModal({
      title,
      content,
      confirmText,
      cancelText: '返回',
      success: (response) => resolve(response.confirm),
      fail: () => resolve(false),
    })
  })
}

function readableError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : fallback
}
