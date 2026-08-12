import { listAddresses, listCoupons, type CustomerAddress } from '../../services/account'
import { getScheme, type DiySchemeResponse } from '../../services/diy'
import { getCommerceRule, type CommerceRule } from '../../services/commerce'
import { createOrder } from '../../services/orders'
import { ApiError } from '../../services/request'
import {
  buildCheckoutBeads,
  buildCheckoutCoupons,
  couponDiscountCents,
  formatCents,
  maskPhone,
  shippingFeeCents,
  yuanToCents,
  type CheckoutCouponItem,
} from '../../utils/order-checkout-view-model'

Page({
  data: {
    schemeId: 0,
    loading: true,
    submitting: false,
    errorMessage: '',
    scheme: null as DiySchemeResponse | null,
    previewUrl: '/assets/images/bead-placeholder.svg',
    productName: '我的专属手串',
    beadCountText: '0 颗',
    beadRows: [] as ReturnType<typeof buildCheckoutBeads>['rows'],
    hasMoreBeads: false,
    address: null as CustomerAddress | null,
    maskedPhone: '',
    coupons: [] as CheckoutCouponItem[],
    selectedCouponId: 0,
    selectedCouponName: '暂不使用优惠券',
    couponModalVisible: false,
    productTotalCents: 0,
    productTotalText: '¥0.00',
    discountCents: 0,
    discountText: '-¥0.00',
    minimumOrderCents: 0,
    minimumOrderText: '¥0.00',
    freeShippingThresholdCents: 0,
    baseShippingFeeCents: 0,
    shippingFeeCents: 0,
    shippingFeeText: '¥0.00',
    shippingHint: '',
    payableCents: 0,
    payableText: '¥0.00',
    savedText: '已优惠 ¥0.00',
  },

  pageReady: false,

  onLoad(options: Record<string, string>) {
    this.setData({ schemeId: Number(options.schemeId || 0) })
    void this.loadCheckout()
  },

  onShow() {
    if (this.pageReady) void this.refreshAddress()
  },

  async loadCheckout() {
    if (!this.data.schemeId) {
      this.setData({ loading: false, errorMessage: '方案编号无效' })
      return
    }
    this.setData({ loading: true, errorMessage: '' })
    try {
      const [scheme, addresses, rawCoupons, commerceRule] = await Promise.all([
        getScheme(this.data.schemeId),
        listAddresses().catch(() => []),
        listCoupons().catch(() => []),
        getCommerceRule(),
      ])
      const address = preferredAddress(addresses)
      const productTotalCents = yuanToCents(scheme.totalAmount)
      const beadSummary = buildCheckoutBeads(scheme)
      const coupons = buildCheckoutCoupons(rawCoupons, productTotalCents, 0)
      const commerce = commerceRuleData(commerceRule, productTotalCents, 0)
      this.setData({
        scheme,
        previewUrl: scheme.previewUrl || '/assets/images/bead-placeholder.svg',
        productName: scheme.name || '我的专属手串',
        beadCountText: `${scheme.beadCount} 颗`,
        beadRows: beadSummary.rows,
        hasMoreBeads: beadSummary.hasMore,
        address,
        maskedPhone: address ? maskPhone(address.phone) : '',
        coupons,
        productTotalCents,
        productTotalText: formatCents(productTotalCents),
        ...commerce,
        payableCents: productTotalCents + commerce.shippingFeeCents,
        payableText: formatCents(productTotalCents + commerce.shippingFeeCents),
      })
      this.pageReady = true
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '订单信息加载失败') })
    } finally {
      this.setData({ loading: false })
    }
  },

  async refreshAddress() {
    try {
      const address = preferredAddress(await listAddresses())
      this.setData({
        address,
        maskedPhone: address ? maskPhone(address.phone) : '',
      })
    } catch {
      // 保留当前地址，避免返回页面时短暂的网络错误清空已展示内容。
    }
  },

  openAddresses() {
    wx.navigateTo({ url: '/pages/profile/addresses' })
  },

  openCouponModal() {
    this.setData({ couponModalVisible: true })
  },

  closeCouponModal() {
    this.setData({ couponModalVisible: false })
  },

  keepCouponModalOpen() {},

  selectCoupon(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    const coupon = this.data.coupons.find((item) => item.id === id)
    if (id && (!coupon || !coupon.usable)) {
      wx.showToast({ title: coupon?.statusText || '该优惠券暂不可用', icon: 'none' })
      return
    }
    const coupons = this.data.coupons.map((item) => ({
      ...item,
      selected: item.id === id && item.usable,
    }))
    const discountCents = couponDiscountCents(
      coupons,
      id,
      this.data.productTotalCents,
    )
    const payableCents = Math.max(0, this.data.productTotalCents - discountCents)
    const shippingFee = shippingFeeCents(
      this.data.productTotalCents,
      discountCents,
      this.data.freeShippingThresholdCents,
      this.data.baseShippingFeeCents,
    )
    this.setData({
      coupons,
      selectedCouponId: id,
      selectedCouponName: id ? (coupon?.name || '已选择优惠券') : '暂不使用优惠券',
      discountCents,
      discountText: `-${formatCents(discountCents)}`,
      shippingFeeCents: shippingFee,
      shippingFeeText: formatCents(shippingFee),
      shippingHint: shippingFee
        ? `优惠后商品满 ${formatCents(this.data.freeShippingThresholdCents)} 包邮`
        : '已享包邮',
      payableCents: payableCents + shippingFee,
      payableText: formatCents(payableCents + shippingFee),
      savedText: `已优惠 ${formatCents(discountCents)}`,
      couponModalVisible: false,
    })
  },

  async submitOrder() {
    if (this.data.productTotalCents < this.data.minimumOrderCents) {
      wx.showToast({
        title: `订单商品金额需满 ${this.data.minimumOrderText} 才能下单`,
        icon: 'none',
      })
      return
    }
    const address = this.data.address
    if (!address) {
      wx.showToast({ title: '请先添加收货地址', icon: 'none' })
      this.openAddresses()
      return
    }

    this.setData({ submitting: true, errorMessage: '' })
    try {
      const order = await createOrder({
        schemeId: this.data.schemeId,
        contactName: address.receiverName,
        contactPhone: address.phone,
        addressDetail: `${address.region} ${address.detail}`,
        couponId: this.data.selectedCouponId || undefined,
      })
      wx.showToast({ title: '订单已创建', icon: 'success' })
      wx.redirectTo({ url: `/pages/order/detail?id=${order.id}` })
    } catch (error) {
      this.setData({ errorMessage: readableError(error, '创建订单失败') })
    } finally {
      this.setData({ submitting: false })
    }
  },
})

function preferredAddress(addresses: CustomerAddress[]): CustomerAddress | null {
  return addresses.find((item) => item.isDefault) || addresses[0] || null
}

function readableError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.traceId ? `${error.message}（${error.traceId}）` : error.message
  }
  return error instanceof Error ? error.message : fallback
}

function commerceRuleData(
  rule: CommerceRule,
  productTotalCents: number,
  discountCents: number,
) {
  const minimumOrderCents = yuanToCents(rule.minimumOrderAmount)
  const freeShippingThresholdCents = yuanToCents(rule.freeShippingThreshold)
  const baseShippingFeeCents = yuanToCents(rule.baseShippingFee)
  const shippingFee = shippingFeeCents(
    productTotalCents,
    discountCents,
    freeShippingThresholdCents,
    baseShippingFeeCents,
  )
  return {
    minimumOrderCents,
    minimumOrderText: formatCents(minimumOrderCents),
    freeShippingThresholdCents,
    baseShippingFeeCents,
    shippingFeeCents: shippingFee,
    shippingFeeText: formatCents(shippingFee),
    shippingHint: shippingFee
      ? `优惠后商品满 ${formatCents(freeShippingThresholdCents)} 包邮`
      : '已享包邮',
  }
}
