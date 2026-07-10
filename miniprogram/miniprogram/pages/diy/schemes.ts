import {
  copyScheme as copyDiyScheme,
  deleteScheme as deleteDiyScheme,
  listSchemes,
} from '../../services/diy'
import {
  toSchemeCard,
  type SchemeCard,
} from '../../utils/diy-view-model'

interface PendingPreview {
  schemeId: number
  previewPending: boolean
}

const PREVIEW_PENDING_KEY = 'diy_preview_pending'

Page({
  data: {
    loading: true,
    errorMessage: '',
    schemes: [] as Array<SchemeCard & { previewPending: boolean }>,
  },

  onShow() {
    void this.loadSchemes()
  },

  async loadSchemes() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const pending = readPendingPreview()
      const schemes = (await listSchemes()).map((scheme) => ({
        ...toSchemeCard(scheme),
        previewPending: pending?.schemeId === scheme.id && pending.previewPending,
      }))
      this.setData({ schemes })
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

  async copyScheme(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    wx.showLoading({ title: '正在复制', mask: true })
    try {
      await copyDiyScheme(id)
      await this.loadSchemes()
      wx.showToast({ title: '已复制', icon: 'success' })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '复制失败',
      })
    } finally {
      wx.hideLoading()
    }
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
