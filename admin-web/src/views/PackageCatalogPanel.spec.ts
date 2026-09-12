import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getAdminPackage,
  listAdminBeads,
  listAdminPackages,
  updateAdminPackage,
  type AdminPackageDetail,
} from '../api/catalog'
import PackageCatalogPanel from './PackageCatalogPanel.vue'

vi.mock('../api/catalog', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/catalog')>(),
  getAdminPackage: vi.fn(),
  listAdminBeads: vi.fn(),
  listAdminPackages: vi.fn(),
  updateAdminPackage: vi.fn(),
}))

enableAutoUnmount(afterEach)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const first: AdminPackageDetail = {
  id: 1, packageCode: 'PACKAGE-1', name: '第一款搭配', description: '白色搭配',
  salePrice: 10, coverAssetId: null, coverUrl: null, status: 'ON_SALE', recommended: false,
  items: [{ beadSkuId: 1, quantity: 10, sortOrder: 1 }],
}
const second: AdminPackageDetail = {
  ...first, id: 2, packageCode: 'PACKAGE-2', name: '第二款搭配', description: '蓝色搭配',
  items: [{ beadSkuId: 2, quantity: 20, sortOrder: 1 }],
}

describe('PackageCatalogPanel editor requests', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listAdminPackages).mockResolvedValue({ items: [first, second], total: 2, page: 1, size: 20 })
    vi.mocked(listAdminBeads).mockResolvedValue({ items: [], total: 0, page: 1, size: 100 })
    vi.mocked(updateAdminPackage).mockResolvedValue(second)
  })

  it('saves the selected package with its own detail when an older editor request finishes last', async () => {
    const older = deferred<AdminPackageDetail>()
    vi.mocked(getAdminPackage).mockReturnValueOnce(older.promise).mockResolvedValueOnce(second)
    const wrapper = mount(PackageCatalogPanel)
    await flushPromises()
    await wrapper.get('[data-test="edit-package-1"]').trigger('click')
    await wrapper.get('[data-test="edit-package-2"]').trigger('click')
    await flushPromises()

    older.resolve(first)
    await flushPromises()
    expect(wrapper.get<HTMLInputElement>('.catalog-form input[maxlength="120"]').element.value)
      .toBe('第二款搭配')
    await wrapper.get('.catalog-form').trigger('submit')
    await flushPromises()
    expect(updateAdminPackage).toHaveBeenCalledWith(2, expect.objectContaining({
      packageCode: 'PACKAGE-2', name: '第二款搭配',
      items: [{ beadSkuId: 2, quantity: 20 }],
    }))
  })

  it('keeps the new editor loading when an older editor request fails', async () => {
    const older = deferred<AdminPackageDetail>()
    const latest = deferred<AdminPackageDetail>()
    vi.mocked(getAdminPackage).mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    const wrapper = mount(PackageCatalogPanel)
    await flushPromises()
    await wrapper.get('[data-test="edit-package-1"]').trigger('click')
    await wrapper.get('[data-test="edit-package-2"]').trigger('click')

    older.reject(new Error('older detail failed'))
    await flushPromises()
    expect(wrapper.get('[data-test="create-package"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    latest.resolve(second)
    await flushPromises()
    expect(wrapper.get<HTMLInputElement>('.catalog-form input[maxlength="120"]').element.value)
      .toBe('第二款搭配')
  })

  it('does not reopen a closed editor when an older response arrives', async () => {
    const older = deferred<AdminPackageDetail>()
    vi.mocked(getAdminPackage).mockReturnValueOnce(older.promise).mockResolvedValueOnce(second)
    const wrapper = mount(PackageCatalogPanel)
    await flushPromises()
    await wrapper.get('[data-test="edit-package-1"]').trigger('click')
    await wrapper.get('[data-test="edit-package-2"]').trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="关闭"]').trigger('click')

    older.resolve(first)
    await flushPromises()

    expect(wrapper.find('.catalog-form').exists()).toBe(false)
  })

  it('does not publish editor data after unmount', async () => {
    const pending = deferred<AdminPackageDetail>()
    vi.mocked(getAdminPackage).mockReturnValueOnce(pending.promise)
    const wrapper = mount(PackageCatalogPanel)
    await flushPromises()
    await wrapper.get('[data-test="edit-package-1"]').trigger('click')
    const state = wrapper.vm as unknown as { showForm: boolean; form: { name: string } }

    wrapper.unmount()
    pending.resolve(first)
    await flushPromises()

    expect(state.showForm).toBe(false)
    expect(state.form.name).toBe('')
  })
})
