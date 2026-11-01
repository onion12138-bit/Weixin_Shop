import { navigateOnce, resetNavigation } from '../../utils/navigation'
import { getProfile } from '../../services/account'

Page({
  data: {
    nickname: '小丸子',
    avatarUrl: '/assets/images/profile/avatar.webp',
  },

  onShow() {
    resetNavigation(this)
    void getProfile().then((profile) => {
      this.setData({
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl || '/assets/images/profile/avatar.webp',
      })
    }).catch(() => {
      // 保留本地默认资料，网络恢复后下次进入会自动刷新。
    })
  },

  openHome() {
    navigateOnce(this, {
      url: '/pages/home/index',
      fail: () => this.showUnavailable(),
    }, 'redirectTo')
  },

  openRecommendation() {
    this.navigate('/pages/recommendation/index')
  },

  openDiy() {
    this.navigate('/pages/diy/editor')
  },

  openSchemes() {
    this.navigate('/pages/diy/schemes')
  },

  openOrders() {
    this.navigate('/pages/order/list')
  },

  openProfileEditor() {
    this.navigate('/pages/profile/edit')
  },

  openFavorites() {
    this.navigate('/pages/profile/favorites')
  },

  openAddresses() {
    this.navigate('/pages/profile/addresses')
  },

  openCoupons() {
    this.navigate('/pages/profile/coupons')
  },

  scrollToTop() {
    const scrollPage = wx as typeof wx & {
      pageScrollTo(options: { scrollTop: number; duration: number }): void
    }
    scrollPage.pageScrollTo({ scrollTop: 0, duration: 300 })
  },

  navigate(url: string) {
    navigateOnce(this, {
      url,
      fail: () => this.showUnavailable(),
    })
  },

  showUnavailable() {
    wx.showToast({ title: '页面暂时无法打开', icon: 'none' })
  },
})
