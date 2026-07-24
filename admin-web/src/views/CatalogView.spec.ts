import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CatalogView from './CatalogView.vue'
import {
  createAdminBead,
  downloadBeadImportTemplate,
  importAdminBeads,
  listBeadVisuals,
  createManualBeadVisual,
  publishBeadVisual,
  rollbackBeadVisual,
  listAdminBeads,
  listAdminCategories,
  listAdminPackages,
  uploadCatalogFile,
  updateAdminBead,
} from '../api/catalog'

vi.mock('../api/catalog', () => ({
  listAdminBeads: vi.fn(),
  listAdminCategories: vi.fn(),
  listAdminPackages: vi.fn(),
  getAdminPackage: vi.fn(),
  createAdminBead: vi.fn(),
  downloadBeadImportTemplate: vi.fn(),
  importAdminBeads: vi.fn(),
  updateAdminBead: vi.fn(),
  changeBeadStatus: vi.fn(),
  createAdminCategory: vi.fn(),
  updateAdminCategory: vi.fn(),
  changeCategoryStatus: vi.fn(),
  createAdminPackage: vi.fn(),
  updateAdminPackage: vi.fn(),
  changePackageStatus: vi.fn(),
  uploadCatalogFile: vi.fn(),
  listBeadVisuals: vi.fn(),
  createManualBeadVisual: vi.fn(),
  publishBeadVisual: vi.fn(),
  rollbackBeadVisual: vi.fn(),
}))

/**
 * 商品管理首页必须通过 API 模块加载真实列表，而不是在组件中保存演示假数据。
 */
describe('CatalogView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
          coverAssetId: 5001,
          coverUrl: '/api/public/files/2026/07/white-crystal.png',
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
    vi.mocked(downloadBeadImportTemplate).mockResolvedValue(
      new Blob(['template']),
    )
    vi.mocked(importAdminBeads).mockResolvedValue({
      totalRows: 1,
      successCount: 1,
      failureCount: 0,
      errors: [],
    })
    vi.mocked(listBeadVisuals).mockResolvedValue({
      publishedVisualVersionId: null,
      versions: [],
    })
  })

  it('loads catalog rows through the API module', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    expect(wrapper.text()).toContain('白水晶 8mm')
    expect(wrapper.text()).toContain('BEAD-CRYSTAL-001')
    expect(wrapper.find('[data-test="create-bead"]').exists()).toBe(true)
  })

  it('shows the uploaded cover and opens a large image preview', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    const cover = wrapper.get('[data-test="preview-bead-cover-2001"]')
    expect(cover.get('img').attributes('src')).toContain(
      '/api/public/files/2026/07/white-crystal.png',
    )
    await cover.trigger('click')
    expect(wrapper.get('[aria-label="商品封面大图预览"] img')
      .attributes('src')).toContain('white-crystal.png')
    await wrapper.get('[aria-label="关闭大图预览"]').trigger('click')
    expect(wrapper.find('[aria-label="商品封面大图预览"]').exists()).toBe(false)
  })

  it('opens the DIY visual workflow for the selected bead', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    await wrapper.get('[data-test="manage-bead-visual-2001"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('白水晶 8mm')
    expect(wrapper.find('[data-test="tray-file"]').exists()).toBe(true)
  })

  it('clears search immediately and sends category and attribute filters', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    await wrapper.get('[aria-label="搜索商品"]').setValue('紫水晶')
    await wrapper.get('[data-test="clear-bead-search"]').trigger('click')
    await flushPromises()
    expect(wrapper.get<HTMLInputElement>('[aria-label="搜索商品"]')
      .element.value).toBe('')
    expect(vi.mocked(listAdminBeads).mock.calls.at(-1)?.[0])
      .toEqual(expect.objectContaining({ keyword: '', page: 1 }))

    await wrapper.get('[aria-label="商品分类"]').setValue('1001')
    await wrapper.get('[aria-label="商品属性"]').setValue('透明 8mm')
    await wrapper.get('.toolbar .secondary-button').trigger('click')
    await flushPromises()
    expect(vi.mocked(listAdminBeads).mock.calls.at(-1)?.[0])
      .toEqual(expect.objectContaining({
        categoryId: 1001,
        attribute: '透明 8mm',
        page: 1,
      }))
  })

  it('keeps the newest search result when an older request finishes later', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    let resolveOlderRequest!: (value: Awaited<ReturnType<typeof listAdminBeads>>) => void
    const olderRequest = new Promise<Awaited<ReturnType<typeof listAdminBeads>>>(
      (resolve) => { resolveOlderRequest = resolve },
    )
    vi.mocked(listAdminBeads)
      .mockImplementationOnce(() => olderRequest)
      .mockResolvedValueOnce({
        items: [{
          id: 2001,
          categoryId: 1001,
          skuCode: 'BEAD-LATEST',
          name: '最新列表',
          material: '水晶',
          color: '透明',
          diameterMm: 8,
          salePrice: 10,
          costPrice: 2,
          stockAlertThreshold: 20,
          coverAssetId: null,
          coverUrl: null,
          tagsJson: '[]',
          status: 'ON_SALE',
        }],
        total: 1,
        page: 1,
        size: 20,
      })

    await wrapper.get('[aria-label="搜索商品"]').setValue('旧查询')
    await wrapper.get('.toolbar .secondary-button').trigger('click')
    await wrapper.get('[data-test="clear-bead-search"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('最新列表')

    resolveOlderRequest({
      items: [{
        id: 2002,
        categoryId: 1001,
        skuCode: 'BEAD-OLDER',
        name: '过期列表',
        material: '水晶',
        color: '紫色',
        diameterMm: 8,
        salePrice: 10,
        costPrice: 2,
        stockAlertThreshold: 20,
        coverAssetId: null,
        coverUrl: null,
        tagsJson: '[]',
        status: 'ON_SALE',
      }],
      total: 1,
      page: 1,
      size: 20,
    })
    await flushPromises()

    expect(wrapper.text()).toContain('最新列表')
    expect(wrapper.text()).not.toContain('过期列表')
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

  it('includes initial stock only when creating a bead', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()

    await wrapper.get('[data-test="create-bead"]').trigger('click')
    expect(wrapper.find('[data-test="initial-stock"]').exists()).toBe(true)
    expect(wrapper.get<HTMLInputElement>('[data-test="bead-sku-code"]')
      .element.value).toMatch(/^BEAD-NATURAL-CRYSTAL-8MM-\d{8}-[A-Z0-9]{4}$/)
    await wrapper.get('[data-test="initial-stock"]').setValue(321)
    const labels = wrapper.findAll('.catalog-form label')
    await labels.find((label) => label.text().includes('商品名称'))!
      .get('input').setValue('测试紫水晶')
    await labels.find((label) => label.text().includes('SKU 编码'))!
      .get('input').setValue('BEAD-TEST-VUE')
    await labels.find((label) => label.text().includes('材质'))!
      .get('input').setValue('水晶')
    await labels.find((label) => label.text().includes('颜色'))!
      .get('input').setValue('紫色')
    await wrapper.get('.catalog-form').trigger('submit')
    await flushPromises()

    expect(createAdminBead).toHaveBeenCalledWith(
      expect.objectContaining({ initialStock: 321 }),
    )

    await wrapper.get('[data-test="edit-bead-2001"]').trigger('click')
    expect(wrapper.find('[data-test="initial-stock"]').exists()).toBe(false)
    await wrapper.get('.catalog-form').trigger('submit')
    await flushPromises()

    const updatePayload = vi.mocked(updateAdminBead).mock.calls[0][1]
    expect(updatePayload).not.toHaveProperty('initialStock')
  })

  it('uses a decimal 5 MB upload limit before sending the file', async () => {
    const wrapper = mount(CatalogView)
    await flushPromises()
    await wrapper.get('[data-test="create-bead"]').trigger('click')
    expect(wrapper.text()).toContain('最大 5 MB')
    expect(wrapper.text()).not.toContain('5 MiB')

    const input = wrapper.get<HTMLInputElement>('.cover-upload input[type="file"]')
    const oversized = new File(
      [new Uint8Array(5_000_001)],
      'too-large.png',
      { type: 'image/png' },
    )
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [oversized],
    })
    await input.trigger('change')

    expect(wrapper.text()).toContain('封面图片不能超过 5 MB')
    expect(uploadCatalogFile).not.toHaveBeenCalled()
  })

  it('downloads the Excel template and reports row-level import errors', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:catalog-template'),
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })
    const anchorClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)
    vi.mocked(importAdminBeads).mockResolvedValueOnce({
      totalRows: 2,
      successCount: 1,
      failureCount: 1,
      errors: [{
        rowNumber: 3,
        skuCode: 'BEAD-DUPLICATE',
        message: 'SKU 编码已存在',
      }],
    })
    const wrapper = mount(CatalogView)
    await flushPromises()

    await wrapper.get('[data-test="import-beads"]').trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('Excel 批量导入商品')
    expect(wrapper.get('[role="dialog"]').text()).toContain('图片无需写入 Excel')

    await wrapper.get('[data-test="download-import-template"]').trigger('click')
    await flushPromises()
    expect(downloadBeadImportTemplate).toHaveBeenCalledOnce()
    expect(anchorClick).toHaveBeenCalledOnce()

    const file = new File(['xlsx'], 'catalog.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    await wrapper.get('[data-test="import-dropzone"]').trigger('drop', {
      dataTransfer: { files: [file] },
    })
    expect(wrapper.text()).toContain('catalog.xlsx')
    await wrapper.get('[data-test="submit-import"]').trigger('click')
    await flushPromises()

    expect(importAdminBeads).toHaveBeenCalledWith(file)
    expect(wrapper.text()).toContain('成功 1 行')
    expect(wrapper.text()).toContain('失败 1 行')
    expect(wrapper.text()).toContain('第 3 行')
    expect(wrapper.text()).toContain('BEAD-DUPLICATE')
    expect(wrapper.text()).toContain('SKU 编码已存在')
    anchorClick.mockRestore()
  })

  it('uploads a product cover by dropping an image', async () => {
    vi.mocked(uploadCatalogFile).mockResolvedValueOnce({
      id: 5901,
      storageKey: 'catalog/drop-cover.png',
      originalName: 'drop-cover.png',
      contentType: 'image/png',
      sizeBytes: 1024,
      publicUrl: '/api/public/files/catalog/drop-cover.png',
    })
    const wrapper = mount(CatalogView)
    await flushPromises()
    await wrapper.get('[data-test="create-bead"]').trigger('click')

    const image = new File(['image'], 'drop-cover.png', {
      type: 'image/png',
    })
    await wrapper.get('[data-test="cover-dropzone"]').trigger('drop', {
      dataTransfer: { files: [image] },
    })
    await flushPromises()

    expect(uploadCatalogFile).toHaveBeenCalledWith(image)
    expect(wrapper.text()).toContain('素材 #5901 已上传')
    expect(wrapper.get('img[alt="新商品封面预览"]')
      .attributes('src')).toContain('drop-cover.png')
  })

  it('also accepts a dropped cover in the package editor', async () => {
    vi.mocked(uploadCatalogFile).mockResolvedValueOnce({
      id: 5902,
      storageKey: 'catalog/package-cover.webp',
      originalName: 'package-cover.webp',
      contentType: 'image/webp',
      sizeBytes: 2048,
      publicUrl: '/api/public/files/catalog/package-cover.webp',
    })
    const wrapper = mount(CatalogView)
    await flushPromises()
    await wrapper.get('[data-test="tab-packages"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="create-package"]').trigger('click')
    await flushPromises()

    const image = new File(['image'], 'package-cover.webp', {
      type: 'image/webp',
    })
    await wrapper.get('[data-test="package-cover-dropzone"]').trigger('drop', {
      dataTransfer: { files: [image] },
    })
    await flushPromises()

    expect(uploadCatalogFile).toHaveBeenCalledWith(image)
    expect(wrapper.text()).toContain('素材 #5902 已选择')
  })
})
