declare function require(name: string): unknown
export {}

jest.mock('../miniprogram/services/account', () => ({ listAddresses: jest.fn(), listCoupons: jest.fn(), setDefaultAddress: jest.fn() }))
jest.mock('../miniprogram/services/diy', () => ({ getScheme: jest.fn() }))
jest.mock('../miniprogram/services/orders', () => ({ createOrder: jest.fn(), getOrderQuote: jest.fn(), findOrderByCheckoutToken: jest.fn(), getOrder: jest.fn(), mockPayOrder: jest.fn() }))
jest.mock('../miniprogram/services/session', () => ({ INSTALLATION_ID_KEY: 'diy_installation_id', createUuidV4: jest.fn().mockResolvedValue('550e8400-e29b-41d4-a716-446655440000') }))

const address = { id: 1, receiverName: '张三', phone: '13812345678', region: '上海', detail: '一号', isDefault: true }
const addressB = { ...address, id: 2, detail: '二号', isDefault: false }
const quote = { schemeVersion: 1, quoteFingerprint: 'quote-1', beadAmount: 30, craftFee: 5, cordFee: 1, discountAmount: 0, shippingFee: 8, totalAmount: 44 }
const coupon = { id: 1, name: '立减券', discountAmount: 5, minimumSpend: 0, status: 'UNUSED', expiresAt: '2030-01-01T00:00:00Z' }
const scheme = { id: 1, version: 1, totalAmount: 100, beadCount: 1, catalogSnapshot: [], sequence: [], skuQuantities: {} }
const tick = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
function deferred() { let resolve!: (x: any) => void; const promise = new Promise<any>(r => { resolve = r }); return { promise, resolve } }
function loadPage(route = 'order/confirm', storage = new Map<string, string>()) {
  let definition: any; let account: any; let orders: any; let ApiError: any; let diy: any
  const wxMock = { getStorageSync: jest.fn((key: string) => storage.get(key) || ''), setStorageSync: jest.fn((k: string, v: string) => storage.set(k, v)), removeStorageSync: jest.fn((k: string) => storage.delete(k)), navigateTo: jest.fn(), navigateBack: jest.fn(), redirectTo: jest.fn(), showToast: jest.fn(), showModal: jest.fn() }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (value: any) => { definition = value }
  jest.isolateModules(() => {
    account = jest.requireMock('../miniprogram/services/account')
    orders = jest.requireMock('../miniprogram/services/orders')
    ApiError = (jest.requireActual('../miniprogram/services/request') as any).ApiError
    account.listAddresses.mockResolvedValue([address, addressB]); account.listCoupons.mockResolvedValue([coupon])
    diy = jest.requireMock('../miniprogram/services/diy')
    diy.getScheme.mockResolvedValue(scheme)
    orders.getOrderQuote.mockResolvedValue(quote)
    orders.findOrderByCheckoutToken.mockRejectedValue(new ApiError('不存在', 'COMMON_NOT_FOUND'))
    require(`../miniprogram/pages/${route}`)
  })
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) }
  page.setData = (patch: any) => Object.assign(page.data, patch)
  page.setData({ schemeId: 1 })
  return { page, account, orders, wxMock, ApiError, storage, diy }
}

describe('reliable checkout', () => {
  beforeEach(() => jest.clearAllMocks())
  it('uses authoritative quote amounts instead of stored scheme prices', async () => {
    const { page } = loadPage(); await page.loadCheckout()
    expect(page.data.productTotalText).toBe('¥36.00'); expect(page.data.payableText).toBe('¥44.00')
  })
  it('keeps address and coupon failures distinct from empty successful results and retries independently', async () => {
    const { page, account } = loadPage()
    account.listAddresses.mockRejectedValueOnce(new Error('地址断网')); account.listCoupons.mockRejectedValueOnce(new Error('券断网'))
    await page.loadCheckout()
    expect(page.data.addressError).toContain('地址断网'); expect(page.data.couponError).toContain('券断网')
    expect(page.data.addressLoaded).toBe(false); expect(page.data.couponsLoaded).toBe(false)
    await page.refreshAddress(); expect(page.data.address?.id).toBe(1); expect(page.data.couponError).toContain('券断网')
    await page.refreshCoupons(); expect(page.data.couponError).toBe(''); expect(page.data.couponsLoaded).toBe(true)
  })
  it('keeps the nondefault address selected for this checkout across refresh and ignores an older response', async () => {
    const { page, account } = loadPage(); await page.loadCheckout()
    page.setData({ selectedAddressId: 2, address: addressB })
    const stale = deferred(); account.listAddresses.mockReturnValueOnce(stale.promise)
    const old = page.refreshAddress(); await page.refreshAddress()
    stale.resolve([address]); await old
    expect(page.data.address.id).toBe(2); expect(account.setDefaultAddress).not.toHaveBeenCalled()
  })
  it('allows selecting B without changing default A', async () => {
    const { page, account, wxMock } = loadPage('profile/addresses')
    page.onLoad({ selectForScheme: '1' }); await tick()
    page.chooseForCheckout({ currentTarget: { dataset: { id: 2 } } })
    expect(account.setDefaultAddress).not.toHaveBeenCalled(); expect(wxMock.navigateBack).toHaveBeenCalled()
  })
  it('reads a checkout-only address choice when returning without changing the default', async () => {
    const storage = new Map<string, string>()
    const picker = loadPage('profile/addresses', storage)
    picker.page.onLoad({ selectForScheme: '1' }); await tick()
    picker.page.chooseForCheckout({ currentTarget: { dataset: { id: 2 } } })
    const checkout = loadPage('order/confirm', storage); await checkout.page.loadCheckout()
    expect(checkout.page.data.address.id).toBe(2)
    expect(checkout.account.setDefaultAddress).not.toHaveBeenCalled()
  })
  it('ignores stale quote responses when the coupon changes quickly', async () => {
    const { page, orders } = loadPage(); await page.loadCheckout()
    const old = deferred(); orders.getOrderQuote.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...quote, quoteFingerprint: 'new' })
    const first = page.selectCoupon({ currentTarget: { dataset: { id: 1 } } })
    await page.selectCoupon({ currentTarget: { dataset: { id: 0 } } })
    old.resolve({ ...quote, quoteFingerprint: 'old', totalAmount: 39 }); await first
    expect(page.data.quote.quoteFingerprint).toBe('new'); expect(page.data.payableText).toBe('¥44.00')
  })
  it('blocks submission if the server quote rejects the minimum amount', async () => {
    const { page, orders, ApiError } = loadPage()
    orders.getOrderQuote.mockRejectedValue(new ApiError('订单商品金额需满 ¥20.00', 'COMMON_BAD_REQUEST'))
    await page.loadCheckout(); await page.submitOrder()
    expect(page.data.quoteError).toContain('¥20.00'); expect(orders.createOrder).not.toHaveBeenCalled()
  })
  it('does not send when the pending request cannot be saved locally', async () => {
    const { page, orders, wxMock } = loadPage(); await page.loadCheckout()
    wxMock.setStorageSync.mockImplementation(() => { throw new Error('存储空间不足') })
    await page.submitOrder()
    expect(orders.createOrder).not.toHaveBeenCalled(); expect(page.data.errorMessage).toContain('存储空间不足')
  })
  it('resends exactly the original request after restart when lookup confirms no order', async () => {
    const first = loadPage(); await first.page.loadCheckout(); first.page.setData({ address: addressB })
    first.orders.createOrder.mockRejectedValue(new first.ApiError('超时', 'NETWORK_ERROR'))
    await first.page.submitOrder(); const original = first.orders.createOrder.mock.calls[0][0]
    const next = loadPage('order/confirm', first.storage)
    await next.page.loadCheckout()
    expect(next.page.data.address.detail).toBe('上海 二号')
    next.orders.createOrder.mockResolvedValue({ id: 8 }); await next.page.submitOrder()
    expect(next.orders.createOrder).toHaveBeenCalledTimes(1)
    expect(next.orders.createOrder).toHaveBeenCalledWith(original)
  })
  it('locks before asynchronous token generation and persists the complete request before sending', async () => {
    const { page, orders, storage } = loadPage(); await page.loadCheckout()
    const pending = deferred(); orders.createOrder.mockReturnValue(pending.promise)
    const first = page.submitOrder(); void page.submitOrder(); await tick()
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    const payload = orders.createOrder.mock.calls[0][0]
    expect(payload).toMatchObject({ quoteFingerprint: 'quote-1', checkoutToken: expect.any(String), addressDetail: '上海 一号' })
    expect([...storage.values()].some(v => v.includes(payload.checkoutToken))).toBe(true)
    pending.resolve({ id: 9 }); await first
  })
  it('recovers a committed order when the create response is lost', async () => {
    const { page, orders, ApiError, wxMock } = loadPage(); await page.loadCheckout()
    orders.createOrder.mockRejectedValue(new ApiError('超时', 'NETWORK_ERROR')); orders.findOrderByCheckoutToken.mockResolvedValue({ id: 9 })
    await page.submitOrder()
    expect(page.data.createdOrderId).toBe(9); expect(orders.createOrder).toHaveBeenCalledTimes(1)
    expect(wxMock.redirectTo).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/order/detail?id=9' }))
  })
  it('re-enters checkout with the same persisted request after an ambiguous failure', async () => {
    const first = loadPage(); await first.page.loadCheckout()
    first.orders.createOrder.mockRejectedValue(new first.ApiError('超时', 'NETWORK_ERROR'))
    first.orders.findOrderByCheckoutToken.mockRejectedValue(new first.ApiError('断网', 'NETWORK_ERROR'))
    await first.page.submitOrder(); const payload = first.orders.createOrder.mock.calls[0][0]
    const next = loadPage('order/confirm', first.storage); next.orders.findOrderByCheckoutToken.mockResolvedValue({ id: 7 })
    await next.page.loadCheckout()
    expect(next.orders.findOrderByCheckoutToken).toHaveBeenLastCalledWith(payload.checkoutToken)
    expect(next.page.data.createdOrderId).toBe(7); expect(next.orders.createOrder).not.toHaveBeenCalled()
  })
  it('never replaces an uncertain request while order recovery is offline', async () => {
    const { page, orders, ApiError } = loadPage(); await page.loadCheckout()
    orders.createOrder.mockRejectedValue(new ApiError('超时', 'NETWORK_ERROR'))
    orders.findOrderByCheckoutToken.mockRejectedValue(new ApiError('断网', 'NETWORK_ERROR'))
    await page.submitOrder(); await page.submitOrder()
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    expect(page.data.recoveryPending).toBe(true)
    page.openAddresses(); expect(page.data.selectedCouponId).toBe(0)
  })
  it('refreshes the displayed design and requires confirmation when the scheme changes during checkout loading', async () => {
    const { page, orders, diy } = loadPage()
    diy.getScheme.mockResolvedValueOnce(scheme).mockResolvedValueOnce({ ...scheme, version: 2, name: '新版设计', beadCount: 2, previewUrl: '/version-2.png' })
    orders.getOrderQuote.mockResolvedValue({ ...quote, schemeVersion: 2, quoteFingerprint: 'quote-2' })
    await page.loadCheckout()
    expect(page.data.scheme.version).toBe(2)
    expect(page.data.productName).toBe('新版设计'); expect(page.data.previewUrl).toBe('/version-2.png')
    expect(page.data.designNeedsConfirmation).toBe(true)
    expect(orders.createOrder).not.toHaveBeenCalled()
    orders.createOrder.mockResolvedValue({ id: 9 }); await page.submitOrder()
    expect(orders.createOrder).toHaveBeenCalledWith(expect.objectContaining({ quoteFingerprint: 'quote-2' }))
  })
  it('refreshes the design as well as prices when submit reports an expired quote', async () => {
    const { page, orders, ApiError, diy } = loadPage(); await page.loadCheckout()
    orders.createOrder.mockRejectedValueOnce(new ApiError('方案已变化', 'QUOTE_EXPIRED'))
    diy.getScheme.mockResolvedValue({ ...scheme, version: 2, name: '替换后的设计', beadCount: 3, previewUrl: '/new-design.png' })
    orders.getOrderQuote.mockResolvedValue({ ...quote, schemeVersion: 2, quoteFingerprint: 'quote-2', totalAmount: 55 })
    await page.submitOrder()
    expect(page.data.scheme.version).toBe(2); expect(page.data.productName).toBe('替换后的设计')
    expect(page.data.previewUrl).toBe('/new-design.png'); expect(page.data.payableText).toBe('¥55.00')
    expect(page.data.designNeedsConfirmation).toBe(true); expect(orders.createOrder).toHaveBeenCalledTimes(1)
  })
  it('ignores a design reload from an older quote when a newer coupon selection has completed', async () => {
    const { page, orders, diy } = loadPage(); await page.loadCheckout()
    const staleScheme = deferred()
    orders.getOrderQuote.mockResolvedValueOnce({ ...quote, schemeVersion: 2, quoteFingerprint: 'older' })
      .mockResolvedValueOnce({ ...quote, quoteFingerprint: 'latest' })
    diy.getScheme.mockReturnValueOnce(staleScheme.promise)
    const old = page.refreshQuote(); await tick()
    await page.selectCoupon({ currentTarget: { dataset: { id: 0 } } })
    staleScheme.resolve({ ...scheme, version: 2, name: '旧请求加载的设计' }); await old
    expect(page.data.quote.quoteFingerprint).toBe('latest'); expect(page.data.scheme.version).toBe(1)
    expect(page.data.designNeedsConfirmation).toBe(false)
  })
  it('fails within a bounded number of attempts when the scheme keeps changing between quote and reload', async () => {
    const { page, orders } = loadPage()
    orders.getOrderQuote.mockResolvedValue({ ...quote, schemeVersion: 2 })
    await page.loadCheckout(); await page.submitOrder()
    expect(orders.getOrderQuote.mock.calls.length).toBeLessThanOrEqual(3)
    expect(page.data.quote).toBeNull(); expect(page.data.quoteError).toContain('方案持续变化')
    expect(orders.createOrder).not.toHaveBeenCalled()
  })
  it('shows exact old-to-new amounts only for changed settlement components', async () => {
    const { page, orders, ApiError } = loadPage(); await page.loadCheckout()
    expect(page.data.quoteChanges).toEqual([])
    orders.createOrder.mockRejectedValueOnce(new ApiError('报价变化', 'QUOTE_EXPIRED'))
    orders.getOrderQuote.mockResolvedValueOnce({ ...quote, quoteFingerprint: 'quote-2', beadAmount: 31.25, discountAmount: 5.50, shippingFee: 0, totalAmount: 31.75 })
    await page.submitOrder()
    expect(page.data.quoteChanges).toEqual([
      { label: '商品金额', before: '¥36.00', after: '¥37.25' },
      { label: '优惠抵扣', before: '¥0.00', after: '¥5.50' },
      { label: '运费', before: '¥8.00', after: '¥0.00' },
      { label: '应付金额', before: '¥44.00', after: '¥31.75' },
    ])
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
  })
  it('does not call a design-only change an amount change and compares rounded integer cents', async () => {
    const { page, orders, diy } = loadPage(); await page.loadCheckout()
    diy.getScheme.mockResolvedValue({ ...scheme, version: 2, name: '新设计' })
    orders.getOrderQuote.mockResolvedValueOnce({ ...quote, schemeVersion: 2, beadAmount: 30.00000000001, totalAmount: 44.00000000001 })
    await page.refreshQuote()
    expect(page.data.designNeedsConfirmation).toBe(true)
    expect(page.data.quoteChanges).toEqual([])
  })
  it('requires an explicit next submit after quote expiry and never silently creates at the new amount', async () => {
    const { page, orders, ApiError } = loadPage(); await page.loadCheckout()
    orders.createOrder.mockRejectedValueOnce(new ApiError('报价变化', 'QUOTE_EXPIRED'))
    orders.getOrderQuote.mockResolvedValueOnce({ ...quote, quoteFingerprint: 'quote-2', totalAmount: 50 })
    await page.submitOrder()
    expect(page.data.quoteNeedsConfirmation).toBe(true); expect(page.data.payableText).toBe('¥50.00')
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    orders.createOrder.mockResolvedValueOnce({ id: 9 }); await page.submitOrder()
    expect(orders.createOrder.mock.calls[1][0].quoteFingerprint).toBe('quote-2')
  })
})

describe('payment deadline', () => {
  beforeEach(() => { jest.useFakeTimers(); jest.setSystemTime(new Date('2026-01-01T00:00:00Z')); jest.clearAllMocks() })
  afterEach(() => jest.useRealTimers())
  const order = { id: 1, status: 'PENDING_PAYMENT', items: [], createdAt: '2026-10-23T01:00:00Z', serverTime: '2026-10-23T01:00:00Z', expiresAt: '2026-10-23T01:00:02Z', totalAmount: 44 }
  it('uses server time despite local clock skew and stops the timer when leaving', async () => {
    const { page, orders } = loadPage('order/detail'); orders.getOrder.mockResolvedValue(order)
    page.setData({ id: 1 }); await page.loadOrder()
    expect(page.data.countdownText).toBe('00:02')
    jest.advanceTimersByTime(1000); expect(page.data.countdownText).toBe('00:01')
    page.onUnload(); expect(jest.getTimerCount()).toBe(0)
  })
  it('disables payment at the deadline and refreshes closure once', async () => {
    const { page, orders } = loadPage('order/detail'); orders.getOrder.mockResolvedValue(order)
    page.setData({ id: 1 }); await page.loadOrder()
    orders.getOrder.mockResolvedValue({ ...order, status: 'CANCELLED', closedReason: 'PAYMENT_TIMEOUT' })
    jest.advanceTimersByTime(2000); await tick()
    expect(page.data.order.canMockPay).toBe(false)
    expect(page.data.closedReasonText).toContain('超时')
    expect(orders.getOrder).toHaveBeenCalledTimes(2)
    page.onUnload(); expect(jest.getTimerCount()).toBe(0)
  })
  it('does not show payment success when the backend closes an expired order', async () => {
    const { page, orders, wxMock } = loadPage('order/detail'); orders.getOrder.mockResolvedValue(order)
    page.setData({ id: 1 }); await page.loadOrder()
    orders.mockPayOrder.mockResolvedValue({ ...order, status: 'CANCELLED', closedReason: 'PAYMENT_TIMEOUT' })
    wxMock.showModal.mockImplementation((options: any) => options.success({ confirm: true }))
    await page.mockPay()
    expect(wxMock.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '订单已超时关闭', icon: 'none' }))
    page.onUnload()
  })
})
