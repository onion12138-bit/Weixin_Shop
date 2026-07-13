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
  instance.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(instance.data, patch)
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
    }
    catalog.listAllPublicBeads.mockReset()
    catalog.listBeads.mockReset()
    catalog.listCategories.mockReset()
    diy.getDiyConfiguration.mockReset()
    catalog.listAllPublicBeads.mockResolvedValue([bead])
    catalog.listBeads.mockResolvedValue({
      items: [bead], total: 1, page: 1, size: 30,
    })
    catalog.listCategories.mockResolvedValue([])
    diy.getDiyConfiguration.mockResolvedValue(config)
    require('../miniprogram/pages/diy/editor')
  })
  if (!definition) throw new Error('Editor Page definition was not captured')
  return { definition, storage, wxMock }
}

async function flushMicrotasks(): Promise<void> {
  for (let index = 0; index < 12; index += 1) await Promise.resolve()
}

const validHandoff = {
  sequence: [2001],
  wristCircumferenceMm: 165,
  fitType: 'STANDARD',
  cordOptionId: 6001,
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

    const secondPage = instantiate(definition)
    secondPage.onLoad({ source: 'ai' })
    await flushMicrotasks()

    expect(secondPage.data.sequence).toEqual([])
    expect(wxMock.showModal).not.toHaveBeenCalled()
    expect(wxMock.getStorageSync)
      .toHaveBeenCalledWith('pending_ai_recommendation')
  })
})
