/** 管理已保存设计及预览补传状态，删除与页面跳转均避免重复触发。 */
import { isNavigationPending, navigateOnce, resetNavigation } from '../../utils/navigation'
import {
  deleteScheme as deleteDiyScheme,
  listSchemes,
} from '../../services/diy'
import { getCommerceRule } from '../../services/commerce'
import { hasPendingSchemePreview, retryPendingSchemePreviews } from '../../services/scheme-preview'
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
    deletingId: 0,
    errorMessage: '',
    minimumOrderAmountCents: 2000,
    minimumOrderAmountText: '¥20.00',
    schemes: [] as Array<SchemeCard & { previewPending: boolean }>,
  },

  pageVisible: false,

  onShow() {
    this.pageVisible = true
    resetNavigation(this)
    void this.loadSchemes()
    void retryPendingSchemePreviews().then(() => {
      if (!this.pageVisible) return
      this.setData({ schemes: this.data.schemes.map((item) => ({
        ...item, previewPending: hasPendingSchemePreview(item.id),
      })) })
    })
  },

  onHide() { this.pageVisible = false },
  onUnload() { this.pageVisible = false },

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
        previewPending: hasPendingSchemePreview(scheme.id)
          || (pending?.schemeId === scheme.id && pending.previewPending) || false,
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
    if (this.data.deletingId) return
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    navigateOnce(this, { url: `/pages/diy/editor?schemeId=${id}` })
  },

  createOrder(event: WechatMiniprogram.TouchEvent) {
    if (this.data.deletingId) return
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
    navigateOnce(this, { url: `/pages/order/confirm?schemeId=${id}` })
  },

  openOrders() {
    if (this.data.deletingId) return
    navigateOnce(this, { url: '/pages/order/list' })
  },

  async deleteScheme(event: WechatMiniprogram.TouchEvent) {
    if (this.data.deletingId || isNavigationPending(this)) return
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id || !this.data.schemes.some((item) => item.id === id)) return
    this.setData({ deletingId: id })
    let loadingShown = false
    try {
      const confirmed = await confirmDelete()
      if (!confirmed) return
      wx.showLoading({ title: '正在删除', mask: true })
      loadingShown = true
      await deleteDiyScheme(id)
      await this.loadSchemes()
      wx.showToast({ title: '已删除', icon: 'success' })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '删除失败',
      })
    } finally {
      if (loadingShown) wx.hideLoading()
      this.setData({ deletingId: 0 })
    }
  },

  createScheme() {
    if (this.data.deletingId) return
    if (this.data.schemes.length >= MAX_SCHEME_COUNT) {
      wx.showToast({ title: '最多保存 3 个 DIY 方案', icon: 'none' })
      return
    }
    navigateOnce(this, { url: '/pages/diy/editor' })
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
