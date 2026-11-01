/** 把本地收藏标识映射为商品卡片，并允许移除已失效或不再需要的收藏。 */
import { navigateOnce, resetNavigation } from '../../utils/navigation'
import { removeFavoriteBead, readFavoriteBeadIds } from '../../domain/favorite-beads'
import { listAllPublicBeads } from '../../services/catalog'
import { ApiError } from '../../services/request'
import { toBeadCard } from '../../utils/catalog-view-model'

Page({
  data: {
    loading: true,
    favoriteCards: [] as ReturnType<typeof toBeadCard>[],
    errorMessage: '',
  },

  onShow() {
    resetNavigation(this)
    void this.loadFavorites()
  },

  async loadFavorites() {
    const ids = new Set(readFavoriteBeadIds())
    if (!ids.size) {
      this.setData({ loading: false, favoriteCards: [], errorMessage: '' })
      return
    }
    this.setData({ loading: true, errorMessage: '' })
    try {
      const favoriteCards = (await listAllPublicBeads())
        .filter((bead) => ids.has(bead.id)).map(toBeadCard)
      this.setData({ favoriteCards })
    } catch (error) {
      this.setData({ errorMessage: error instanceof ApiError ? error.message : '收藏加载失败' })
    } finally { this.setData({ loading: false }) }
  },

  removeFavorite(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    removeFavoriteBead(id)
    this.setData({ favoriteCards: this.data.favoriteCards.filter((item) => item.id !== id) })
    wx.showToast({ title: '已取消收藏', icon: 'none' })
  },

  startDesign(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    navigateOnce(this, { url: `/pages/diy/editor?skuId=${id}` })
  },

  browseBeads() { navigateOnce(this, { url: '/pages/diy/editor' }) },
})
