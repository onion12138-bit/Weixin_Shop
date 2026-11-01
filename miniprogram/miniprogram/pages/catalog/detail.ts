/** 展示商品或套餐详情，并把选中商品带入 DIY 编辑流程。 */
import { navigateOnce, resetNavigation } from '../../utils/navigation'
import {
  getBead,
  getPackage,
  type PublicPackageDetail,
} from '../../services/catalog'
import { ApiError } from '../../services/request'
import {
  toBeadCard,
  toPackageCard,
} from '../../utils/catalog-view-model'

Page({
  data: {
    id: 0,
    type: 'bead',
    loading: true,
    errorMessage: '',
    bead: null as ReturnType<typeof toBeadCard> | null,
    packageCard: null as ReturnType<typeof toPackageCard> | null,
    packageDetail: null as PublicPackageDetail | null,
  },

  onShow() {
    resetNavigation(this)
  },

  onLoad(options: Record<string, string>) {
    const id = Number(options.id)
    const type = options.type === 'package' ? 'package' : 'bead'
    this.setData({ id, type })
    void this.loadDetail()
  },

  async loadDetail() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      if (this.data.type === 'package') {
        const detail = await getPackage(this.data.id)
        this.setData({
          packageDetail: detail,
          packageCard: toPackageCard(detail),
        })
      } else {
        const bead = await getBead(this.data.id)
        this.setData({ bead: toBeadCard(bead) })
      }
    } catch (error) {
      this.setData({
        errorMessage:
          error instanceof ApiError
            ? error.message
            : '详情加载失败，请稍后重试',
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  retry() {
    void this.loadDetail()
  },

  startDesign() {
    const parameter = this.data.type === 'package'
      ? `packageId=${this.data.id}`
      : `skuId=${this.data.id}`
    navigateOnce(this, {
      url: `/pages/diy/editor?${parameter}`,
    })
  },
})
