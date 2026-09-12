declare function require(name: string): unknown

jest.mock('../miniprogram/services/catalog', () => ({
  listBeads: jest.fn(),
  listCategories: jest.fn(),
  listPackages: jest.fn(),
}))

import {
  listBeads,
  listCategories,
  listPackages,
} from '../miniprogram/services/catalog'

function loadPage() {
  let definition: Record<string, any> | null = null
  ;(globalThis as any).wx = {
    navigateTo: jest.fn(),
    stopPullDownRefresh: jest.fn(),
  }
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }

  jest.isolateModules(() => {
    require('../miniprogram/pages/catalog/index')
  })

  if (!definition) throw new Error('Page definition was not captured')
  const captured = definition as Record<string, any>
  const page: Record<string, any> = {
    ...captured,
    data: { ...captured.data },
  }
  page.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(page.data, patch)
  })
  return page
}

describe('catalog Page behavior', () => {
  beforeEach(() => {
    jest.mocked(listCategories).mockReset()
    jest.mocked(listBeads).mockReset()
    jest.mocked(listPackages).mockReset()
    jest.mocked(listCategories).mockResolvedValue([])
    jest.mocked(listBeads).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 10,
    })
    jest.mocked(listPackages).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 10,
    })
  })

  it('opens the finished-product list when requested by the home page', async () => {
    const page = loadPage()

    await page.onLoad({ tab: 'packages' })

    expect(page.data.activeTab).toBe('packages')
    expect(listPackages).toHaveBeenCalledWith({ page: 1, size: 10 })
    expect(listBeads).not.toHaveBeenCalled()
  })

  it('keeps the newest search when an older request finishes last', async () => {
    const page = loadPage()
    let finishOld!: (value: any) => void
    jest.mocked(listBeads).mockImplementationOnce(() => new Promise((resolve) => {
      finishOld = resolve
    }))
    const oldSearch = page.loadProducts(true)
    page.setData({ keyword: '新搜索' })
    jest.mocked(listBeads).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 10 })
    await page.loadProducts(true)
    finishOld({ items: [], total: 50, page: 1, size: 10 })
    await oldSearch

    expect(page.data.total).toBe(0)
    expect(page.data.hasMore).toBe(false)
  })

  it('does not skip a page when loading more fails', async () => {
    const page = loadPage()
    page.setData({ page: 1, hasMore: true })
    jest.mocked(listBeads).mockRejectedValueOnce(new Error('offline'))

    page.loadMore()
    for (let index = 0; index < 5; index += 1) await Promise.resolve()

    expect(page.data.page).toBe(1)
    page.loadMore()
    for (let index = 0; index < 5; index += 1) await Promise.resolve()
    expect(listBeads).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
  })
})
