import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CatalogView from './CatalogView.vue'
import {
  listAdminBeads,
  listAdminCategories,
  listAdminPackages,
} from '../api/catalog'

vi.mock('../api/catalog', () => ({
  listAdminBeads: vi.fn(),
  listAdminCategories: vi.fn(),
  listAdminPackages: vi.fn(),
  getAdminPackage: vi.fn(),
  createAdminBead: vi.fn(),
  updateAdminBead: vi.fn(),
  changeBeadStatus: vi.fn(),
  createAdminCategory: vi.fn(),
  updateAdminCategory: vi.fn(),
  changeCategoryStatus: vi.fn(),
  createAdminPackage: vi.fn(),
  updateAdminPackage: vi.fn(),
  changePackageStatus: vi.fn(),
  uploadCatalogFile: vi.fn(),
}))

/**
 * 商品管理首页必须通过 API 模块加载真实列表，而不是在组件中保存演示假数据。
 */
describe('CatalogView', () => {
  beforeEach(() => {
    vi.mocked(listAdminCategories).mockResolvedValue([
      {
        id: 1001,
        name: '天然水晶',
        slug: 'natural-crystal',
        sortOrder: 10,
        status: 'ON_SALE',
      },
    ])
    vi.mocked(listAdminBeads).mockResolvedValue({
      items: [
        {
          id: 2001,
          categoryId: 1001,
          skuCode: 'BEAD-CRYSTAL-001',
          name: '白水晶 8mm',
          material: '天然水晶',
          color: '透明',
          diameterMm: 8,
          salePrice: 12.8,
          costPrice: 5.2,
          stockAlertThreshold: 20,
          coverAssetId: null,
          tagsJson: '["净化","日常"]',
          status: 'ON_SALE',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    })
    vi.mocked(listAdminPackages).mockResolvedValue({
      items: [
        {
          id: 3001,
          packageCode: 'PACKAGE-CALM-001',
          name: '澄心日常手串',
          description: '白水晶与老山檀组成的日常搭配。',
          salePrice: 168,
          coverAssetId: null,
          status: 'ON_SALE',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    })
  })

  it('loads catalog rows through the API module', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    expect(wrapper.text()).toContain('白水晶 8mm')
    expect(wrapper.text()).toContain('BEAD-CRYSTAL-001')
    expect(wrapper.find('[data-test="create-bead"]').exists()).toBe(true)
  })

  it('exposes category, bead editing and package maintenance entry points', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    expect(wrapper.find('[data-test="tab-categories"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="tab-packages"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="edit-bead-2001"]').exists()).toBe(true)
  })

  it('loads the selected package maintenance panel through the API module', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    await wrapper.get('[data-test="tab-packages"]').trigger('click')
    await flushPromises()

    expect(listAdminPackages).toHaveBeenCalled()
    expect(wrapper.text()).toContain('澄心日常手串')
    expect(wrapper.find('[data-test="create-package"]').exists()).toBe(true)
  })
})
