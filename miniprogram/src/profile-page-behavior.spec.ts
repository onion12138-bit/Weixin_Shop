declare function require(name: string): unknown

function loadPage() {
  let definition: Record<string, any> | null = null
  const wxMock = {
    navigateTo: jest.fn(),
    redirectTo: jest.fn(),
    pageScrollTo: jest.fn(),
    showToast: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }

  jest.isolateModules(() => {
    require('../miniprogram/pages/profile/index')
  })

  if (!definition) throw new Error('Page definition was not captured')
  const page = definition as Record<string, any>
  return { page, wxMock }
}

describe('profile Page behavior', () => {
  it.each([
    ['openRecommendation', '/pages/recommendation/index'],
    ['openDiy', '/pages/diy/editor'],
    ['openSchemes', '/pages/diy/schemes'],
    ['openOrders', '/pages/order/list'],
    ['openProfileEditor', '/pages/profile/edit'],
    ['openFavorites', '/pages/profile/favorites'],
    ['openAddresses', '/pages/profile/addresses'],
    ['openCoupons', '/pages/profile/coupons'],
  ])('%s opens the existing route', (method, url) => {
    const { page, wxMock } = loadPage()

    page[method]()

    expect(wxMock.navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({ url }),
    )
  })

  it('returns to the home page without stacking another profile page', () => {
    const { page, wxMock } = loadPage()

    page.openHome()

    expect(wxMock.redirectTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/pages/home/index' }),
    )
  })

  it('scrolls the active profile tab back to the top', () => {
    const { page, wxMock } = loadPage()

    page.scrollToTop()

    expect(wxMock.pageScrollTo).toHaveBeenCalledWith({
      scrollTop: 0,
      duration: 300,
    })
  })
})
