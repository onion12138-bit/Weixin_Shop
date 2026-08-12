import {
  deleteScheme as deleteDiyScheme,
  listSchemes,
} from '../../services/diy'
import { getCommerceRule } from '../../services/commerce'
import {
  toSchemeCard,
  type SchemeCard,
} from '../../utils/diy-view-model'

interface PendingPreview {
  schemeId: number
  previewPending: boolean
}

const PREVIEW_PENDING_KEY = 'diy_preview_pending'
const MAX_SCHEME_COUNT = 3

Page({
  data: {
    loading: true,
    errorMessage: '',
    minimumOrderAmountCents: 2000,
    minimumOrderAmountText: '¥20.00',
    schemes: [] as Array<SchemeCard & { previewPending: boolean }>,
  },

  onShow() {
    void this.loadSchemes()
  },

  async loadSchemes() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const pending = readPendingPreview()
      const [rawSchemes, commerceRule] = await Promise.all([
        listSchemes(),
        getCommerceRule().catch(() => ({
          minimumOrderAmount: this.data.minimumOrderAmountCents / 100,
          freeShippingThreshold: 60,
          baseShippingFee: 8,
          configVersion: 0,
        })),
      ])
      const minimumOrderAmountCents = Math.max(
        0,
        Math.round(Number(commerceRule.minimumOrderAmount || 0) * 100),
      )
      const schemes = rawSchemes.map((scheme) => ({
        ...toSchemeCard(scheme),
        previewPending: pending?.schemeId === scheme.id && pending.previewPending,
      }))
      this.setData({
        schemes,
        minimumOrderAmountCents,
        minimumOrderAmountText: `¥${(minimumOrderAmountCents / 100).toFixed(2)}`,
      })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '方案加载失败',
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  openScheme(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    wx.navigateTo({ url: `/pages/diy/editor?schemeId=${id}` })
  },

  createOrder(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    const scheme = this.data.schemes.find((item) => item.id === id)
    if (!scheme) return
    if (scheme.totalAmountCents < this.data.minimumOrderAmountCents) {
      wx.showToast({
        title: `订单商品金额需满 ${this.data.minimumOrderAmountText} 才能下单`,
        icon: 'none',
      })
      return
    }
    wx.navigateTo({ url: `/pages/order/confirm?schemeId=${id}` })
  },

  openOrders() {
    wx.navigateTo({ url: '/pages/order/list' })
  },

  async deleteScheme(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    const confirmed = await confirmDelete()
    if (!confirmed) return
    wx.showLoading({ title: '正在删除', mask: true })
    try {
      await deleteDiyScheme(id)
      await this.loadSchemes()
      wx.showToast({ title: '已删除', icon: 'success' })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '删除失败',
      })
    } finally {
      wx.hideLoading()
    }
  },

  createScheme() {
    if (this.data.schemes.length >= MAX_SCHEME_COUNT) {
      wx.showToast({ title: '最多保存 3 个 DIY 方案', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/diy/editor' })
  },
})

function readPendingPreview(): PendingPreview | null {
  const raw = wx.getStorageSync(PREVIEW_PENDING_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as PendingPreview
    return parsed.previewPending ? parsed : null
  } catch {
    return null
  }
}

function confirmDelete(): Promise<boolean> {
  return new Promise((resolve) => {
    wx.showModal({
      title: '删除方案',
      content: '删除后该方案不会再出现在列表中，是否继续？',
      confirmText: '删除',
      cancelText: '取消',
      success: (response) => resolve(response.confirm),
      fail: () => resolve(false),
    })
  })
}
