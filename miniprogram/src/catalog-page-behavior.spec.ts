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
})
