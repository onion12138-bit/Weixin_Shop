import { buildEditorSummary } from '../miniprogram/utils/diy-view-model'

declare function require(name: string): unknown

jest.mock('../miniprogram/services/catalog', () => ({
  getBead: jest.fn(),
  getPackage: jest.fn(),
  listAllPublicBeads: jest.fn(),
  listBeads: jest.fn(),
  listCategories: jest.fn(),
}))

jest.mock('../miniprogram/services/diy', () => ({
  createScheme: jest.fn(),
  getDiyConfiguration: jest.fn(),
  getScheme: jest.fn(),
  quoteDiy: jest.fn(),
  updateScheme: jest.fn(),
  uploadSchemePreview: jest.fn(),
  yuanToCents: (value: number) => Math.round(value * 100),
}))

function loadEditorPage(favoriteBeadIds: number[] = []) {
  let definition: Record<string, any> | null = null
  const storage = new Map<string, unknown>([
    ['diy_favorite_bead_ids', JSON.stringify(favoriteBeadIds)],
  ])
  const wxMock = {
    getStorageSync: jest.fn((key: string) => storage.get(key) || ''),
    setStorageSync: jest.fn((key: string, value: unknown) => {
      storage.set(key, value)
    }),
    showToast: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (pageOptions: Record<string, any>) => {
    definition = pageOptions
  }

  jest.isolateModules(() => {
    require('../miniprogram/pages/diy/editor')
  })
  if (!definition) throw new Error('Editor Page definition was not captured')
  const editorDefinition = definition as Record<string, any>
  const page: Record<string, any> = {
    ...editorDefinition,
    data: { ...editorDefinition.data },
  }
  page.setData = jest.fn((patch: Record<string, unknown>, callback?: () => void) => {
    Object.assign(page.data, patch)
    callback?.()
  })
  return { page, wxMock }
}

describe('DIY editor presentation behavior', () => {
  it('starts expanded and toggles preview with one redraw', () => {
    const { page } = loadEditorPage()
    page.drawBracelet = jest.fn()

    expect(page.data.previewExpanded).toBe(true)
    page.togglePreview()

    expect(page.data.previewExpanded).toBe(false)
    expect(page.drawBracelet).toHaveBeenCalledTimes(1)
  })

  it('loads, toggles and persists favorites without adding a bead', () => {
    const { page, wxMock } = loadEditorPage([2001])
    page.data.catalogResults = [{ id: 2001 }, { id: 2002 }]
    page.data.visibleCatalog = [...page.data.catalogResults]

    page.loadFavoriteBeads()
    page.toggleFavorite({ currentTarget: { dataset: { id: 2002 } } })

    expect(page.data.favoriteBeadIds).toEqual([2001, 2002])
    expect(page.data.sequence).toEqual([])
    expect(wxMock.setStorageSync).toHaveBeenCalledWith(
      'diy_favorite_bead_ids',
      JSON.stringify([2001, 2002]),
    )
  })

  it('filters current catalog results and restores them when disabled', () => {
    const { page } = loadEditorPage([2002])
    page.data.favoriteBeadIds = [2002]
    page.data.catalogResults = [
      { id: 2001, salePriceCents: 680 },
      { id: 2002, salePriceCents: 980 },
    ]

    page.toggleFavoriteFilter()
    expect(page.data.visibleCatalog.map((item: { id: number }) => item.id))
      .toEqual([2002])

    page.toggleFavoriteFilter()
    expect(page.data.visibleCatalog.map((item: { id: number }) => item.id))
      .toEqual([2001, 2002])
  })

  it('builds target and current length as separate lines', () => {
    expect(buildEditorSummary({
      beadCount: 5,
      currentLengthMm: 40,
      targetLengthMm: 185,
      sizeStatus: 'TOO_SHORT',
      estimatedPriceCents: 4460,
      authoritative: false,
    })).toMatchObject({
      targetLengthText: '目标 185 mm',
      currentLengthText: '当前 40 mm',
    })
  })
})
