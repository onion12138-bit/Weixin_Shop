import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommerceSettingsView from './CommerceSettingsView.vue'
import { getCommerceRule, saveCommerceRule } from '../api/commerce'

vi.mock('../api/commerce', () => ({
  getCommerceRule: vi.fn(),
  saveCommerceRule: vi.fn(),
}))

describe('CommerceSettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getCommerceRule).mockResolvedValue({
      minimumOrderAmount: 20,
      freeShippingThreshold: 60,
      baseShippingFee: 8,
      configVersion: 1,
    })
    vi.mocked(saveCommerceRule).mockResolvedValue({
      minimumOrderAmount: 30,
      freeShippingThreshold: 80,
      baseShippingFee: 10,
      configVersion: 2,
    })
  })

  it('loads and saves order and shipping rules together', async () => {
    const wrapper = mount(CommerceSettingsView)
    await flushPromises()

    await wrapper.get('[data-test="minimum-order-amount"]').setValue('30')
    await wrapper.get('[data-test="free-shipping-threshold"]').setValue('80')
    await wrapper.get('[data-test="base-shipping-fee"]').setValue('10')
    await wrapper.get('[data-test="save-commerce-rule"]').trigger('click')
    await flushPromises()

    expect(saveCommerceRule).toHaveBeenCalledWith({
      minimumOrderAmount: 30,
      freeShippingThreshold: 80,
      baseShippingFee: 10,
    })
    expect(wrapper.text()).toContain('交易规则已保存')
    expect(wrapper.text()).toContain('v2')
  })
})
