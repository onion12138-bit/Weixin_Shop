import type { CustomerAddress } from '../miniprogram/services/account'
import type { OrderResponse, OrderStatus } from '../miniprogram/services/orders'
import { toOrderDetail } from '../miniprogram/utils/order-view-model'

declare function require(name: string): unknown

jest.mock('../miniprogram/services/request', () => ({
  ...jest.requireActual('../miniprogram/services/request'),
  request: jest.fn(),
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

async function flushPromises() {
  for (let index = 0; index < 5; index += 1) await Promise.resolve()
}

function loadPage(route: 'profile/addresses' | 'order/detail') {
  let definition: any
  let request!: jest.Mock
  const wxMock = { showModal: jest.fn(), showToast: jest.fn(), setClipboardData: jest.fn() }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (value: any) => { definition = value }
  jest.isolateModules(() => {
    request = jest.requireMock('../miniprogram/services/request').request
    require(`../miniprogram/pages/${route}`)
  })
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) }
  page.setData = (patch: Record<string, unknown>) => {
    for (const [path, value] of Object.entries(patch)) {
      const fields = path.split('.')
      const key = fields.pop()!
      let target = page.data
      for (const field of fields) target = target[field]
      target[key] = value
    }
  }
  return { page, request, wxMock }
}

const address: CustomerAddress = {
  id: 7, receiverName: '小丸子', phone: '13812345678', region: '上海',
  detail: '花园路 1 号', isDefault: false, status: 'ACTIVE',
  createdAt: '2026-09-01T10:00:00', updatedAt: '2026-09-01T10:00:00',
}
const addressTap = { currentTarget: { dataset: { id: 7 } } }

function addressPage() {
  const context = loadPage('profile/addresses')
  context.page.setData({ loading: false, addresses: [address], draft: { ...address } })
  return context
}

function order(status: OrderStatus): OrderResponse {
  return {
    id: 41, orderNo: 'ORD202609120001', status, schemeId: 12,
    beadAmount: 20, craftFee: 10, cordFee: 0, couponId: null,
    discountAmount: 0, shippingFee: 0, totalAmount: 30,
    contactName: '小丸子', contactPhone: '13812345678', addressDetail: '上海 花园路 1 号',
    items: [{ skuId: 8, skuCode: 'BEAD-8', name: '白水晶', quantity: 2, unitPrice: 10, totalPrice: 20 }],
    logisticsCompany: null, trackingNo: null, paidAt: null, shippedAt: null, completedAt: null,
    createdAt: '2026-09-12T10:00:00', updatedAt: '2026-09-12T10:00:00',
  }
}

const orderActions = [
  { action: 'mockPay', endpoint: 'mock-pay', before: 'PENDING_PAYMENT', after: 'IN_PRODUCTION' },
  { action: 'cancel', endpoint: 'cancel', before: 'PENDING_PAYMENT', after: 'CANCELLED' },
  { action: 'confirmReceipt', endpoint: 'confirm-receipt', before: 'SHIPPED', after: 'COMPLETED' },
] as const

describe('duplicate address mutations', () => {
  beforeEach(() => jest.resetAllMocks())

  it('sends one default-address request and keeps other writes blocked until the list refresh finishes', async () => {
    const { page, request, wxMock } = addressPage()
    page.setData({ showEditor: true })
    const mutation = deferred<CustomerAddress>()
    const refresh = deferred<CustomerAddress[]>()
    request.mockReturnValueOnce(mutation.promise).mockReturnValue(refresh.promise)
    const first = page.makeDefault(addressTap)
    void page.makeDefault(addressTap)
    void page.removeAddress(addressTap)
    void page.saveAddress()
    expect(request.mock.calls).toEqual([['/app/addresses/7/default', { method: 'PUT' }]])
    expect(wxMock.showModal).not.toHaveBeenCalled()
    mutation.resolve({ ...address, isDefault: true })
    await flushPromises()
    void page.makeDefault(addressTap)
    expect(request.mock.calls).toHaveLength(2)
    refresh.resolve([{ ...address, isDefault: true }])
    await first
    expect(page.data.addresses[0].isDefault).toBe(true)
    page.editAddress(addressTap)
    expect(page.data.showEditor).toBe(true)
    expect(page.data.editingId).toBe(7)
  })

  it('opens one delete confirmation and blocks competing address writes before confirmation', async () => {
    const { page, request, wxMock } = addressPage()
    page.setData({ showEditor: true })
    const mutation = deferred<boolean>()
    request.mockReturnValueOnce(mutation.promise).mockResolvedValue([])
    const first = page.removeAddress(addressTap)
    void page.removeAddress(addressTap)
    void page.makeDefault(addressTap)
    void page.saveAddress()
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    expect(request).not.toHaveBeenCalled()
    wxMock.showModal.mock.calls[0][0].success({ confirm: true })
    await flushPromises()
    void page.removeAddress(addressTap)
    void page.makeDefault(addressTap)
    expect(request.mock.calls).toEqual([['/app/addresses/7', { method: 'DELETE' }]])
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    mutation.resolve(true)
    await first
    expect(page.data.addresses).toEqual([])
  })

  it.each(['cancel', 'fail'] as const)('unlocks deletion after modal %s so a new tap can retry', async (outcome) => {
    const { page, request, wxMock } = addressPage()
    const first = page.removeAddress(addressTap)
    void page.removeAddress(addressTap)
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    const modal = wxMock.showModal.mock.calls[0][0]
    if (outcome === 'cancel') modal.success({ confirm: false })
    else modal.fail({ errMsg: 'showModal:fail' })
    await first
    expect(request).not.toHaveBeenCalled()
    request.mockResolvedValueOnce(true).mockResolvedValueOnce([])
    const retry = page.removeAddress(addressTap)
    expect(wxMock.showModal).toHaveBeenCalledTimes(2)
    wxMock.showModal.mock.calls[1][0].success({ confirm: true })
    await retry
    expect(request.mock.calls).toEqual([
      ['/app/addresses/7', { method: 'DELETE' }], ['/app/addresses'],
    ])
  })

  it.each(['makeDefault', 'removeAddress'] as const)('unlocks %s after a failed request and preserves a normal retry', async (action) => {
    const { page, request, wxMock } = addressPage()
    wxMock.showModal.mockImplementation((options) => options.success({ confirm: true }))
    request.mockRejectedValueOnce(new Error('网络连接失败')).mockResolvedValueOnce(address).mockResolvedValueOnce([address])
    const first = page[action](addressTap)
    void page[action](addressTap)
    await first
    await flushPromises()
    expect(request).toHaveBeenCalledTimes(1)
    expect(page.data.errorMessage).toBe('网络连接失败')
    await page[action](addressTap)
    await flushPromises()
    expect(request).toHaveBeenCalledTimes(3)
    expect(page.data.errorMessage).toBe('')
  })

  it('preserves the submitted editor and blocks default/delete writes while saving, then permits editing after failure', async () => {
    const { page, request, wxMock } = addressPage()
    const mutation = deferred<CustomerAddress>()
    request.mockReturnValue(mutation.promise)
    page.setData({ showEditor: true, editingId: 7 })
    const first = page.saveAddress()
    page.updateDraft({ currentTarget: { dataset: { field: 'receiverName' } }, detail: { value: '改名' } })
    expect(page.data.draft.receiverName).toBe('小丸子')
    page.toggleDraftDefault()
    expect(page.data.draft.isDefault).toBe(false)
    page.closeEditor()
    expect(page.data.showEditor).toBe(true)
    expect(page.data.editingId).toBe(7)
    page.addAddress()
    expect(page.data.editingId).toBe(7)
    page.setData({ addresses: [{ ...address, id: 8 }] })
    page.editAddress({ currentTarget: { dataset: { id: 8 } } })
    expect(page.data.editingId).toBe(7)
    page.setData({ addresses: [address] })
    page.editAddress(addressTap)
    void page.makeDefault(addressTap)
    void page.removeAddress(addressTap)
    expect(page.data.draft.receiverName).toBe('小丸子')
    expect(page.data.draft.isDefault).toBe(false)
    expect(page.data.showEditor).toBe(true)
    expect(page.data.editingId).toBe(7)
    expect(request).toHaveBeenCalledTimes(1)
    expect(wxMock.showModal).not.toHaveBeenCalled()
    mutation.reject(new Error('保存失败'))
    await first
    expect(page.data.saving).toBe(false)
    page.updateDraft({ currentTarget: { dataset: { field: 'receiverName' } }, detail: { value: '新姓名' } })
    expect(page.data.draft.receiverName).toBe('新姓名')
    page.closeEditor()
    expect(page.data.showEditor).toBe(false)
  })

  it.each([
    { mode: 'new', endpoint: '/app/addresses', method: 'POST' },
    { mode: 'edit', endpoint: '/app/addresses/7', method: 'PUT' },
  ])('ignores old save taps after a successful $mode save and permits saving a newly opened editor', async ({ mode, endpoint, method }) => {
    const { page, request } = addressPage()
    request.mockImplementation((_path, options) => Promise.resolve(options ? address : [address]))
    const openEditor = () => {
      if (mode === 'edit') page.editAddress(addressTap)
      else {
        page.addAddress()
        for (const [field, value] of Object.entries({
          receiverName: '小丸子', phone: '13812345678', region: '上海', detail: '花园路 1 号',
        })) {
          page.updateDraft({ currentTarget: { dataset: { field } }, detail: { value } })
        }
      }
    }
    openEditor()
    await page.saveAddress()
    expect(request.mock.calls).toEqual([
      [endpoint, { method, data: {
        receiverName: '小丸子', phone: '13812345678', region: '上海', detail: '花园路 1 号', isDefault: false,
      } }],
      ['/app/addresses'],
    ])
    expect(page.data.showEditor).toBe(false)
    expect(page.data.editingId).toBe(0)
    expect(page.data.saving).toBe(false)
    await page.saveAddress()
    await page.saveAddress()
    expect(request).toHaveBeenCalledTimes(2)
    openEditor()
    page.updateDraft({ currentTarget: { dataset: { field: 'detail' } }, detail: { value: '花园路 2 号' } })
    await page.saveAddress()
    expect(request).toHaveBeenCalledTimes(4)
    expect(request.mock.calls[2]).toEqual([endpoint, { method, data: {
      receiverName: '小丸子', phone: '13812345678', region: '上海', detail: '花园路 2 号', isDefault: false,
    } }])
    expect(page.data.showEditor).toBe(false)
  })
})

describe.each(orderActions)('duplicate order $action mutations', ({ action, endpoint, before, after }) => {
  beforeEach(() => jest.resetAllMocks())

  it('opens one confirmation, performs one request, and ignores taps after the order has moved on', async () => {
    const { page, request, wxMock } = loadPage('order/detail')
    page.setData({ id: 41, loading: false, order: toOrderDetail(order(before)) })
    const mutation = deferred<OrderResponse>()
    request.mockReturnValue(mutation.promise)
    const first = page[action]()
    void page[action]()
    void page[action === 'cancel' ? 'mockPay' : 'cancel']()
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    expect(page.data.operating).toBe(true)
    wxMock.showModal.mock.calls[0][0].success({ confirm: true })
    await flushPromises()
    void page[action]()
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    expect(request.mock.calls).toEqual([[`/app/orders/41/${endpoint}`, { method: 'POST' }]])
    mutation.resolve(order(after))
    await first
    expect(page.data.order.status).toBe(after)
    expect(page.data.operating).toBe(false)
    await page[action]()
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledTimes(1)
  })

  it.each(['cancel', 'fail'] as const)('releases the lock after modal %s and allows retry', async (outcome) => {
    const { page, request, wxMock } = loadPage('order/detail')
    page.setData({ id: 41, loading: false, order: toOrderDetail(order(before)) })
    const first = page[action]()
    expect(page.data.operating).toBe(true)
    const modal = wxMock.showModal.mock.calls[0][0]
    if (outcome === 'cancel') modal.success({ confirm: false })
    else modal.fail({ errMsg: 'showModal:fail' })
    await first
    expect(request).not.toHaveBeenCalled()
    expect(page.data.operating).toBe(false)
    request.mockResolvedValue(order(after))
    const retry = page[action]()
    expect(wxMock.showModal).toHaveBeenCalledTimes(2)
    wxMock.showModal.mock.calls[1][0].success({ confirm: true })
    await retry
    expect(page.data.order.status).toBe(after)
  })

  it('unlocks after request failure so a retry can complete', async () => {
    const { page, request, wxMock } = loadPage('order/detail')
    page.setData({ id: 41, loading: false, order: toOrderDetail(order(before)) })
    wxMock.showModal.mockImplementation((options) => options.success({ confirm: true }))
    request.mockRejectedValueOnce(new Error('网络连接失败')).mockResolvedValue(order(after))
    const first = page[action]()
    void page[action]()
    await first
    expect(wxMock.showModal).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledTimes(1)
    expect(page.data.errorMessage).toBe('网络连接失败')
    expect(page.data.operating).toBe(false)
    await page[action]()
    expect(request).toHaveBeenCalledTimes(2)
    expect(page.data.order.status).toBe(after)
    expect(page.data.errorMessage).toBe('')
  })

  it('checks the latest displayed status again after confirmation', async () => {
    const { page, request, wxMock } = loadPage('order/detail')
    page.setData({ id: 41, loading: false, order: toOrderDetail(order(before)) })
    const first = page[action]()
    page.setData({ order: toOrderDetail(order(after)) })
    wxMock.showModal.mock.calls[0][0].success({ confirm: true })
    await first
    expect(request).not.toHaveBeenCalled()
    expect(page.data.order.status).toBe(after)
    expect(page.data.operating).toBe(false)
  })
})
