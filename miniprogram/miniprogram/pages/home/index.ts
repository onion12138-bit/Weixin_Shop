/** 组织首页推荐与方案入口，页面恢复显示时刷新数据并释放导航锁。 */
import { navigateOnce, resetNavigation } from '../../utils/navigation'
import { listRecommendedPackages } from '../../services/catalog'
import { listSchemes } from '../../services/diy'
import {
  toPackageCard,
  type PackageCard,
} from '../../utils/catalog-view-model'
import {
  toHomeSchemeCard,
  type HomeSchemeCard,
} from '../../utils/home-scheme-view-model'

interface WishCard {
  id: string
  name: string
  imageUrl: string
}

const wishGroups: WishCard[][] = [
  [
    { id: 'wealth', name: '财运', imageUrl: '/assets/images/wishes/wealth.png' },
    { id: 'romance', name: '姻缘', imageUrl: '/assets/images/wishes/romance.png' },
    { id: 'career', name: '事业', imageUrl: '/assets/images/wishes/career.png' },
    { id: 'study', name: '学业', imageUrl: '/assets/images/wishes/study.png' },
  ],
  [
    { id: 'peace', name: '平安', imageUrl: '/assets/images/wishes/peace.png' },
    { id: 'vitality', name: '活力', imageUrl: '/assets/images/wishes/vitality.png' },
    { id: 'companionship', name: '相守', imageUrl: '/assets/images/wishes/companionship.png' },
    { id: 'family', name: '家庭', imageUrl: '/assets/images/wishes/family.png' },
  ],
]

const recommendationRoute = {
  url: '/pages/recommendation/index',
}

Page({
  data: {
    wishes: wishGroups[0],
    wishGroupIndex: 0,
    products: [] as PackageCard[],
    productsLoading: false,
    productsError: '',
    scheme: null as HomeSchemeCard | null,
    schemeLoading: true,
    schemeError: '',
  },

  async onLoad() {
    await this.loadFeaturedProducts()
  },

  async onShow() {
    resetNavigation(this)
    await this.loadLatestScheme()
  },

  async loadFeaturedProducts() {
    this.setData({ productsLoading: true, productsError: '' })
    try {
      const products = await listRecommendedPackages(4)
      this.setData({ products: products.map(toPackageCard) })
    } catch (_error) {
      this.setData({
        products: [],
        productsError: '精选成品暂时无法加载',
      })
    } finally {
      this.setData({ productsLoading: false })
    }
  },

  async loadLatestScheme() {
    this.setData({ schemeLoading: true, schemeError: '' })
    try {
      const schemes = await listSchemes()
      const latest = [...schemes].sort(
        (left, right) => right.updatedAt.localeCompare(left.updatedAt),
      )[0]
      this.setData({
        scheme: latest ? toHomeSchemeCard(latest) : null,
      })
    } catch (_error) {
      this.setData({
        scheme: null,
        schemeError: '方案暂时无法加载',
      })
    } finally {
      this.setData({ schemeLoading: false })
    }
  },

  navigate(url: string) {
    navigateOnce(this, {
      url,
      fail: () => {
        wx.showToast({
          title: '页面暂时无法打开',
          icon: 'none',
        })
      },
    })
  },

  openDiy() {
    this.navigate('/pages/diy/editor')
  },

  openRecommendation() {
    this.navigate(recommendationRoute.url)
  },

  openWishInspiration(event: WechatMiniprogram.TouchEvent) {
    const wish = String(event.currentTarget.dataset.wish || '')
    if (!wish) return
    this.navigate(
      `${recommendationRoute.url}?wishInspiration=${encodeURIComponent(wish)}`,
    )
  },

  openCatalog() {
    this.navigate('/pages/catalog/index?tab=packages')
  },

  openProductDetail(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id)
    if (!Number.isFinite(id) || id <= 0) return
    this.navigate(`/pages/catalog/detail?id=${id}&type=package`)
  },

  openSchemes() {
    this.navigate('/pages/diy/schemes')
  },

  openLatestScheme(event?: WechatMiniprogram.TouchEvent) {
    const eventId = Number(event?.currentTarget.dataset.id || 0)
    const id = eventId || this.data.scheme?.id || 0
    if (!id) {
      this.openDiy()
      return
    }
    this.navigate(`/pages/diy/editor?schemeId=${id}`)
  },

  openProfile() {
    this.navigate('/pages/profile/index')
  },

  rotateMoods() {
    const nextGroupIndex = (this.data.wishGroupIndex + 1) % wishGroups.length
    this.setData({
      wishGroupIndex: nextGroupIndex,
      wishes: wishGroups[nextGroupIndex],
    })
  },

  backToTop() {
    const scrollPage = wx as typeof wx & {
      pageScrollTo(options: { scrollTop: number; duration: number }): void
    }
    scrollPage.pageScrollTo({
      scrollTop: 0,
      duration: 300,
    })
  },

  onShareAppMessage() {
    return {
      title: '一颗珠子，一种心情｜来设计你的能量手串',
      path: '/pages/home/index',
      imageUrl: '/assets/images/home/hero-bracelet-lotus.webp',
    }
  },
})
