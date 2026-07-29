declare function require(name: string): unknown

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
  it.each([
    ['openDiy', '/pages/diy/editor'],
    ['openRecommendation', '/pages/recommendation/index'],
    ['openCatalog', '/pages/catalog/index'],
    ['openSchemes', '/pages/diy/schemes'],
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

  it('selects the mood card the user tapped', () => {
    const { page } = loadPage()

    page.selectMood({ currentTarget: { dataset: { moodId: 'energy' } } })

    expect(page.data.selectedMoodId).toBe('energy')
  })

  it('rotates the recommendation order and selects the new first mood', () => {
    const { page } = loadPage()
    const firstMood = page.data.moods[0]

    page.rotateMoods()

    expect(page.data.moods[page.data.moods.length - 1]).toEqual(firstMood)
    expect(page.data.selectedMoodId).toBe(page.data.moods[0].id)
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
