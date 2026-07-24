import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DiySettingsView from './DiySettingsView.vue'
import {
  getAdminDiyConfiguration,
  saveCraftFeeRules,
  saveDiyRule,
  saveDiyVisualConfig,
} from '../api/diy'

vi.mock('../api/diy', () => ({
  getAdminDiyConfiguration: vi.fn(),
  saveDiyRule: vi.fn(),
  saveCraftFeeRules: vi.fn(),
  saveDiyVisualConfig: vi.fn(),
  createCordOption: vi.fn(),
  updateCordOption: vi.fn(),
}))

describe('DiySettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getAdminDiyConfiguration).mockResolvedValue({
      rule: {
        minWristMm: 120,
        maxWristMm: 250,
        snugAllowanceMm: 5,
        standardAllowanceMm: 10,
        looseAllowanceMm: 15,
        toleranceMm: 4,
        maxBeadCount: 60,
        configVersion: 1,
      },
      craftFeeRules: [
        {
          id: 5001,
          minBeadCount: 1,
          maxBeadCount: 60,
          fee: 10,
          ruleVersion: 1,
        },
      ],
      cordOptions: [
        {
          id: 6001,
          code: 'ELASTIC-CLEAR',
          name: '透明弹力绳',
          colorHex: '#E8E4DC',
          extraFee: 0,
          status: 'ENABLED',
          sortOrder: 10,
          optionVersion: 1,
        },
      ],
      visualConfig: {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.2,
        beadOverlapRatio: 0.06,
        configVersion: 1,
      },
    })
    vi.mocked(saveDiyRule).mockResolvedValue({
      rule: {
        minWristMm: 120,
        maxWristMm: 250,
        snugAllowanceMm: 5,
        standardAllowanceMm: 10,
        looseAllowanceMm: 15,
        toleranceMm: 4,
        maxBeadCount: 50,
        configVersion: 2,
      },
      craftFeeRules: [],
      cordOptions: [],
      visualConfig: {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.2,
        beadOverlapRatio: 0.06,
        configVersion: 1,
      },
    })
  })

  it('loads and saves sizing rules', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="max-bead-count"]').setValue('50')
    await wrapper.get('[data-test="save-diy-rule"]').trigger('click')

    expect(saveDiyRule).toHaveBeenCalledWith(
      expect.objectContaining({ maxBeadCount: 50 }),
    )
  })

  it('blocks overlapping craft fee ranges', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="add-craft-rule"]').trigger('click')
    await wrapper.get('[data-test="save-craft-rules"]').trigger('click')

    expect(wrapper.text()).toContain('工艺费区间不能重叠或留空档')
    expect(saveCraftFeeRules).not.toHaveBeenCalled()
  })

  it('saves global 2.5D visual configuration', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="bead-overlap-ratio"]').setValue('0.08')
    await wrapper.get('[data-test="save-visual-config"]').trigger('click')

    expect(saveDiyVisualConfig).toHaveBeenCalledWith({
      canvasBackgroundColor: '#fffdfb',
      braceletShadowOpacity: 0.16,
      beadShadowOpacity: 0.2,
      beadOverlapRatio: 0.08,
    })
  })

  it('blocks an overlap ratio above 0.15', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="bead-overlap-ratio"]').setValue('0.16')
    await wrapper.get('[data-test="save-visual-config"]').trigger('click')

    expect(wrapper.text()).toContain('珠子重叠比例必须在 0 到 0.15 之间')
    expect(saveDiyVisualConfig).not.toHaveBeenCalled()
  })

  it('blocks a blank overlap ratio', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="bead-overlap-ratio"]').setValue('')
    await wrapper.get('[data-test="save-visual-config"]').trigger('click')

    expect(wrapper.text()).toContain('珠子重叠比例不能为空')
    expect(saveDiyVisualConfig).not.toHaveBeenCalled()
  })
})
