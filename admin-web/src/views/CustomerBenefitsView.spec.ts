import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CustomerBenefitsView from './CustomerBenefitsView.vue'
import {
  grantCouponsBatch,
  listCouponTemplates,
  listCustomerAddresses,
  listCustomers,
  searchCustomersByPhone,
} from '../api/customers'

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
    vi.mocked(listCustomers).mockResolvedValue([customer])
    vi.mocked(listCouponTemplates).mockResolvedValue([coupon])
    vi.mocked(listCustomerAddresses).mockResolvedValue([{ id: 21, customerIdentityId: 15, customerNickname: '小丸子', receiverName: '小丸子', phone: '13812345678', region: '上海市徐汇区', detail: '测试路 18 号', isDefault: true, status: 'ACTIVE', updatedAt: '2026-08-08T10:00:00' }])
    vi.mocked(searchCustomersByPhone).mockResolvedValue([customer])
    vi.mocked(grantCouponsBatch).mockResolvedValue({ requestedCount: 1, grantedCount: 1, skippedCount: 0 })
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
      recipientMode: 'SELECTED',
      customerIdentityIds: [15],
      note: '',
    })

    await wrapper.get('.coupon-actions .primary-button').trigger('click')
    await wrapper.findAll('.recipient-tabs button')[2].trigger('click')
    await wrapper.get('[data-test="send-coupon"]').trigger('click')
    await flushPromises()
    expect(grantCouponsBatch).toHaveBeenLastCalledWith(9, {
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
})
