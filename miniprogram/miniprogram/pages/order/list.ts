/** 加载当前访客的订单卡片，支持下拉刷新和订单详情导航。 */
import { navigateOnce, resetNavigation } from '../../utils/navigation'
import { listOrders } from '../../services/orders'
import { ApiError } from '../../services/request'
import {
  toOrderCard,
  type OrderCard,
} from '../../utils/order-view-model'

Page({
  data: {
    loading: true,
    errorMessage: '',
    orders: [] as OrderCard[],
  },

  onShow() {
    resetNavigation(this)
    void this.loadOrders()
  },

  onPullDownRefresh() {
    void this.loadOrders().finally(() => wx.stopPullDownRefresh())
  },

  async loadOrders() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      this.setData({ orders: (await listOrders()).map(toOrderCard) })
    } catch (error) {
      this.setData({ errorMessage: readableError(error) })
    } finally {
      this.setData({ loading: false })
    }
  },

  openOrder(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    navigateOnce(this, { url: `/pages/order/detail?id=${id}` })
  },

  openSchemes() {
    navigateOnce(this, { url: '/pages/diy/schemes' })
  },
})

function readableError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : '订单加载失败'
}
