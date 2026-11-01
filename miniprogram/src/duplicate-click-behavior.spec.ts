declare function require(name: string): unknown
export {}

jest.mock('../miniprogram/services/account', () => ({
  getProfile: jest.fn(), listAddresses: jest.fn(), listCoupons: jest.fn(),
}))
jest.mock('../miniprogram/services/catalog', () => ({
  listBeads: jest.fn(), listPackages: jest.fn(), listCategories: jest.fn(),
  listAllPublicBeads: jest.fn(), listRecommendedPackages: jest.fn(),
  getBead: jest.fn(), getPackage: jest.fn(),
}))
jest.mock('../miniprogram/services/diy', () => ({
  listSchemes: jest.fn(), deleteScheme: jest.fn(), createScheme: jest.fn(),
  updateScheme: jest.fn(), uploadSchemePreview: jest.fn(), getScheme: jest.fn(),
  yuanToCents: (value: number) => Math.round(value * 100),
}))
jest.mock('../miniprogram/services/commerce', () => ({ getCommerceRule: jest.fn() }))
jest.mock('../miniprogram/services/orders', () => ({ createOrder: jest.fn(), listOrders: jest.fn(), findOrderByCheckoutToken: jest.fn() }))

function deferred() {
  let resolve!: (value?: any) => void
  let reject!: (error: Error) => void
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function loadPage(route: string) {
  let definition: any
  const wxMock = {
    navigateTo: jest.fn(), redirectTo: jest.fn(), showToast: jest.fn(),
    showModal: jest.fn(), showLoading: jest.fn(), hideLoading: jest.fn(),
    getUserCryptoManager: () => ({ getRandomValues: (options: any) => options.success({ randomValues: new ArrayBuffer(16) }) }),
    getStorageSync: jest.fn(() => ''), setStorageSync: jest.fn(), removeStorageSync: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (value: any) => { definition = value }
  let catalog: any; let diy: any; let orders: any
  jest.isolateModules(() => {
    const account = jest.requireMock('../miniprogram/services/account')
    catalog = jest.requireMock('../miniprogram/services/catalog')
    diy = jest.requireMock('../miniprogram/services/diy')
    orders = jest.requireMock('../miniprogram/services/orders')
    account.getProfile.mockResolvedValue({ nickname: '顾客', avatarUrl: null })
    account.listAddresses.mockResolvedValue([])
    account.listCoupons.mockResolvedValue([])
    catalog.listAllPublicBeads.mockResolvedValue([])
    catalog.listCategories.mockResolvedValue([])
    catalog.listRecommendedPackages.mockResolvedValue([])
    diy.listSchemes.mockResolvedValue([])
    orders.listOrders.mockResolvedValue([])
    const ApiError = (jest.requireActual('../miniprogram/services/request') as any).ApiError
    orders.findOrderByCheckoutToken.mockRejectedValue(new ApiError('订单不存在', 'COMMON_NOT_FOUND'))
    jest.requireMock('../miniprogram/services/commerce').getCommerceRule.mockResolvedValue({
      minimumOrderAmount: 20, freeShippingThreshold: 60, baseShippingFee: 8, configVersion: 1,
    })
    require(`../miniprogram/pages/${route}`)
  })
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) }
  page.setData = (patch: any, done?: () => void) => { Object.assign(page.data, patch); done?.() }
  return { page, wxMock, catalog, diy, orders }
}

const event = { currentTarget: { dataset: { id: 1, type: 'bead' } } }
const routes = [
  ['home/index', 'openDiy'],
  ['profile/index', 'openDiy'],
  ['catalog/index', 'openDetail'],
  ['catalog/detail', 'startDesign'],
  ['profile/favorites', 'startDesign'],
  ['diy/editor', 'goSchemes'],
  ['diy/schemes', 'openScheme'],
  ['order/list', 'openOrder'],
  ['order/confirm', 'openAddresses'],
]

describe('repeated click boundaries', () => {
  beforeEach(() => jest.resetAllMocks())

  it.each(routes)('%s starts %s immediately only once until returning', async (route, method) => {
    const { page, wxMock } = loadPage(route)
    page.setData({ id: 1 })
    page[method](event)
    page[method](event)
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    wxMock.navigateTo.mock.calls[0][0].success?.()
    page[method](event)
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    await page.onShow()
    page[method](event)
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
  })

  it('blocks competing home destinations and releases the lock after navigation fails', () => {
    const { page, wxMock } = loadPage('home/index')
    page.openDiy()
    page.openProfile()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    wxMock.navigateTo.mock.calls[0][0].fail({ errMsg: 'failed' })
    page.openProfile()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
    expect(wxMock.navigateTo.mock.calls[1][0].url).toBe('/pages/profile/index')
  })

  it('blocks duplicate redirects to home', () => {
    const { page, wxMock } = loadPage('profile/index')
    page.openHome()
    page.openHome()
    expect(wxMock.redirectTo).toHaveBeenCalledTimes(1)
  })

  it('retries navigation after a synchronous platform error', () => {
    const { page, wxMock } = loadPage('home/index')
    wxMock.navigateTo.mockImplementationOnce(() => { throw new Error('navigation unavailable') })
    expect(() => page.openDiy()).not.toThrow()
    page.openDiy()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
  })

  it('ignores an old navigation callback after returning and starting another route', async () => {
    const { page, wxMock } = loadPage('home/index')
    page.openDiy()
    const old = wxMock.navigateTo.mock.calls[0][0]
    await page.onShow()
    page.openProfile()
    old.fail({ errMsg: 'late callback' })
    page.openSchemes()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
    expect(wxMock.showToast).not.toHaveBeenCalled()
  })

  it('never creates a second order while redirecting or retrying a failed redirect', async () => {
    const { page, orders, wxMock } = loadPage('order/confirm')
    orders.createOrder.mockResolvedValue({ id: 9 })
    page.setData({ loading: false, schemeId: 1, scheme: { id: 1 }, quote: { quoteFingerprint: 'test-quote' }, productTotalCents: 3000,
      address: { receiverName: '顾客', phone: '13812345678', region: '上海', detail: '一号' } })
    await page.submitOrder()
    await page.submitOrder()
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    expect(wxMock.redirectTo).toHaveBeenCalledTimes(1)
    wxMock.redirectTo.mock.calls[0][0].fail({ errMsg: 'cannot open' })
    await page.submitOrder()
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    expect(wxMock.redirectTo).toHaveBeenCalledTimes(2)
    expect(wxMock.redirectTo.mock.calls[1][0].url).toBe('/pages/order/detail?id=9')
  })

  it('allows a failed order request to be retried', async () => {
    const { page, orders } = loadPage('order/confirm')
    orders.createOrder.mockRejectedValueOnce(new Error('failed')).mockResolvedValueOnce({ id: 9 })
    page.setData({ loading: false, schemeId: 1, scheme: { id: 1 }, quote: { quoteFingerprint: 'test-quote' }, productTotalCents: 3000,
      address: { receiverName: '顾客', phone: '13812345678', region: '上海', detail: '一号' } })
    await page.submitOrder()
    expect(page.data.submitting).toBe(false)
    await page.submitOrder()
    expect(orders.createOrder).toHaveBeenCalledTimes(2)
  })

  it('does not redirect a different page when order creation finishes after leaving checkout', async () => {
    const { page, orders, wxMock } = loadPage('order/confirm')
    const pending = deferred()
    orders.createOrder.mockReturnValueOnce(pending.promise)
    page.setData({ loading: false, schemeId: 1, scheme: { id: 1 }, quote: { quoteFingerprint: 'test-quote' }, productTotalCents: 3000,
      address: { receiverName: '顾客', phone: '13812345678', region: '上海', detail: '一号' } })
    const saving = page.submitOrder()
    for (let i = 0; i < 5; i++) await Promise.resolve()
    page.onUnload()
    page.setData = jest.fn()
    pending.resolve({ id: 9 })
    await saving
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    expect(page.setData).not.toHaveBeenCalled()
    expect(wxMock.redirectTo).not.toHaveBeenCalled()
    expect(wxMock.showToast).not.toHaveBeenCalled()
  })

  it('locks scheme deletion before confirmation and throughout deletion, then permits retry', async () => {
    const { page, diy, wxMock } = loadPage('diy/schemes')
    page.setData({ loading: false, schemes: [{ id: 1 }] })
    const pending = deferred()
    diy.deleteScheme.mockReturnValueOnce(pending.promise)
    const first = page.deleteScheme(event)
    void page.deleteScheme(event)
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    wxMock.showModal.mock.calls[0][0].success({ confirm: true, cancel: false })
    await Promise.resolve()
    void page.deleteScheme(event)
    expect(diy.deleteScheme).toHaveBeenCalledTimes(1)
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    pending.reject(new Error('offline'))
    await first
    const retry = page.deleteScheme(event)
    expect(wxMock.showModal).toHaveBeenCalledTimes(2)
    wxMock.showModal.mock.calls[1][0].success({ confirm: false, cancel: true })
    await retry
    const afterCancel = page.deleteScheme(event)
    expect(wxMock.showModal).toHaveBeenCalledTimes(3)
    wxMock.showModal.mock.calls[2][0].fail({ errMsg: 'cancelled' })
    await afterCancel
  })

  it('ignores an old delete tap after the scheme has been removed', async () => {
    const { page, diy, wxMock } = loadPage('diy/schemes')
    page.setData({ loading: false, schemes: [{ id: 1 }] })
    wxMock.showModal.mockImplementation((options) => options.success({ confirm: true }))
    diy.deleteScheme.mockResolvedValue(undefined)
    await page.deleteScheme(event)
    await page.deleteScheme(event)
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    expect(diy.deleteScheme).toHaveBeenCalledTimes(1)
  })

  it('keeps DIY save and navigation mutually exclusive across checkout persistence and departure', async () => {
    const { page, diy, wxMock } = loadPage('diy/editor')
    const pending = deferred()
    page.setData({ loading: false, sequence: [1], minimumOrderAmountCents: 0 })
    diy.createScheme.mockReturnValueOnce(pending.promise)
    const first = page.goCheckout()
    await page.goCheckout()
    await page.saveDraft()
    page.goSchemes()
    expect(diy.createScheme).toHaveBeenCalledTimes(1)
    expect(wxMock.navigateTo).not.toHaveBeenCalled()
    pending.resolve({ id: 7, version: 1, name: '我的方案' })
    await first
    expect(wxMock.navigateTo.mock.calls[0][0].url).toBe('/pages/order/confirm?schemeId=7')
    await page.goCheckout()
    await page.saveDraft()
    page.goSchemes()
    expect(diy.createScheme).toHaveBeenCalledTimes(1)
    expect(diy.updateScheme).not.toHaveBeenCalled()
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    wxMock.navigateTo.mock.calls[0][0].fail({ errMsg: 'cannot open' })
    diy.updateScheme.mockResolvedValueOnce({ id: 7, version: 2, name: '我的方案' })
    await page.goCheckout()
    expect(diy.createScheme).toHaveBeenCalledTimes(1)
    expect(diy.updateScheme).toHaveBeenCalledTimes(1)
    expect(wxMock.navigateTo).toHaveBeenCalledTimes(2)
  })
})
