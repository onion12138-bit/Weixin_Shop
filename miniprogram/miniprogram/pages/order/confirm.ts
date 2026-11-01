import { isNavigationPending, navigateOnce, resetNavigation } from '../../utils/navigation'
import { listAddresses, listCoupons, type CustomerAddress, type CustomerCoupon } from '../../services/account'
import { getScheme, type DiySchemeResponse } from '../../services/diy'
import { createOrder, getOrderQuote, findOrderByCheckoutToken, type CreateOrderPayload, type OrderQuote } from '../../services/orders'
import { createUuidV4 } from '../../services/session'
import { clearCheckoutAddress, clearCheckoutRequest, readCheckoutRequest, saveCheckoutRequest, selectedCheckoutAddress } from '../../utils/checkout-state'
import { onSchemePreviewReady, readySchemePreviewUrl } from '../../services/scheme-preview'
import { ApiError } from '../../services/request'
import { buildCheckoutBeads, buildCheckoutCoupons, formatCents, maskPhone, yuanToCents, type CheckoutCouponItem } from '../../utils/order-checkout-view-model'

Page({
  data: {
    schemeId: 0, loading: true, submitting: false, createdOrderId: 0, errorMessage: '',
    scheme: null as DiySchemeResponse | null,
    previewUrl: '/assets/images/bead-placeholder.svg', productName: '我的专属手串',
    beadCountText: '0 颗', beadRows: [] as ReturnType<typeof buildCheckoutBeads>['rows'], hasMoreBeads: false,
    address: null as CustomerAddress | null, selectedAddressId: 0, maskedPhone: '',
    addressLoading: false, addressLoaded: false, addressError: '',
    coupons: [] as CheckoutCouponItem[], selectedCouponId: 0, selectedCouponName: '暂不使用优惠券',
    couponLoading: false, couponsLoaded: false, couponError: '', couponModalVisible: false,
    quote: null as OrderQuote | null, quoteLoading: false, quoteError: '', quoteNeedsConfirmation: false, designNeedsConfirmation: false,
    quoteChanges: [] as Array<{ label: string; before: string; after: string }>,
    recoveryPending: false,
    productTotalCents: 0, productTotalText: '—', discountCents: 0, discountText: '—',
    shippingFeeCents: 0, shippingFeeText: '—', shippingHint: '以服务端结算报价为准',
    payableCents: 0, payableText: '—', savedText: '',
  },
  pageReady: false, pageDisposed: false,
  addressVersion: 0, couponVersion: 0, quoteVersion: 0, loadVersion: 0,
  rawCoupons: [] as CustomerCoupon[],
  pendingRequest: null as CreateOrderPayload | null,
  displayedQuote: null as OrderQuote | null,
  unsubscribePreview: null as (() => void) | null,

  onLoad(options: Record<string, string>) {
    this.setData({ schemeId: Number(options.schemeId || 0) })
    this.unsubscribePreview = onSchemePreviewReady((schemeId, version, previewUrl) => {
      if (!this.pageDisposed && schemeId === this.data.schemeId && version === this.data.scheme?.version) this.setData({ previewUrl })
    })
    void this.loadCheckout()
  },
  onUnload() {
    this.pageDisposed = true
    this.unsubscribePreview?.()
    this.unsubscribePreview = null
  },
  onShow() {
    resetNavigation(this)
    if (this.data.createdOrderId) this.setData({ submitting: false })
    if (this.pageReady && !this.data.submitting && !this.data.createdOrderId && !this.pendingRequest) void this.refreshAddress()
  },

  async loadCheckout() {
    if (!this.data.schemeId) { this.setData({ loading: false, errorMessage: '方案编号无效' }); return }
    const version = ++this.loadVersion
    this.setData({ loading: true, errorMessage: '' })
    try {
      this.pendingRequest = readCheckoutRequest(this.data.schemeId)
      if (this.pendingRequest) {
        this.setData({ recoveryPending: true, selectedCouponId: this.pendingRequest.couponId || 0 })
        if (await this.recoverOrder()) return
      }
      const scheme = await getScheme(this.data.schemeId)
      if (this.pageDisposed || version !== this.loadVersion) return
      this.setData(schemePresentation(scheme))
      await Promise.all([this.refreshAddress(), this.refreshCoupons(), this.refreshQuote()])
      this.pageReady = true
    } catch (error) {
      if (!this.pageDisposed) this.setData({ errorMessage: readableError(error, '订单信息加载失败') })
    } finally {
      if (!this.pageDisposed && version === this.loadVersion) this.setData({ loading: false })
    }
  },

  async refreshAddress() {
    if (this.pendingRequest) {
      const request = this.pendingRequest
      this.setData({ address: { id: 0, receiverName: request.contactName, phone: request.contactPhone,
        region: '', detail: request.addressDetail, isDefault: false, status: 'ACTIVE', createdAt: '', updatedAt: '' },
        maskedPhone: maskPhone(request.contactPhone), addressLoaded: true, addressError: '' })
      return
    }
    const version = ++this.addressVersion
    this.setData({ addressLoading: true, addressError: '' })
    try {
      const addresses = await listAddresses()
      if (this.pageDisposed || version !== this.addressVersion) return
      const selectedId = selectedCheckoutAddress(this.data.schemeId) || this.data.selectedAddressId
      // 用户明确选择过的地址被删除时要求重新选择，不能悄悄换成默认地址。
      const address = selectedId ? addresses.find(item => item.id === selectedId) || null
        : addresses.find(item => item.isDefault) || addresses[0] || null
      this.setData({ address, maskedPhone: address ? maskPhone(address.phone) : '', addressLoaded: true,
        addressError: selectedId && !address ? '本单地址已被删除，请重新选择' : '' })
    } catch (error) {
      if (!this.pageDisposed && version === this.addressVersion) this.setData({ addressError: readableError(error, '地址加载失败') })
    } finally {
      if (!this.pageDisposed && version === this.addressVersion) this.setData({ addressLoading: false })
    }
  },

  async refreshCoupons() {
    const version = ++this.couponVersion
    this.setData({ couponLoading: true, couponError: '' })
    try {
      const coupons = await listCoupons()
      if (this.pageDisposed || version !== this.couponVersion) return
      this.rawCoupons = coupons
      this.setData({ couponsLoaded: true, selectedCouponName: this.data.selectedCouponId
        ? coupons.find(item => item.id === this.data.selectedCouponId)?.name || '原提交使用的优惠券' : '暂不使用优惠券', coupons: buildCheckoutCoupons(coupons, this.data.productTotalCents, this.data.selectedCouponId) })
    } catch (error) {
      if (!this.pageDisposed && version === this.couponVersion) this.setData({ couponError: readableError(error, '优惠券加载失败') })
    } finally {
      if (!this.pageDisposed && version === this.couponVersion) this.setData({ couponLoading: false })
    }
  },

  async refreshQuote() {
    const version = ++this.quoteVersion
    const previousQuote = this.displayedQuote
    this.setData({ quoteLoading: true, quoteError: '' })
    try {
      let scheme = this.data.scheme
      if (!scheme) throw new Error('方案尚未加载，请重新加载订单信息')
      const displayedVersion = scheme.version
      let quote: OrderQuote | null = null
      // 报价必须与展示的设计属于同一版本；最多三轮，持续编辑时留给用户重试。
      for (let attempt = 0; attempt < 3; attempt += 1) {
        const candidate = await getOrderQuote(this.data.schemeId, this.data.selectedCouponId || undefined)
        if (this.pageDisposed || version !== this.quoteVersion) return
        if (candidate.schemeVersion !== scheme.version) {
          scheme = await getScheme(this.data.schemeId)
          if (this.pageDisposed || version !== this.quoteVersion) return
        }
        if (candidate.schemeVersion === scheme.version) { quote = candidate; break }
      }
      if (!quote) throw new Error('方案持续变化，暂时无法确认报价，请稍后重试')
      const designChanged = displayedVersion !== scheme.version
      const quoteChanges = settlementChanges(previousQuote, quote)
      this.displayedQuote = quote
      const productTotalCents = yuanToCents(quote.beadAmount) + yuanToCents(quote.craftFee) + yuanToCents(quote.cordFee)
      const discountCents = yuanToCents(quote.discountAmount)
      this.setData({ ...schemePresentation(scheme), quote, quoteChanges,
        ...(quoteChanges.length ? { quoteNeedsConfirmation: true } : {}),
        ...(designChanged ? { designNeedsConfirmation: true,
          errorMessage: '方案设计已更新，请核对商品预览、珠子与金额后点击“确认新设计并提交”。' } : {}),
        productTotalCents, productTotalText: formatCents(productTotalCents),
        discountCents, discountText: `-${formatCents(discountCents)}`,
        shippingFeeCents: yuanToCents(quote.shippingFee), shippingFeeText: formatCents(yuanToCents(quote.shippingFee)),
        payableCents: yuanToCents(quote.totalAmount), payableText: formatCents(yuanToCents(quote.totalAmount)),
        savedText: `已优惠 ${formatCents(discountCents)}`,
        coupons: buildCheckoutCoupons(this.rawCoupons, productTotalCents, this.data.selectedCouponId),
      })
    } catch (error) {
      if (!this.pageDisposed && version === this.quoteVersion) this.setData({ quote: null, quoteError: readableError(error, '报价加载失败') })
    } finally {
      if (!this.pageDisposed && version === this.quoteVersion) this.setData({ quoteLoading: false })
    }
  },

  openAddresses() {
    if (this.data.submitting || this.data.createdOrderId || this.data.recoveryPending) return
    navigateOnce(this, { url: `/pages/profile/addresses?selectForScheme=${this.data.schemeId}` })
  },
  openCouponModal() {
    if (this.data.submitting || this.data.createdOrderId || this.data.recoveryPending) return
    this.setData({ couponModalVisible: true })
  },
  closeCouponModal() { this.setData({ couponModalVisible: false }) },
  keepCouponModalOpen() {},
  async selectCoupon(event: WechatMiniprogram.TouchEvent) {
    if (this.data.submitting || this.data.createdOrderId || this.data.recoveryPending) return
    const id = Number(event.currentTarget.dataset.id || 0)
    const coupon = this.data.coupons.find(item => item.id === id)
    if (id && (!coupon || !coupon.usable)) {
      wx.showToast({ title: coupon?.statusText || '该优惠券暂不可用', icon: 'none' }); return
    }
    this.setData({ selectedCouponId: id, selectedCouponName: id ? coupon!.name : '暂不使用优惠券',
      couponModalVisible: false, quote: null })
    await this.refreshQuote()
  },

  async submitOrder() {
    if (this.pageDisposed || this.data.submitting || this.data.loading || isNavigationPending(this)) return
    if (this.data.createdOrderId) { this.openCreatedOrder(); return }
    if (!this.pendingRequest && (!this.data.scheme || this.data.quoteLoading || !this.data.quote || this.data.addressLoading || this.data.addressError)) return
    if (!this.pendingRequest && !this.data.address) {
      wx.showToast({ title: '请先选择收货地址', icon: 'none' }); this.openAddresses(); return
    }
    // 锁必须先于随机数生成，避免两个点击各生成一个提交令牌。
    this.setData({ submitting: true, errorMessage: '' })
    try {
      if (this.pendingRequest) {
        if (await this.recoverOrder()) return
      } else {
        const address = this.data.address!
        const request: CreateOrderPayload = {
          schemeId: this.data.schemeId, contactName: address.receiverName, contactPhone: address.phone,
          addressDetail: `${address.region} ${address.detail}`, couponId: this.data.selectedCouponId || undefined,
          quoteFingerprint: this.data.quote!.quoteFingerprint, checkoutToken: await createUuidV4(),
        }
        if (this.pageDisposed) return
        saveCheckoutRequest(request) // 写入失败不得发请求，否则无法在响应丢失后恢复。
        this.pendingRequest = request
        this.setData({ recoveryPending: true })
      }
      const order = await createOrder(this.pendingRequest!)
      this.acceptOrder(order.id)
    } catch (error) {
      if (this.pageDisposed) return
      if (error instanceof ApiError && error.code === 'QUOTE_EXPIRED') {
        clearCheckoutRequest(this.data.schemeId)
        this.pendingRequest = null
        this.setData({ recoveryPending: false, quoteNeedsConfirmation: true,
          errorMessage: '结算价格或规则已变化，请核对最新金额后点击“确认新报价并提交”。' })
        await this.refreshQuote()
      } else if (error instanceof ApiError && ['COMMON_BAD_REQUEST', 'COMMON_CONFLICT', 'COMMON_NOT_FOUND', 'INVENTORY_INSUFFICIENT', 'DIY_DESIGN_NOT_QUOTABLE'].includes(error.code)) {
        clearCheckoutRequest(this.data.schemeId)
        this.pendingRequest = null
        this.setData({ recoveryPending: false, errorMessage: readableError(error, '创建订单失败') })
      } else if (this.pendingRequest) {
        try {
          if (await this.recoverOrder()) return
          this.setData({ errorMessage: '尚未查询到提交结果，请点击“找回或重试提交”，会复用原请求。' })
        } catch {
          this.setData({ errorMessage: '网络异常，提交结果待确认。请点击“找回或重试提交”，原地址和优惠券已保留。' })
        }
      } else this.setData({ errorMessage: readableError(error, '创建订单失败') })
    } finally {
      if (!this.pageDisposed && !isNavigationPending(this)) this.setData({ submitting: false })
    }
  },

  async recoverOrder(): Promise<boolean> {
    if (!this.pendingRequest) return false
    try {
      const order = await findOrderByCheckoutToken(this.pendingRequest.checkoutToken)
      this.acceptOrder(order.id)
      return true
    } catch (error) {
      if (error instanceof ApiError && error.code === 'COMMON_NOT_FOUND') return false
      throw error
    }
  },
  acceptOrder(id: number) {
    // 离开页面后仍保留记录，下一次进入会找回结果；不跳转其他页面。
    if (this.pageDisposed) return
    this.setData({ createdOrderId: id, recoveryPending: false })
    wx.showToast({ title: '订单已创建', icon: 'success' })
    this.openCreatedOrder()
  },
  openCreatedOrder() {
    if (this.pageDisposed) return
    this.setData({ submitting: true, errorMessage: '' })
    navigateOnce(this, {
      url: `/pages/order/detail?id=${this.data.createdOrderId}`,
      success: () => {
        clearCheckoutRequest(this.data.schemeId)
        clearCheckoutAddress(this.data.schemeId)
      },
      fail: () => {
        if (!this.pageDisposed) this.setData({ submitting: false, errorMessage: '订单已创建，进入详情失败，请点击“查看订单”重试' })
      },
    }, 'redirectTo')
  },
})

function readableError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.traceId ? `${error.message}（${error.traceId}）` : error.message
  return error instanceof Error ? error.message : fallback
}

function schemePresentation(scheme: DiySchemeResponse) {
  const beadSummary = buildCheckoutBeads(scheme)
  return {
    scheme,
    previewUrl: readySchemePreviewUrl(scheme.id, scheme.version) || scheme.previewUrl || '/assets/images/bead-placeholder.svg',
    productName: scheme.name || '我的专属手串', beadCountText: `${scheme.beadCount} 颗`,
    beadRows: beadSummary.rows, hasMoreBeads: beadSummary.hasMore,
  }
}

function settlementChanges(previous: OrderQuote | null, next: OrderQuote) {
  if (!previous) return []
  const amounts = (quote: OrderQuote) => [
    yuanToCents(quote.beadAmount) + yuanToCents(quote.craftFee) + yuanToCents(quote.cordFee),
    yuanToCents(quote.discountAmount), yuanToCents(quote.shippingFee), yuanToCents(quote.totalAmount),
  ]
  const before = amounts(previous)
  const after = amounts(next)
  return ['商品金额', '优惠抵扣', '运费', '应付金额'].flatMap((label, index) =>
    before[index] === after[index] ? [] : [{ label, before: formatCents(before[index]), after: formatCents(after[index]) }])
}
