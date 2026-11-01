import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CustomerBenefitsView from './CustomerBenefitsView.vue'
import { ApiError } from '../api/http'
import {
  createCouponTemplate,
  grantCouponsBatch,
  listCouponTemplates,
  listCustomerAddresses,
  listCustomers,
  searchCustomersByPhone,
  updateCouponTemplate,
  type AdminCustomerAddress,
  type CouponTemplate,
  type CustomerSummary,
} from '../api/customers'

enableAutoUnmount(afterEach)
afterEach(() => vi.useRealTimers())

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

vi.mock('../api/customers', () => ({
  listCustomers: vi.fn(),
  searchCustomersByPhone: vi.fn(),
  listCustomerAddresses: vi.fn(),
  changeCustomerAddressStatus: vi.fn(),
  listCouponTemplates: vi.fn(),
  createCouponTemplate: vi.fn(),
  updateCouponTemplate: vi.fn(),
  grantCouponsBatch: vi.fn(),
}))

const customer = {
  id: 15,
  nickname: '小丸子',
  avatarUrl: null,
  phone: '13812345678',
  address: '上海市徐汇区测试路 18 号',
  addressCount: 2,
  source: 'GUEST',
  createdAt: '2026-08-08T10:00:00',
}

const coupon = {
  id: 9,
  code: 'CARE-10',
  name: '客服关怀券',
  description: '满一百元可用',
  discountAmount: 10,
  minimumSpend: 100,
  validDays: 30,
  status: 'ACTIVE' as const,
  issuedCount: 1,
  createdAt: '2026-08-08T10:00:00',
  updatedAt: '2026-08-08T10:00:00',
}

describe('CustomerBenefitsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    sessionStorage.clear()
    vi.mocked(listCustomers).mockResolvedValue([customer])
    vi.mocked(listCouponTemplates).mockResolvedValue([coupon])
    vi.mocked(listCustomerAddresses).mockResolvedValue([{ id: 21, customerIdentityId: 15, customerNickname: '小丸子', receiverName: '小丸子', phone: '13812345678', region: '上海市徐汇区', detail: '测试路 18 号', isDefault: true, status: 'ACTIVE', updatedAt: '2026-08-08T10:00:00' }])
    vi.mocked(searchCustomersByPhone).mockResolvedValue([customer])
    vi.mocked(grantCouponsBatch).mockResolvedValue({ requestedCount: 1, grantedCount: 1, skippedCount: 0 })
  })

  it('does not restore stale customer data when an older tab refresh finishes last', async () => {
    const older = deferred<CustomerSummary[]>()
    vi.mocked(listCustomers).mockReturnValueOnce(older.promise)
      .mockResolvedValue([{ ...customer, nickname: '最新客户' }])
    const wrapper = mount(CustomerBenefitsView)
    await wrapper.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    await wrapper.get('.customer-tabs button:first-child').trigger('click')
    await flushPromises()
    older.resolve([{ ...customer, nickname: '过期客户' }])
    await flushPromises()
    expect(wrapper.text()).toContain('最新客户')
    expect(wrapper.text()).not.toContain('过期客户')
  })

  it('shows coupon amounts and minimum spend without rounding away cents', async () => {
    vi.mocked(listCouponTemplates).mockResolvedValue([{ ...coupon, discountAmount: 10.49, minimumSpend: 100.25 }])
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()
    await wrapper.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    expect(wrapper.get('.coupon-value').text()).toContain('¥10.49')
    expect(wrapper.get('.coupon-value').text()).toContain('¥100.25')
    wrapper.unmount()
  })

  it('merges address management into each customer row', async () => {
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()

    expect(wrapper.text()).not.toContain('地址管理')
    expect(wrapper.text()).toContain('查看 2 个地址')
    await wrapper.get('[data-test="open-customer-addresses"]').trigger('click')
    await flushPromises()

    expect(listCustomerAddresses).toHaveBeenCalledWith({ customerIdentityId: 15, status: '' })
    expect(wrapper.text()).toContain('小丸子的地址')
    expect(wrapper.text()).toContain('测试路 18 号')
    expect(wrapper.text()).toContain('2026-08-08 10:00:00')
  })

  it('searches customers by phone and supports selected or all-recipient grants', async () => {
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()
    await wrapper.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    await wrapper.get('.coupon-actions .primary-button').trigger('click')

    await wrapper.get('[data-test="coupon-customer-search"]').setValue('3456')
    await new Promise((resolve) => setTimeout(resolve, 330))
    await flushPromises()
    expect(searchCustomersByPhone).toHaveBeenCalledWith('3456', 50)

    await wrapper.get('.customer-option').trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(grantCouponsBatch).toHaveBeenLastCalledWith(9, {
      batchId: expect.any(String),
      recipientMode: 'SELECTED',
      customerIdentityIds: [15],
      note: '',
    })

    await wrapper.get('.coupon-actions .primary-button').trigger('click')
    await wrapper.findAll('.recipient-tabs button')[2].trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(grantCouponsBatch).toHaveBeenLastCalledWith(9, {
      batchId: expect.any(String),
      recipientMode: 'ALL',
      customerIdentityIds: [],
      note: '',
    })
  })

  it('shows ten customers initially and loads the next ten while retaining phone search', async () => {
    const customerItems = Array.from({ length: 21 }, (_, index) => ({
      ...customer,
      id: index + 1,
      nickname: `客户${index + 1}`,
      phone: `1380000${String(index + 1).padStart(4, '0')}`,
    }))
    vi.mocked(listCustomers).mockResolvedValue(customerItems)
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()
    await wrapper.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    await wrapper.get('.coupon-actions .primary-button').trigger('click')

    expect(wrapper.findAll('.customer-option')).toHaveLength(10)
    await wrapper.get('[data-test="load-more-customers"]').trigger('click')
    expect(wrapper.findAll('.customer-option')).toHaveLength(20)
    await wrapper.get('[data-test="load-more-customers"]').trigger('click')
    expect(wrapper.findAll('.customer-option')).toHaveLength(21)

    await wrapper.get('[data-test="coupon-customer-search"]').setValue('3456')
    await new Promise((resolve) => setTimeout(resolve, 330))
    await flushPromises()
    expect(searchCustomersByPhone).toHaveBeenCalledWith('3456', 50)
  })

  async function openGrantDialog() {
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()
    await wrapper.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    await wrapper.get('.coupon-actions .primary-button').trigger('click')
    return wrapper
  }

  it('invalidates an older phone result as soon as the query changes, before the debounce expires', async () => {
    const wrapper = await openGrantDialog()
    vi.useFakeTimers()
    const older = deferred<CustomerSummary[]>()
    vi.mocked(searchCustomersByPhone).mockReturnValueOnce(older.promise)
      .mockResolvedValueOnce([{ ...customer, id: 16, nickname: '新查询客户' }])
    await wrapper.get('[data-test="coupon-customer-search"]').setValue('138')
    await vi.advanceTimersByTimeAsync(300)
    await wrapper.get('[data-test="coupon-customer-search"]').setValue('139')

    older.resolve([{ ...customer, nickname: '过期查询客户' }])
    await flushPromises()
    expect(wrapper.get('[data-test="coupon-customer-results"]').text()).not.toContain('过期查询客户')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(wrapper.get('[data-test="coupon-customer-results"]').text()).toContain('新查询客户')
  })

  it('keeps a new phone search loading when an older search fails', async () => {
    const wrapper = await openGrantDialog()
    vi.useFakeTimers()
    const older = deferred<CustomerSummary[]>()
    const latest = deferred<CustomerSummary[]>()
    vi.mocked(searchCustomersByPhone).mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    await wrapper.get('[data-test="coupon-customer-search"]').setValue('138')
    await vi.advanceTimersByTimeAsync(300)
    await wrapper.get('[data-test="coupon-customer-search"]').setValue('139')
    await vi.advanceTimersByTimeAsync(300)

    older.reject(new Error('过期搜索错误'))
    await flushPromises()
    expect(wrapper.get('[data-test="coupon-customer-results"]').text()).toContain('正在搜索')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    latest.resolve([customer])
    await flushPromises()
  })

  it('cancels pending phone debounce when the grant dialog closes', async () => {
    const wrapper = await openGrantDialog()
    vi.useFakeTimers()
    await wrapper.get('[data-test="coupon-customer-search"]').setValue('138')
    await wrapper.get('.grant-modal .panel-title .link-button').trigger('click')

    await vi.advanceTimersByTimeAsync(300)

    expect(searchCustomersByPhone).not.toHaveBeenCalled()
  })

  it.each(['close', 'unmount'])('ignores a phone response after %s', async (action) => {
    const wrapper = await openGrantDialog()
    vi.useFakeTimers()
    const pending = deferred<CustomerSummary[]>()
    vi.mocked(searchCustomersByPhone).mockReturnValueOnce(pending.promise)
    await wrapper.get('[data-test="coupon-customer-search"]').setValue('138')
    await vi.advanceTimersByTimeAsync(300)
    const state = wrapper.vm as unknown as { phoneResults: CustomerSummary[] }
    if (action === 'close') await wrapper.get('.grant-modal .panel-title .link-button').trigger('click')
    else wrapper.unmount()
    const previousResults = state.phoneResults

    pending.resolve([customer])
    await flushPromises()

    expect(state.phoneResults).toBe(previousResults)
  })

  it.each(['success', 'failure'])('ignores an older address %s after switching customers', async (outcome) => {
    vi.mocked(listCustomers).mockResolvedValue([customer, { ...customer, id: 16, nickname: '第二位顾客' }])
    const older = deferred<AdminCustomerAddress[]>()
    const latest = deferred<AdminCustomerAddress[]>()
    vi.mocked(listCustomerAddresses).mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()
    await wrapper.findAll('[data-test="open-customer-addresses"]')[0].trigger('click')
    await wrapper.get('.address-modal .panel-title .link-button').trigger('click')
    await wrapper.findAll('[data-test="open-customer-addresses"]')[1].trigger('click')

    if (outcome === 'success') older.resolve([])
    else older.reject(new Error('过期地址错误'))
    await flushPromises()
    expect(wrapper.get('.address-modal').text()).toContain('正在读取地址')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    latest.resolve([{
      id: 22, customerIdentityId: 16, customerNickname: '第二位顾客', receiverName: '第二位顾客',
      phone: '13912345678', region: '上海市', detail: '第二个地址', isDefault: true,
      status: 'ACTIVE', updatedAt: '2026-08-08T10:00:00',
    }])
    await flushPromises()
    expect(wrapper.get('.address-modal').text()).toContain('第二个地址')
  })

  it.each(['create', 'update'])('prevents duplicate %s coupon submissions before rendering disables the button', async (action) => {
    const pending = deferred<CouponTemplate>()
    const save = action === 'create' ? createCouponTemplate : updateCouponTemplate
    vi.mocked(save).mockReturnValue(pending.promise)
    const wrapper = mount(CustomerBenefitsView)
    await flushPromises()
    await wrapper.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    if (action === 'update') await wrapper.get('.coupon-actions .link-button').trigger('click')
    else {
      await wrapper.get('input[placeholder="例如 NEW-20"]').setValue('NEW-20')
      await wrapper.get('input[placeholder="例如 新客立减券"]').setValue('新客券')
      await wrapper.get('textarea').setValue('测试优惠说明')
    }
    const form = wrapper.get('.coupon-form').element
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))

    expect(save).toHaveBeenCalledTimes(1)
    pending.resolve(coupon)
    await flushPromises()
  })

  it('keeps a recoverable grant batch across a failed response and page remount', async () => {
    vi.mocked(grantCouponsBatch).mockRejectedValueOnce(new Error('连接断开'))
    const wrapper = await openGrantDialog()
    await wrapper.get('.customer-option').trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    const original = vi.mocked(grantCouponsBatch).mock.calls[0]
    expect(original[1]).toEqual(expect.objectContaining({ batchId: expect.any(String) }))
    expect(wrapper.text()).toContain('重试确认')
    wrapper.unmount()
    const restored = mount(CustomerBenefitsView)
    await flushPromises()
    expect(restored.text()).toContain('重试确认')
    await restored.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(grantCouponsBatch).toHaveBeenLastCalledWith(...original)
    expect(restored.text()).toContain('成功 1 位')
  })

  it('prevents duplicate coupon grants before rendering disables the send button', async () => {
    const pending = deferred<Awaited<ReturnType<typeof grantCouponsBatch>>>()
    vi.mocked(grantCouponsBatch).mockReturnValue(pending.promise)
    const wrapper = await openGrantDialog()
    await wrapper.get('.customer-option').trigger('click')
    const button = wrapper.get('[data-test="send-coupon"]').element
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(grantCouponsBatch).toHaveBeenCalledTimes(1)
    pending.resolve({ requestedCount: 1, grantedCount: 1, skippedCount: 0 })
    await flushPromises()
  })

  it('retains the original batch when retry receives an untrusted proxy 404', async () => {
    vi.mocked(grantCouponsBatch).mockRejectedValueOnce(new Error('响应丢失'))
      .mockRejectedValueOnce(new ApiError('请求失败', 'COMMON_INTERNAL_ERROR', 404))
    const wrapper = await openGrantDialog()
    await wrapper.get('.customer-option').trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    const original = vi.mocked(grantCouponsBatch).mock.calls[0]
    const pending = sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')).toBe(pending)
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(grantCouponsBatch).toHaveBeenLastCalledWith(...original)
    expect(wrapper.text()).toContain('成功 1 位')
  })

  it('preserves a newer batch when an unmounted page receives an older grant response', async () => {
    const oldRequest = deferred<Awaited<ReturnType<typeof grantCouponsBatch>>>()
    const newRequest = deferred<Awaited<ReturnType<typeof grantCouponsBatch>>>()
    vi.mocked(grantCouponsBatch).mockReturnValueOnce(oldRequest.promise)
      .mockResolvedValueOnce({ requestedCount: 1, grantedCount: 1, skippedCount: 0 })
      .mockReturnValueOnce(newRequest.promise)
    const oldPage = await openGrantDialog()
    await oldPage.get('.customer-option').trigger('click')
    await oldPage.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    oldPage.unmount()

    const newPage = mount(CustomerBenefitsView)
    await flushPromises()
    await newPage.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    await newPage.get('.customer-tabs button:last-child').trigger('click')
    await flushPromises()
    await newPage.get('.coupon-actions .primary-button').trigger('click')
    await newPage.get('.customer-option').trigger('click')
    await newPage.get('[data-test="send-coupon"]').trigger('click')
    const pending = sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')
    expect(pending).not.toBeNull()
    expect(JSON.parse(pending!).payload.batchId).toBe(vi.mocked(grantCouponsBatch).mock.calls[2][1].batchId)

    oldRequest.resolve({ requestedCount: 1, grantedCount: 1, skippedCount: 0 })
    await flushPromises()
    expect(sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')).toBe(pending)
    newRequest.reject(new Error('响应丢失'))
    await flushPromises()
    expect(sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')).toBe(pending)
  })

  it('does not clear a newer grant or unlock its request when an earlier success refresh fails', async () => {
    vi.mocked(listCouponTemplates).mockResolvedValue([coupon, { ...coupon, id: 10, name: '另一张券' }])
    const wrapper = await openGrantDialog()
    const refresh = deferred<CouponTemplate[]>()
    vi.mocked(listCouponTemplates).mockReturnValueOnce(refresh.promise)
    await wrapper.get('.customer-option').trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('成功 1 位')

    const nextGrant = deferred<Awaited<ReturnType<typeof grantCouponsBatch>>>()
    vi.mocked(grantCouponsBatch).mockReturnValueOnce(nextGrant.promise)
    await wrapper.findAll('.coupon-actions .primary-button')[1].trigger('click')
    await wrapper.get('.customer-option').trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    const pending = sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')
    expect(pending).not.toBeNull()
    refresh.reject(new ApiError('列表读取失败', 'COMMON_NOT_FOUND', 404))
    await flushPromises()

    expect(sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')).toBe(pending)
    expect(wrapper.get('[data-test="send-coupon"]').attributes('disabled')).toBeDefined()
    wrapper.get('[data-test="send-coupon"]').element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(grantCouponsBatch).toHaveBeenCalledTimes(2)
    nextGrant.reject(new Error('响应丢失'))
    await flushPromises()
    expect(sessionStorage.getItem('diy_admin_pending_coupon_grant_v1')).toBe(pending)
  })
})
