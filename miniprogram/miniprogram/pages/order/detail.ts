/** 展示订单并串行处理付款、取消与收货，防止确认弹窗期间重复操作。 */
import {
  cancelOrder,
  confirmOrderReceipt,
  getOrder,
  mockPayOrder,
  type OrderResponse,
} from '../../services/orders'
import { ApiError } from '../../services/request'
import {
  toOrderDetail,
  type OrderDetail,
} from '../../utils/order-view-model'

interface ActionConfirmation {
  title: string
  content: string
  confirmText: string
  permission: 'canMockPay' | 'canCancel' | 'canConfirmReceipt'
}

Page({
  data: {
    id: 0,
    loading: true,
    operating: false,
    errorMessage: '',
    countdownText: '',
    expiresAtText: '',
    closedReasonText: '',
    paymentExpired: false,
    order: null as OrderDetail | null,
    steps: ['待付款', '制作中', '待发货', '待收货', '已完成'],
  },

  countdownTimer: null as number | null,
  deadline: 0,
  pageDisposed: false,
  pageHidden: false,
  expirationRefreshDone: false,
  loadVersion: 0,

  onShow() {
    this.pageHidden = false
    if (this.data.order) void this.loadOrder()
  },
  onHide() { this.pageHidden = true; this.stopCountdown() },
  onUnload() { this.pageDisposed = true; this.stopCountdown() },
  stopCountdown() {
    if (this.countdownTimer !== null) clearTimeout(this.countdownTimer)
    this.countdownTimer = null
  },
  applyOrder(order: OrderResponse) {
    if (this.pageDisposed) return
    this.stopCountdown()
    const serverTime = Date.parse(order.serverTime || '')
    const expiresAt = Date.parse(order.expiresAt || '')
    this.deadline = Number.isFinite(serverTime) && Number.isFinite(expiresAt)
      ? Date.now() + expiresAt - serverTime : 0
    this.setData({ order: toOrderDetail(order), paymentExpired: false,
      expiresAtText: order.expiresAt ? localDateTime(order.expiresAt) : '',
      closedReasonText: order.closedReason === 'PAYMENT_TIMEOUT' ? '支付超时，订单已关闭，库存与优惠券占用已释放'
        : order.closedReason === 'CUSTOMER_CANCELLED' ? '你已取消订单，库存与优惠券占用已释放'
        : order.closedReason === 'ADMIN_CANCELLED' ? '商家已取消订单，库存与优惠券占用已释放' : '',
      countdownText: '',
    })
    if (order.status === 'PENDING_PAYMENT' && this.deadline) this.tickCountdown()
  },
  tickCountdown() {
    if (this.pageDisposed || this.pageHidden || this.data.order?.status !== 'PENDING_PAYMENT') return
    const seconds = Math.max(0, Math.ceil((this.deadline - Date.now()) / 1000))
    this.setData({ countdownText: `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`,
      paymentExpired: seconds === 0,
      order: { ...this.data.order, canMockPay: seconds > 0 },
    })
    if (seconds > 0) this.countdownTimer = setTimeout(() => this.tickCountdown(), 1000)
    else if (!this.expirationRefreshDone) {
      this.expirationRefreshDone = true
      void this.loadOrder()
    }
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
    const version = ++this.loadVersion
    this.setData({ loading: true, errorMessage: '' })
    try {
      const order = await getOrder(this.data.id)
      if (!this.pageDisposed && version === this.loadVersion) this.applyOrder(order)
    } catch (error) {
      if (!this.pageDisposed && version === this.loadVersion) this.setData({ errorMessage: readableError(error, '订单加载失败') })
    } finally {
      if (!this.pageDisposed && version === this.loadVersion) this.setData({ loading: false })
    }
  },

  async mockPay() {
    await this.runAction(
      () => mockPayOrder(this.data.id),
      '模拟支付成功',
      {
        title: '模拟支付',
        content: '本操作仅用于产品演示，不会调用微信支付或产生真实扣款。是否继续？',
        confirmText: '确认支付',
        permission: 'canMockPay',
      },
    )
  },

  async cancel() {
    await this.runAction(() => cancelOrder(this.data.id), '订单已取消', {
      title: '取消订单',
      content: '取消后将释放已预占库存，是否继续？',
      confirmText: '确认取消',
      permission: 'canCancel',
    })
  },

  async confirmReceipt() {
    await this.runAction(
      () => confirmOrderReceipt(this.data.id),
      '已确认收货',
      {
        title: '确认收货',
        content: '确认已经收到手串吗？确认后订单将进入已完成。',
        confirmText: '确认收货',
        permission: 'canConfirmReceipt',
      },
    )
  },

  async runAction(
    action: () => ReturnType<typeof getOrder>,
    message: string,
    confirmation?: ActionConfirmation,
  ) {
    if (this.data.operating) return
    if (confirmation && !this.data.order?.[confirmation.permission]) return
    this.setData({ operating: true, errorMessage: '' })
    try {
      if (confirmation) {
        const { title, content, confirmText, permission } = confirmation
        if (!await confirmAction(title, content, confirmText)) return
        if (!this.data.order?.[permission]) return
      }
      const result = await action()
      if (this.pageDisposed) return
      ++this.loadVersion // 操作结果优先于之前发出的读取。
      this.applyOrder(result)
      wx.showToast(result.closedReason === 'PAYMENT_TIMEOUT'
        ? { title: '订单已超时关闭', icon: 'none' } : { title: message, icon: 'success' })
    } catch (error) {
      if (!this.pageDisposed) this.setData({ errorMessage: readableError(error, '订单操作失败') })
    } finally {
      if (!this.pageDisposed) this.setData({ operating: false, loading: false })
    }
  },

  copyTrackingNo() {
    const trackingNo = this.data.order?.trackingNo?.trim()
    if (!trackingNo) return
    wx.setClipboardData({
      data: trackingNo,
      fail: () => wx.showToast({ title: '复制失败，请重试', icon: 'none' }),
    })
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

function localDateTime(value: string): string {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
