import { getScheme } from '../../services/diy'
import { createOrder } from '../../services/orders'
import { ApiError } from '../../services/request'
import {
  toSchemeCard,
  type SchemeCard,
} from '../../utils/diy-view-model'

Page({
  data: {
    schemeId: 0,
    loading: true,
    submitting: false,
    errorMessage: '',
    scheme: null as SchemeCard | null,
    contactName: '',
    contactPhone: '',
    addressDetail: '',
  },

  onLoad(options: Record<string, string>) {
    const schemeId = Number(options.schemeId || 0)
    this.setData({ schemeId })
    void this.loadScheme()
  },

  async loadScheme() {
    if (!this.data.schemeId) {
      this.setData({ loading: false, errorMessage: '方案编号无效' })
      return
    }
    this.setData({ loading: true, errorMessage: '' })
    try {
      this.setData({
        scheme: toSchemeCard(await getScheme(this.data.schemeId)),
      })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '方案加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  updateField(
    event: WechatMiniprogram.Input & WechatMiniprogram.TouchEvent,
  ) {
    const field = String(event.currentTarget.dataset.field || '')
    if (!field) return
    this.setData({ [field]: event.detail.value } as never)
  },

  async submitOrder() {
    const contactName = this.data.contactName.trim()
    const contactPhone = this.data.contactPhone.trim()
    const addressDetail = this.data.addressDetail.trim()
    if (!contactName || !contactPhone || !addressDetail) {
      this.setData({ errorMessage: '请完整填写收货人、联系电话和地址' })
      return
    }

    this.setData({ submitting: true, errorMessage: '' })
    try {
      const order = await createOrder({
        schemeId: this.data.schemeId,
        contactName,
        contactPhone,
        addressDetail,
      })
      wx.showToast({ title: '订单已创建', icon: 'success' })
      wx.navigateTo({ url: `/pages/order/detail?id=${order.id}` })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '创建订单失败') })
    } finally {
      this.setData({ submitting: false })
    }
  },

  openOrders() {
    wx.navigateTo({ url: '/pages/order/list' })
  },
})

function readableError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（${error.traceId}）`
      : error.message
  }
  return error instanceof Error ? error.message : fallback
}
