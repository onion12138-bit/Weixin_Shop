declare function require(name: string): unknown
export {}

const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: string): string
}

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

jest.mock('../miniprogram/services/commerce', () => ({
  getCommerceRule: jest.fn(),
}))

const bead = {
  id: 2001,
  skuCode: 'BEAD-2001',
  name: '合法珠子',
  material: '水晶',
  color: '透明',
  diameterMm: 8,
  salePrice: 10,
  coverUrl: 'https://cdn.test/2001.png',
  tags: [],
}

const config = {
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
    code: 'ELASTIC-CLEAR',
    name: '透明弹力绳',
    colorHex: '#ffffff',
    extraFee: 0,
    sortOrder: 1,
    optionVersion: 1,
  }],
}

function instantiate(definition: Record<string, any>): Record<string, any> {
  const instance: Record<string, any> = {
    ...definition,
    data: { ...definition.data },
  }
  instance.setData = jest.fn((
    patch: Record<string, unknown>,
    callback?: () => void,
  ) => {
    Object.assign(instance.data, patch)
    callback?.()
  })
  return instance
}

function selectorQuery() {
  const query: Record<string, jest.Mock> = {
    exec: jest.fn(),
    select: jest.fn(),
  }
  const node = {
    fields: jest.fn((_: unknown, callback: (value: unknown) => void) => {
      callback({ width: 300, height: 300 })
      return query
    }),
  }
  query.select.mockReturnValue(node)
  return query
}

function loadEditor(options: { removeThrows?: boolean } = {}) {
  let definition: Record<string, any> | null = null
  let services: { catalog: any; diy: any } | null = null
  const storage = new Map<string, unknown>()
  const wxMock = {
    getStorageSync: jest.fn((key: string) => storage.get(key) || ''),
    setStorageSync: jest.fn((key: string, value: unknown) => {
      storage.set(key, value)
    }),
    removeStorageSync: jest.fn((key: string) => {
      if (options.removeThrows) throw new Error('cannot remove storage')
      storage.delete(key)
    }),
    createSelectorQuery: jest.fn(() => selectorQuery()),
    showModal: jest.fn(),
    showToast: jest.fn(),
    navigateTo: jest.fn(),
    getSystemInfoSync: jest.fn(() => ({ pixelRatio: 1 })),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (pageOptions: Record<string, any>) => {
    definition = pageOptions
  }

  jest.isolateModules(() => {
    const catalog = jest.requireMock('../miniprogram/services/catalog') as {
      listAllPublicBeads: jest.Mock
      listBeads: jest.Mock
      listCategories: jest.Mock
    }
    const diy = jest.requireMock('../miniprogram/services/diy') as {
      getDiyConfiguration: jest.Mock
      getScheme: jest.Mock
    }
    const commerce = jest.requireMock('../miniprogram/services/commerce') as {
      getCommerceRule: jest.Mock
    }
    services = { catalog, diy }
    catalog.listAllPublicBeads.mockReset()
    catalog.listBeads.mockReset()
    catalog.listCategories.mockReset()
    diy.getDiyConfiguration.mockReset()
    diy.getScheme.mockReset()
    commerce.getCommerceRule.mockReset()
    catalog.listAllPublicBeads.mockResolvedValue([bead])
    catalog.listBeads.mockResolvedValue({
      items: [bead], total: 1, page: 1, size: 30,
    })
    catalog.listCategories.mockResolvedValue([])
    diy.getDiyConfiguration.mockResolvedValue(config)
    commerce.getCommerceRule.mockResolvedValue({
      minimumOrderAmount: 20,
      freeShippingThreshold: 60,
      baseShippingFee: 8,
      configVersion: 1,
    })
    diy.getScheme.mockResolvedValue({
      id: 91,
      name: '之前保存的设计',
      wristCircumferenceMm: 160,
      fitType: 'STANDARD',
      cordOptionId: 6001,
      sequence: [2001],
      beadCount: 1,
      catalogSnapshot: [{
        skuId: 2001,
        skuCode: 'BEAD-2001',
        name: '合法珠子',
        color: '透明',
        diameterMm: 8,
        coverUrl: 'https://cdn.test/2001.png',
        status: 'ON_SALE',
        diyVisual: null,
      }],
      quoteStatus: 'VALID',
      previewAssetId: null,
      previewUrl: null,
      version: 1,
      createdAt: '2026-08-01T00:00:00',
      updatedAt: '2026-08-01T00:00:00',
    })
    require('../miniprogram/pages/diy/editor')
  })
  if (!definition) throw new Error('Editor Page definition was not captured')
  return { definition, storage, wxMock, services: services! }
}

async function flushMicrotasks(): Promise<void> {
  for (let index = 0; index < 12; index += 1) await Promise.resolve()
}

describe('DIY editor audit regressions', () => {
  it('does not update an unloaded editor or ask to restore its draft after slow bootstrap', async () => {
    const { definition, services, wxMock } = loadEditor()
    const page = instantiate(definition)
    let finish!: (beads: unknown[]) => void
    services.catalog.listAllPublicBeads.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    page.resolveInitialDraft = jest.fn()
    const pending = page.bootstrap({})
    page.onUnload()
    page.setData.mockClear()
    finish([bead])
    await pending
    expect(page.setData).not.toHaveBeenCalled()
    expect(page.resolveInitialDraft).not.toHaveBeenCalled()
    expect(wxMock.showModal).not.toHaveBeenCalled()
  })

  it('keeps the saved design stable while preview export is pending', () => {
    const { definition } = loadEditor()
    const page = instantiate(definition)
    page.setData({ saving: true, sequence: [2001], selectedIndex: 0 })
    page.addCatalogBead({ currentTarget: { dataset: { id: 2001 } } })
    expect(page.data.sequence).toEqual([2001])
  })

  it.each(['changeWrist', 'changeFitType', 'changeCord'])('ignores %s during checkout save', (method) => {
    const { definition } = loadEditor()
    const page = instantiate(definition)
    page.setData({ checkingOut: true, wristOptions: [170], cordOptions: [{ id: 7002 }] })
    const draft = page.currentDraft()
    page[method]({ detail: { value: '0' } })
    expect(page.currentDraft()).toEqual(draft)
  })

  it('stops adding beads at the configured design limit', () => {
    const { definition } = loadEditor()
    const page = instantiate(definition)
    page.config = { ...config, rule: { ...config.rule, maxBeadCount: 2 } }
    page.setData({ sequence: [2001, 2001], selectedIndex: 1 })
    page.addCatalogBead({ currentTarget: { dataset: { id: 2001 } } })
    expect(page.data.sequence).toEqual([2001, 2001])
  })

  it('uses the current cord options when starting a blank design', async () => {
    const { definition, services: { diy } } = loadEditor()
    const page = instantiate(definition)
    diy.getDiyConfiguration.mockResolvedValue({ ...config, cordOptions: [{ ...config.cordOptions[0], id: 7002 }] })
    await page.bootstrap({})
    expect(page.data.cordOptionId).toBe(7002)
    expect(page.currentCord()?.id).toBe(7002)
  })

  it('marks an initial loading failure separately from a recoverable save error', async () => {
    const { definition, services: { diy } } = loadEditor()
    const page = instantiate(definition)
    diy.getDiyConfiguration.mockRejectedValue(new Error('加载失败'))
    await page.bootstrap({})
    expect(page.data.loadFailed).toBe(true)
    expect(page.data.loading).toBe(false)
  })

  it('retains live bead prices when restoring a saved scheme', async () => {
    const { definition } = loadEditor()
    const page = instantiate(definition)
    await page.bootstrap({ schemeId: '91' })

    expect(page.currentEstimateCents()).toBe(1000)
    expect(page.data.priceText).toBe('预估 ¥10.00')
  })

  it('loads the newest category even while another category is pending', async () => {
    const { definition, services: { catalog } } = loadEditor()
    const page = instantiate(definition)
    let finishOld!: (value: any) => void
    catalog.listAllPublicBeads.mockImplementationOnce(() => new Promise((resolve) => {
      finishOld = resolve
    }))
    const oldRequest = page.searchCatalog()
    catalog.listAllPublicBeads.mockResolvedValueOnce([{ ...bead, id: 2002, name: '新分类珠子' }])

    await page.chooseCategory({ currentTarget: { dataset: { id: 3 } } })
    finishOld([bead])
    await oldRequest

    expect(page.data.visibleCatalog.map((item: any) => item.id)).toEqual([2002])
  })

  it('discards a quote if the design changes while it is pending', async () => {
    const { definition, services: { diy } } = loadEditor()
    const page = instantiate(definition)
    let finishQuote!: (value: any) => void
    diy.quoteDiy.mockImplementationOnce(() => new Promise((resolve) => { finishQuote = resolve }))
    page.setData({ sequence: [2001] })
    const pending = page.confirmQuote()
    page.refreshDerivedState([2001, 2001], { authoritative: false, persistDraft: false })
    finishQuote({ quotable: true, totalAmount: 10, issues: [], currentLengthMm: 8, targetLengthMm: 170, sizeStatus: 'TOO_SHORT' })
    await pending

    expect(page.data.authoritative).toBe(false)
    expect(page.data.countText).toBe('2 颗')
  })

  it('does not save another scheme while checkout is already saving it', async () => {
    const { definition, services: { diy } } = loadEditor()
    const page = instantiate(definition)
    diy.createScheme.mockClear()
    page.setData({ sequence: [2001], checkingOut: true })

    await page.saveDraft()

    expect(diy.createScheme).not.toHaveBeenCalled()
    expect(page.data.errorMessage).toBe('')
  })
})

const validHandoff = {
  sequence: [2001],
}

describe('DIY editor AI handoff behavior', () => {
  it('refuses raw handoff when persistent removal throws', async () => {
    const { definition, storage, wxMock } = loadEditor({ removeThrows: true })
    storage.set('pending_ai_recommendation', validHandoff)
    const page = instantiate(definition)

    page.onLoad({ source: 'ai' })
    await flushMicrotasks()

    expect(page.data.loading).toBe(false)
    expect(page.data.sequence).toEqual([])
    expect(page.pendingAiRecommendationRaw).toBeNull()
    expect(wxMock.setStorageSync)
      .toHaveBeenCalledWith('pending_ai_recommendation', '')
  })

  it('consumes one valid handoff once across repeated source=ai opens', async () => {
    const { definition, storage, wxMock } = loadEditor()
    storage.set('pending_ai_recommendation', validHandoff)
    const firstPage = instantiate(definition)

    firstPage.onLoad({ source: 'ai' })
    await flushMicrotasks()
    expect(firstPage.data.sequence).toEqual([2001])
    expect(firstPage.data.wristCircumferenceMm).toBe(160)
    expect(firstPage.data.fitType).toBe('STANDARD')
    expect(firstPage.data.cordOptionId).toBe(6001)

    const secondPage = instantiate(definition)
    secondPage.onLoad({ source: 'ai' })
    await flushMicrotasks()

    expect(secondPage.data.sequence).toEqual([])
    expect(wxMock.showModal).not.toHaveBeenCalled()
    expect(wxMock.getStorageSync)
      .toHaveBeenCalledWith('pending_ai_recommendation')
  })

  it('mounts a saved design canvas beneath the loading overlay', async () => {
    const { definition, wxMock } = loadEditor()
    const page = instantiate(definition)

    page.onLoad({ schemeId: '91' })
    await flushMicrotasks()

    expect(page.data.sequence).toEqual([2001])
    expect(page.data.loading).toBe(false)
    expect(wxMock.createSelectorQuery).toHaveBeenCalledTimes(1)
  })

  it('keeps the bead operation bar visible and inert until a bead is selected', async () => {
    const { definition } = loadEditor()
    const page = instantiate(definition)
    page.refreshDerivedState = jest.fn()

    page.onLoad({ schemeId: '91' })
    await flushMicrotasks()
    page.refreshDerivedState.mockClear()
    page.decreaseSelectedSku()

    expect(page.data.selectedIndex).toBe(-1)
    expect(page.data.selectedNumber).toBe(0)
    expect(page.refreshDerivedState).not.toHaveBeenCalled()

    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    expect(template).not.toContain(
      '<view wx:if="{{selectedIndex >= 0}}" class="selected-actions">',
    )
    expect(template).toContain('disabled="{{selectedIndex < 0 || saving || checkingOut}}"')
  })

  it('loads the bracelet and catalog independently without showing canvas placeholders', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    const styles = readFileSync('miniprogram/pages/diy/editor.wxss', 'utf8')

    expect(template).toContain(
      '<view wx:if="{{loading}}" class="section-loading board-loading">',
    )
    expect(template).toContain(
      '<view wx:if="{{catalogInitialLoading}}" class="section-loading catalog-loading">',
    )
    expect(template).toContain(
      '<block wx:if="{{!catalogInitialLoading}}">',
    )
    expect(template).toContain('<text>加载中...</text>')
    expect(template).not.toContain('class="loading-overlay"')
    expect(styles).toMatch(
      /\.canvas-panel\.is-loading \.bracelet-canvas,[\s\S]*?visibility:\s*hidden;/,
    )
  })
})
