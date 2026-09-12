import { listCoupons, type CustomerCoupon } from '../../services/account'
import { ApiError } from '../../services/request'

interface CouponCard extends CustomerCoupon {
  amountText: string
  thresholdText: string
  statusLabel: string
  expiresText: string
}

const STATUS_LABELS: Record<string, string> = {
  UNUSED: '可使用', USED: '已使用', EXPIRED: '已过期', REVOKED: '已失效',
}

Page({
  data: {
    loading: true,
    activeStatus: 'UNUSED',
    allCoupons: [] as CouponCard[],
    couponCards: [] as CouponCard[],
    errorMessage: '',
  },

  onLoad() { void this.loadCoupons() },
  onPullDownRefresh() { void this.loadCoupons().finally(() => wx.stopPullDownRefresh()) },

  async loadCoupons() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const allCoupons = (await listCoupons()).map((coupon) => ({
        ...coupon,
        amountText: `¥${Number(coupon.discountAmount).toFixed(2)}`,
        thresholdText: Number(coupon.minimumSpend) > 0
          ? `满 ¥${Number(coupon.minimumSpend).toFixed(2)} 可用` : '无门槛',
        statusLabel: STATUS_LABELS[coupon.status] || coupon.status,
        expiresText: `有效期至 ${formatDate(coupon.expiresAt)}`,
      }))
      this.setData({ allCoupons })
      this.applyFilter()
    } catch (error) {
      this.setData({ errorMessage: error instanceof ApiError ? error.message : '优惠权益加载失败' })
    } finally { this.setData({ loading: false }) }
  },

  chooseStatus(event: WechatMiniprogram.TouchEvent) {
    this.setData({ activeStatus: String(event.currentTarget.dataset.status || '') })
    this.applyFilter()
  },

  applyFilter() {
    const status = this.data.activeStatus
    this.setData({
      couponCards: status === 'ALL' ? this.data.allCoupons
        : this.data.allCoupons.filter((item) => item.status === status),
    })
  },
})

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}
