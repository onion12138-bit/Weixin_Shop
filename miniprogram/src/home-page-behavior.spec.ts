declare function require(name: string): unknown

jest.mock('../miniprogram/services/catalog', () => ({
  listRecommendedPackages: jest.fn(),
}))
jest.mock('../miniprogram/services/diy', () => ({
  listSchemes: jest.fn(),
}))

import { listRecommendedPackages } from '../miniprogram/services/catalog'
import { listSchemes } from '../miniprogram/services/diy'

const listRecommendedPackagesMock =
  listRecommendedPackages as jest.MockedFunction<
    typeof listRecommendedPackages
  >
const listSchemesMock = listSchemes as jest.MockedFunction<typeof listSchemes>

function loadPage() {
  let definition: Record<string, any> | null = null
  const wxMock = {
    navigateTo: jest.fn(),
    pageScrollTo: jest.fn(),
    showToast: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }

  jest.isolateModules(() => {
    require('../miniprogram/pages/home/index')
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
  return { page, wxMock }
}

describe('home Page behavior', () => {
  beforeEach(() => {
    listRecommendedPackagesMock.mockReset()
    listRecommendedPackagesMock.mockResolvedValue([])
    listSchemesMock.mockReset()
    listSchemesMock.mockResolvedValue([])
  })

  it.each([
    ['openDiy', '/pages/diy/editor'],
    ['openRecommendation', '/pages/recommendation/index'],
    ['openCatalog', '/pages/catalog/index?tab=packages'],
    ['openSchemes', '/pages/diy/schemes'],
    ['openProfile', '/pages/profile/index'],
  ])('%s opens the existing route', (method, url) => {
    const { page, wxMock } = loadPage()

    page[method]()

    expect(wxMock.navigateTo).toHaveBeenCalledWith(expect.objectContaining({ url }))
  })

  it('shows a safe message when an existing route cannot open', () => {
    const { page, wxMock } = loadPage()
    wxMock.navigateTo.mockImplementationOnce(({ fail }) => fail())

    page.openDiy()

    expect(wxMock.showToast).toHaveBeenCalledWith({
      title: '页面暂时无法打开',
      icon: 'none',
    })
  })

  it('opens AI recommendation with the tapped wish inspiration', () => {
    const { page, wxMock } = loadPage()

    page.openWishInspiration({
      currentTarget: { dataset: { wish: '财运' } },
    })

    expect(wxMock.navigateTo).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pages/recommendation/index?wishInspiration=%E8%B4%A2%E8%BF%90',
    }))
  })

  it('loads backend-recommended finished products and opens their detail', async () => {
    listRecommendedPackagesMock.mockResolvedValueOnce([{
      id: 3001,
      packageCode: 'PACKAGE-CALM-001',
      name: '澄心日常手串',
      description: '日常搭配',
      salePrice: 168,
      coverUrl: '/cover.png',
    }])
    const { page, wxMock } = loadPage()

    await page.onLoad()
    page.openProductDetail({ currentTarget: { dataset: { id: 3001 } } })

    expect(listRecommendedPackagesMock).toHaveBeenCalledWith(4)
    expect(page.data.products).toEqual([expect.objectContaining({
      id: 3001,
      title: '澄心日常手串',
      priceText: '¥168.00',
    })])
    expect(wxMock.navigateTo).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pages/catalog/detail?id=3001&type=package',
    }))
  })

  it('shows the latest real DIY scheme with a shadow-free bead preview', async () => {
    listSchemesMock.mockResolvedValueOnce([{
      id: 18,
      name: '周末的平静手串',
      wristCircumferenceMm: 160,
      fitType: 'STANDARD',
      cordOptionId: 1,
      sequence: [2001, 2002, 2001],
      beadCount: 3,
      catalogSnapshot: [
        {
          skuId: 2001,
          skuCode: 'BEAD-CRYSTAL-001',
          name: '白水晶',
          color: '透明',
          diameterMm: 8,
          coverUrl: 'https://shop.test/white-cover.png',
          status: 'ON_SALE',
          diyVisual: {
            trayUrl: 'https://shop.test/white-tray.png',
            canvasUrl: 'https://shop.test/white-canvas.png',
            version: 1,
          },
        },
        {
          skuId: 2002,
          skuCode: 'BEAD-AMETHYST-001',
          name: '紫水晶',
          color: '紫色',
          diameterMm: 8,
          coverUrl: 'https://shop.test/purple-cover.png',
          status: 'ON_SALE',
          diyVisual: null,
        },
      ],
      quotable: true,
      issues: [],
      skuQuantities: { 2001: 2, 2002: 1 },
      currentLengthMm: 24,
      targetLengthMm: 170,
      deviationMm: -146,
      sizeStatus: 'TOO_SHORT',
      beadAmount: 38.4,
      craftFee: 10,
      cordFee: 0,
      totalAmount: 48.4,
      quoteFingerprint: 'quote-18',
      quoteStatus: 'VALID',
      previewAssetId: 99,
      previewUrl: 'https://shop.test/legacy-preview-with-shadow.png',
      version: 2,
      createdAt: '2026-08-07T10:00:00',
      updatedAt: '2026-08-08T10:00:00',
    }])
    const { page, wxMock } = loadPage()

    await page.onShow()
    page.openLatestScheme({ currentTarget: { dataset: { id: 18 } } })

    expect(page.data.scheme).toEqual(expect.objectContaining({
      id: 18,
      name: '周末的平静手串',
      crystals: '白水晶 · 紫水晶',
      createdAt: '2026-08-07',
    }))
    expect(page.data.scheme.previewBeads).toHaveLength(3)
    expect(page.data.scheme.previewBeads[0].imageUrl)
      .toBe('https://shop.test/white-canvas.png')
    expect(page.data.scheme.previewBeads.map((item: { imageUrl: string }) => item.imageUrl))
      .not.toContain('https://shop.test/legacy-preview-with-shadow.png')
    expect(wxMock.navigateTo).toHaveBeenCalledWith(expect.objectContaining({
      url: '/pages/diy/editor?schemeId=18',
    }))
  })

  it('switches between the two specified groups of four wish inspirations', () => {
    const { page } = loadPage()

    expect(page.data.wishes.map((wish: { name: string }) => wish.name))
      .toEqual(['财运', '姻缘', '事业', '学业'])

    page.rotateMoods()

    expect(page.data.wishes.map((wish: { name: string }) => wish.name))
      .toEqual(['平安', '活力', '相守', '家庭'])

    page.rotateMoods()

    expect(page.data.wishes.map((wish: { name: string }) => wish.name))
      .toEqual(['财运', '姻缘', '事业', '学业'])
  })

  it('returns home and scrolls back to the top', () => {
    const { page, wxMock } = loadPage()

    page.backToTop()

    expect(wxMock.pageScrollTo).toHaveBeenCalledWith({
      scrollTop: 0,
      duration: 300,
    })
  })

  it('returns the confirmed share copy and local hero image', () => {
    const { page } = loadPage()

    expect(page.onShareAppMessage()).toEqual({
      title: '一颗珠子，一种心情｜来设计你的能量手串',
      path: '/pages/home/index',
      imageUrl: '/assets/images/home/hero-bracelet-lotus.webp',
    })
  })
})
