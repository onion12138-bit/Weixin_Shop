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

function loadEditorPage(
  favoriteBeadIds: number[] = [],
  setupServices?: (services: {
    catalog: Record<string, jest.Mock>
    diy: Record<string, jest.Mock>
  }) => void,
) {
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
    createSelectorQuery: jest.fn(),
    getSystemInfoSync: jest.fn(() => ({ pixelRatio: 1 })),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (pageOptions: Record<string, any>) => {
    definition = pageOptions
  }

  jest.isolateModules(() => {
    const catalog = jest.requireMock('../miniprogram/services/catalog') as Record<
      string,
      jest.Mock
    >
    const diy = jest.requireMock('../miniprogram/services/diy') as Record<
      string,
      jest.Mock
    >
    setupServices?.({ catalog, diy })
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
  it('waits for the loaded view before querying canvas and recovers from a missing selector result', async () => {
    const { page, wxMock } = loadEditorPage([], ({ catalog, diy }) => {
      catalog.listBeads.mockResolvedValue({
        items: [], total: 0, page: 1, size: 30,
      })
      catalog.listCategories.mockResolvedValue([])
      diy.getDiyConfiguration.mockResolvedValue({
        rule: {
          minWristMm: 120,
          maxWristMm: 220,
          snugAllowanceMm: 5,
          standardAllowanceMm: 10,
          looseAllowanceMm: 15,
          toleranceMm: 5,
          maxBeadCount: 30,
          configVersion: 1,
        },
        craftFeeRules: [],
        cordOptions: [{
          id: 6001,
          code: 'CLEAR',
          name: '透明弹力绳',
          colorHex: '#e7e3db',
          extraFee: 0,
          sortOrder: 1,
          optionVersion: 1,
        }],
        visualConfig: {
          canvasBackgroundColor: '#fffdfd',
          braceletShadowOpacity: 0.14,
          beadShadowOpacity: 0.16,
          beadOverlapRatio: 0.08,
          configVersion: 1,
        },
      })
    })
    const selectorCallbacks: Array<(result: unknown) => void> = []
    const viewCallbacks: Array<() => void> = []
    const query: Record<string, jest.Mock> = {
      exec: jest.fn(),
      in: jest.fn(),
      select: jest.fn(),
    }
    const selection = {
      fields: jest.fn((_: unknown, callback: (result: unknown) => void) => {
        selectorCallbacks.push(callback)
        return query
      }),
    }
    query.in.mockReturnValue(query)
    query.select.mockReturnValue(selection)
    wxMock.createSelectorQuery.mockReturnValue(query)
    page.setData = jest.fn((patch: Record<string, unknown>, callback?: () => void) => {
      Object.assign(page.data, patch)
      if (callback) viewCallbacks.push(callback)
    })
    page.editorActive = true
    page.editorGeneration = 1

    page.onReady()
    expect(selectorCallbacks).toHaveLength(0)

    await page.bootstrap({})
    expect(page.data.loading).toBe(false)
    expect(selectorCallbacks).toHaveLength(0)
    expect(viewCallbacks).toHaveLength(1)

    viewCallbacks.shift()?.()
    expect(selectorCallbacks).toHaveLength(1)
    expect(page.canvasQueryPending).toBe(true)
    expect(() => selectorCallbacks[0](undefined)).not.toThrow()
    expect(page.canvasQueryPending).toBe(false)

    page.drawBracelet()
    expect(selectorCallbacks).toHaveLength(2)
  })

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

  it('uses an empty favorite list when storage reads fail', () => {
    const { page, wxMock } = loadEditorPage([2001])
    wxMock.getStorageSync.mockImplementation(() => {
      throw new Error('storage unavailable')
    })

    page.loadFavoriteBeads()

    expect(page.data.favoriteBeadIds).toEqual([])
  })

  it('keeps the favorite in view and notifies when storage writes fail', () => {
    const { page, wxMock } = loadEditorPage()
    page.data.catalogResults = [{ id: 2002, salePriceCents: 980 }]
    wxMock.setStorageSync.mockImplementation(() => {
      throw new Error('storage unavailable')
    })

    page.toggleFavorite({ currentTarget: { dataset: { id: 2002 } } })

    expect(page.data.favoriteBeadIds).toEqual([2002])
    expect(page.data.visibleCatalog[0]).toMatchObject({
      id: 2002,
      favorite: true,
    })
    expect(wxMock.showToast).toHaveBeenCalledWith({
      title: '喜欢记录未保存',
      icon: 'none',
    })
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

  it('combines category and keyword query results with the favorite-only filter', async () => {
    let listBeads: jest.Mock
    const beads = [
      {
        id: 2001,
        skuCode: 'BEAD-2001',
        name: '白水晶',
        material: '水晶',
        color: '透明',
        diameterMm: 8,
        salePrice: 6.8,
        coverUrl: null,
        diyVisual: null,
        tags: [],
      },
      {
        id: 2002,
        skuCode: 'BEAD-2002',
        name: '紫水晶',
        material: '水晶',
        color: '紫色',
        diameterMm: 8,
        salePrice: 9.8,
        coverUrl: null,
        diyVisual: null,
        tags: [],
      },
    ]
    const { page } = loadEditorPage([], ({ catalog }) => {
      listBeads = catalog.listBeads
      catalog.listBeads.mockResolvedValue({
        items: beads, total: 2, page: 1, size: 30,
      })
    })
    page.data.categoryId = 7
    page.data.keyword = '紫'
    page.data.favoriteBeadIds = [2002]
    page.data.favoritesOnly = true

    await page.searchCatalog()

    expect(listBeads!).toHaveBeenCalledWith({
      page: 1,
      size: 30,
      categoryId: 7,
      keyword: '紫',
    })
    expect(page.data.catalogResults.map((item: { id: number }) => item.id))
      .toEqual([2001, 2002])
    expect(page.data.visibleCatalog.map((item: { id: number }) => item.id))
      .toEqual([2002])
  })

  it('ignores a selector result started before a canvas reset', () => {
    const { page, wxMock } = loadEditorPage()
    const callbacks: Array<(result: unknown) => void> = []
    const query: Record<string, jest.Mock> = {
      exec: jest.fn(),
      in: jest.fn(),
      select: jest.fn(),
    }
    const node = {
      fields: jest.fn((_: unknown, callback: (result: unknown) => void) => {
        callbacks.push(callback)
        return query
      }),
    }
    query.in.mockReturnValue(query)
    query.select.mockReturnValue(node)
    wxMock.createSelectorQuery.mockReturnValue(query)
    const staleCanvas = { getContext: jest.fn(() => ({ scale: jest.fn() })) }
    const currentCanvas = { getContext: jest.fn(() => ({ scale: jest.fn() })) }
    page.editorActive = true
    page.editorGeneration = 1

    page.drawBracelet()
    page.resetCanvasForResize()
    page.drawBracelet()
    callbacks[0]({ node: staleCanvas, width: 300, height: 240 })

    expect(page.canvasNode).toBeNull()
    expect(page.canvasQueryPending).toBe(true)

    callbacks[1]({ node: currentCanvas, width: 320, height: 240 })
    expect(page.canvasNode).toBe(currentCanvas)
  })

  it('keeps the canonical catalog while search replaces the displayed results', async () => {
    const firstBead = {
      id: 2001,
      skuCode: 'BEAD-2001',
      name: '白水晶',
      material: '水晶',
      color: '透明',
      diameterMm: 8,
      salePrice: 6.8,
      coverUrl: 'https://shop.test/2001.png',
      diyVisual: null,
      tags: [],
    }
    const searchedBead = {
      ...firstBead,
      id: 2002,
      skuCode: 'BEAD-2002',
      name: '紫水晶',
      salePrice: 9.8,
    }
    const { page } = loadEditorPage([], ({ catalog, diy }) => {
      catalog.listBeads.mockResolvedValueOnce({
        items: [firstBead], total: 1, page: 1, size: 30,
      }).mockResolvedValueOnce({
        items: [searchedBead], total: 1, page: 1, size: 30,
      })
      catalog.listCategories.mockResolvedValue([])
      diy.getDiyConfiguration.mockResolvedValue({
        rule: {
          minWristMm: 120,
          maxWristMm: 220,
          snugAllowanceMm: 5,
          standardAllowanceMm: 10,
          looseAllowanceMm: 15,
          toleranceMm: 5,
          maxBeadCount: 30,
          configVersion: 1,
        },
        craftFeeRules: [],
        cordOptions: [],
        visualConfig: {},
      })
    })

    await page.bootstrap({})
    await page.searchCatalog()

    expect(page.data.catalog.map((item: { id: number }) => item.id))
      .toEqual([2001, 2002])
    expect(page.data.catalogResults.map((item: { id: number }) => item.id))
      .toEqual([2002])
    expect(page.data.visibleCatalog.map((item: { id: number }) => item.id))
      .toEqual([2002])
  })

  it('synchronizes the tray fallback into the display catalog', () => {
    const { page } = loadEditorPage()
    const item = {
      id: 2001,
      trayImageUrl: 'https://shop.test/tray.webp',
      coverImageUrl: 'https://shop.test/cover.png',
      visualVersion: 3,
      salePriceCents: 680,
    }
    page.data.catalog = [item]
    page.data.catalogResults = [{ ...item }]
    page.refreshVisibleCatalog()

    page.onTrayImageError({
      currentTarget: {
        dataset: {
          id: 2001,
          visualKey: '3:https://shop.test/tray.webp',
        },
      },
    })

    expect(page.data.catalog[0].trayImageUrl)
      .toBe('https://shop.test/cover.png')
    expect(page.data.visibleCatalog[0].trayImageUrl)
      .toBe('https://shop.test/cover.png')
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
