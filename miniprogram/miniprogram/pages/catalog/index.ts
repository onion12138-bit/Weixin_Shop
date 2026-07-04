import {
  listBeads,
  listCategories,
  listPackages,
} from '../../services/catalog'
import { ApiError } from '../../services/request'
import {
  toBeadCard,
  toPackageCard,
} from '../../utils/catalog-view-model'

Page({
  data: {
    activeTab: 'beads',
    categories: [] as Array<{ id: number; name: string }>,
    activeCategoryId: 0,
    keyword: '',
    beadCards: [] as ReturnType<typeof toBeadCard>[],
    packageCards: [] as ReturnType<typeof toPackageCard>[],
    page: 1,
    size: 10,
    total: 0,
    hasMore: false,
    loading: false,
    errorMessage: '',
  },

  async onLoad() {
    await this.loadInitialData()
  },

  async onPullDownRefresh() {
    await this.loadProducts(true)
    wx.stopPullDownRefresh()
  },

  async loadInitialData() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const categories = await listCategories()
      this.setData({ categories })
      await this.loadProducts(true)
    } catch (error) {
      this.showError(error)
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadProducts(reset = false) {
    if (this.data.loading && !reset) return
    const nextPage = reset ? 1 : this.data.page
    this.setData({ loading: true, errorMessage: '' })
    try {
      if (this.data.activeTab === 'packages') {
        const result = await listPackages({
          page: nextPage,
          size: this.data.size,
        })
        const cards = result.items.map(toPackageCard)
        this.setData({
          packageCards: reset
            ? cards
            : this.data.packageCards.concat(cards),
          page: result.page,
          total: result.total,
          hasMore: result.page * result.size < result.total,
        })
      } else {
        const result = await listBeads({
          categoryId: this.data.activeCategoryId || undefined,
          keyword: this.data.keyword.trim() || undefined,
          page: nextPage,
          size: this.data.size,
        })
        const cards = result.items.map(toBeadCard)
        this.setData({
          beadCards: reset
            ? cards
            : this.data.beadCards.concat(cards),
          page: result.page,
          total: result.total,
          hasMore: result.page * result.size < result.total,
        })
      }
    } catch (error) {
      this.showError(error)
    } finally {
      this.setData({ loading: false })
    }
  },

  switchTab(event: WechatMiniprogram.TouchEvent) {
    const tab = String(event.currentTarget.dataset.tab || 'beads')
    if (tab === this.data.activeTab) return
    this.setData({
      activeTab: tab,
      page: 1,
      total: 0,
      errorMessage: '',
    })
    void this.loadProducts(true)
  },

  chooseCategory(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    this.setData({ activeCategoryId: id, page: 1 })
    void this.loadProducts(true)
  },

  onKeywordInput(event: WechatMiniprogram.Input) {
    this.setData({ keyword: event.detail.value })
  },

  search() {
    this.setData({ page: 1 })
    void this.loadProducts(true)
  },

  loadMore() {
    if (this.data.loading || !this.data.hasMore) return
    this.setData({ page: this.data.page + 1 })
    void this.loadProducts(false)
  },

  retry() {
    void this.loadInitialData()
  },

  openDetail(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    const type = event.currentTarget.dataset.type
    wx.navigateTo({
      url: `/pages/catalog/detail?id=${id}&type=${type}`,
    })
  },

  showError(error: unknown) {
    const message =
      error instanceof ApiError
        ? error.message
        : '商品加载失败，请稍后重试'
    this.setData({ errorMessage: message })
  },
})
