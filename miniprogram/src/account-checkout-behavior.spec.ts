declare function require(name: string): unknown
export {}

jest.mock('../miniprogram/services/account', () => ({
  listAddresses: jest.fn(), listCoupons: jest.fn(), getProfile: jest.fn(),
  saveProfile: jest.fn(), uploadAvatar: jest.fn(), createAddress: jest.fn(),
  updateAddress: jest.fn(), setDefaultAddress: jest.fn(), deleteAddress: jest.fn(),
}))
jest.mock('../miniprogram/services/orders', () => ({ createOrder: jest.fn(), getOrder: jest.fn() }))

function loadPage(route: string) {
  let definition: any
  let account: any
  let orders: any
  const wxMock = { showToast: jest.fn(), redirectTo: jest.fn(), navigateTo: jest.fn(), setClipboardData: jest.fn() }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (value: any) => { definition = value }
  jest.isolateModules(() => {
    account = jest.requireMock('../miniprogram/services/account')
    orders = jest.requireMock('../miniprogram/services/orders')
    require(`../miniprogram/pages/${route}`)
  })
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) }
  page.setData = (patch: any) => Object.assign(page.data, patch)
  return { page, account, orders, wxMock }
}

const address = { id: 1, receiverName: '小丸子', phone: '13812345678', region: '上海', detail: '花园路 1 号', isDefault: true }

describe('account and checkout audit regressions', () => {
  beforeEach(() => jest.clearAllMocks())

  it('creates only one order when submit is tapped twice before the response', async () => {
    const { page, orders } = loadPage('order/confirm')
    let finish!: (value: any) => void
    orders.createOrder.mockImplementation(() => new Promise((resolve: any) => { finish = resolve }))
    page.setData({ loading: false, schemeId: 1, scheme: { id: 1 }, address, productTotalCents: 3000 })
    const first = page.submitOrder()
    void page.submitOrder()
    expect(orders.createOrder).toHaveBeenCalledTimes(1)
    finish({ id: 9 })
    await first
  })

  it('does not submit checkout before the order information has loaded', async () => {
    const { page, orders, wxMock } = loadPage('order/confirm')
    page.setData({ address, productTotalCents: 3000 })
    await page.submitOrder()
    expect(orders.createOrder).not.toHaveBeenCalled()
    expect(wxMock.redirectTo).not.toHaveBeenCalled()
  })

  it('does not start another profile save while the first save is pending', async () => {
    const { page, account } = loadPage('profile/edit')
    let finish!: (value: any) => void
    account.saveProfile.mockImplementation(() => new Promise((resolve: any) => { finish = resolve }))
    page.setData({ loading: false, nickname: '小丸子' })
    const first = page.submit()
    void page.submit()
    expect(account.saveProfile).toHaveBeenCalledTimes(1)
    finish({ nickname: '小丸子', avatarAssetId: null, avatarUrl: null })
    await first
  })

  it('does not discard a newly chosen avatar while a previous profile save is pending', () => {
    const { page } = loadPage('profile/edit')
    page.setData({ saving: true, avatarUrl: '/tmp/saving.png', pendingAvatarPath: '/tmp/saving.png' })
    page.chooseAvatar({ detail: { avatarUrl: '/tmp/new.png' } })
    expect(page.data.pendingAvatarPath).toBe('/tmp/saving.png')
  })

  it('does not create duplicate addresses from repeated save taps', async () => {
    const { page, account } = loadPage('profile/addresses')
    let finish!: (value: any) => void
    account.createAddress.mockImplementation(() => new Promise((resolve: any) => { finish = resolve }))
    account.listAddresses.mockResolvedValue([address])
    page.setData({ loading: false, draft: address })
    const first = page.saveAddress()
    void page.saveAddress()
    expect(account.createAddress).toHaveBeenCalledTimes(1)
    finish(address)
    await first
  })

  it('shows coupon amounts and thresholds without rounding away cents', async () => {
    const { page, account } = loadPage('profile/coupons')
    account.listCoupons.mockResolvedValue([{
      id: 1, templateId: 1, code: 'HALF', name: '优惠券', description: '',
      status: 'UNUSED', discountAmount: 5.5, minimumSpend: 20.5,
      issuedAt: '2026-01-01T00:00:00', expiresAt: '2030-01-01T00:00:00', note: null,
    }])
    await page.loadCoupons()
    expect(page.data.couponCards[0]).toMatchObject({ amountText: '¥5.50', thresholdText: '满 ¥20.50 可用' })
  })

  it('blocks a second order operation while the first is pending', async () => {
    const { page } = loadPage('order/detail')
    page.setData({ operating: true })
    const action = jest.fn()
    await page.runAction(action, '完成')
    expect(action).not.toHaveBeenCalled()
    expect(page.data.operating).toBe(true)
  })

  it('copies the actual tracking number and reports clipboard failure', () => {
    const { page, wxMock } = loadPage('order/detail')
    page.setData({ order: { logisticsCompany: '顺丰速运', trackingNo: ' SF123456789 ' } })
    page.copyTrackingNo()
    expect(wxMock.setClipboardData).toHaveBeenCalledWith(expect.objectContaining({ data: 'SF123456789' }))
    wxMock.setClipboardData.mock.calls[0][0].fail()
    expect(wxMock.showToast).toHaveBeenCalledWith({ title: '复制失败，请重试', icon: 'none' })
  })

  it('does not copy an absent tracking number', () => {
    const { page, wxMock } = loadPage('order/detail')
    page.setData({ order: { trackingNo: null } })
    page.copyTrackingNo()
    expect(wxMock.setClipboardData).not.toHaveBeenCalled()
  })
})
