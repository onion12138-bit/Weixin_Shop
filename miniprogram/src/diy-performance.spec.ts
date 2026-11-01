declare function require(name: string): any
export {}

jest.mock('../miniprogram/services/diy', () => ({
  createScheme: jest.fn(), updateScheme: jest.fn(), uploadSchemePreview: jest.fn(),
  yuanToCents: (amount: number) => Math.round(amount * 100),
}))

async function flush() { for (let i = 0; i < 15; i += 1) await Promise.resolve() }
function deferred() {
  let resolve!: (value: any) => void
  let reject!: (error: Error) => void
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function load() {
  const storage = new Map<string, string>()
  const context = () => ({
    save: jest.fn(), restore: jest.fn(), translate: jest.fn(), scale: jest.fn(),
    beginPath: jest.fn(), arc: jest.fn(), fillRect: jest.fn(), fill: jest.fn(),
    stroke: jest.fn(), drawImage: jest.fn(),
  })
  const ctx = context()
  const canvas = { width: 300, height: 300, getContext: () => ctx }
  const exports: any[] = []
  const wxMock = {
    getStorageSync: jest.fn((key) => storage.get(key) || ''),
    setStorageSync: jest.fn((key, value) => storage.set(key, value)),
    removeStorageSync: jest.fn((key) => storage.delete(key)),
    showToast: jest.fn(), navigateTo: jest.fn(),
    createOffscreenCanvas: jest.fn<any, []>(() => {
      const offscreen = { width: 620, height: 620, getContext: () => context() }
      exports.push(offscreen)
      return offscreen
    }),
    canvasToTempFilePath: jest.fn((options) => options.success({ tempFilePath: '/tmp/preview.png' })),
  }
  ;(globalThis as any).wx = wxMock
  let definition: any; let diy: any; let previews: any
  ;(globalThis as any).Page = (value: any) => { definition = value }
  jest.isolateModules(() => {
    diy = jest.requireMock('../miniprogram/services/diy')
    diy.createScheme.mockResolvedValue({ id: 7, version: 0, name: '方案' })
    diy.updateScheme.mockResolvedValue({ id: 7, version: 1, name: '方案' })
    diy.uploadSchemePreview.mockResolvedValue({ id: 7, version: 0, previewUrl: '/preview.png' })
    previews = require('../miniprogram/services/scheme-preview')
    require('../miniprogram/pages/diy/editor')
  })
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)),
    canvasNode: canvas, canvasContext: ctx, canvasLogicalWidth: 300, canvasLogicalHeight: 300,
    canvasImages: {}, canvasImageQueue: [], editorActive: true }
  page.setData = jest.fn((patch, done) => { Object.assign(page.data, patch); done?.() })
  page.config = { rule: { maxBeadCount: 100, standardAllowanceMm: 10, toleranceMm: 5 }, craftFeeRules: [],
    visualConfig: { canvasBackgroundColor: '#ffffff', braceletShadowOpacity: 0.1,
      beadShadowOpacity: 0.2, beadOverlapRatio: 0.06, configVersion: 1 } }
  page.setData({ loading: false, minimumOrderAmountCents: 0, catalog: [{ id: 1, name: '珠子',
    diameterMm: 8, salePriceCents: 1000, colorHex: '#ffffff', canvasImageUrl: '', coverImageUrl: '', visualVersion: 1 }] })
  return { page, wxMock, storage, diy, ctx, exports, previews }
}

const tap = { currentTarget: { dataset: { id: 1 } } }

describe('DIY interaction and asynchronous preview performance', () => {
  beforeEach(() => { jest.resetAllMocks(); jest.useFakeTimers() })
  afterEach(() => { jest.clearAllTimers(); jest.useRealTimers() })

  it('applies 20 clicks immediately but draws one frame and writes one latest draft', async () => {
    const { page, ctx, wxMock, storage } = load()
    for (let i = 0; i < 20; i += 1) page.addCatalogBead(tap)
    expect(page.data.sequence).toHaveLength(20)
    expect(page.data.countText).toBe('20 颗')
    expect(ctx.fillRect).not.toHaveBeenCalled()
    expect(wxMock.setStorageSync).not.toHaveBeenCalled()
    jest.advanceTimersByTime(16)
    expect(ctx.fillRect).toHaveBeenCalledTimes(1)
    expect(wxMock.canvasToTempFilePath).not.toHaveBeenCalled()
    jest.advanceTimersByTime(150)
    await flush()
    expect(wxMock.canvasToTempFilePath).toHaveBeenCalledTimes(1)
    jest.advanceTimersByTime(84)
    expect(wxMock.setStorageSync.mock.calls.filter(([key]) => key === 'diy_editor_draft')).toHaveLength(1)
    expect(JSON.parse(storage.get('diy_editor_draft')!).sequence).toHaveLength(20)
  })

  it.each(['onHide', 'onUnload'])('flushes the latest draft on %s and cancels delayed work', (lifecycle) => {
    const { page, ctx, storage } = load()
    page.addCatalogBead(tap)
    page.addCatalogBead(tap)
    page[lifecycle]()
    expect(JSON.parse(storage.get('diy_editor_draft')!).sequence).toEqual([1, 1])
    expect(jest.getTimerCount()).toBe(0)
    jest.advanceTimersByTime(1000)
    expect(ctx.fillRect).not.toHaveBeenCalled()
  })

  it('keeps editing responsive when local draft storage fails and retries on hide', () => {
    const { page, wxMock, storage } = load()
    wxMock.setStorageSync.mockImplementationOnce(() => { throw new Error('quota') })
    page.addCatalogBead(tap)
    expect(() => jest.advanceTimersByTime(250)).not.toThrow()
    page.addCatalogBead(tap)
    page.onHide()
    expect(JSON.parse(storage.get('diy_editor_draft')!).sequence).toEqual([1, 1])
  })

  it.each(['saveDraft', 'goCheckout'])('%s finishes before preview generation and upload finish', async (action) => {
    const { page, wxMock, diy, storage } = load()
    const pendingUpload = deferred()
    diy.uploadSchemePreview.mockReturnValueOnce(pendingUpload.promise)
    page.addCatalogBead(tap)
    await page[action]()
    expect(page.data.saving).toBe(false)
    expect(page.data.checkingOut).toBe(false)
    expect(diy.uploadSchemePreview).not.toHaveBeenCalled()
    if (action === 'goCheckout') expect(wxMock.navigateTo).toHaveBeenCalledTimes(1)
    expect(storage.has('diy_editor_draft')).toBe(false)
    jest.advanceTimersByTime(0)
    await flush()
    expect(diy.uploadSchemePreview).toHaveBeenCalledWith(7, '/tmp/preview.png', 0)
    jest.advanceTimersByTime(250)
    expect(storage.has('diy_editor_draft')).toBe(false)
    pendingUpload.resolve({ id: 7, version: 0 })
    await flush()
  })

  it('reuses an idle prepared export for saving an unchanged design', async () => {
    const { page, wxMock } = load()
    page.addCatalogBead(tap)
    jest.advanceTimersByTime(600)
    await flush()
    expect(wxMock.createOffscreenCanvas).toHaveBeenCalledTimes(1)
    await page.saveDraft()
    jest.advanceTimersByTime(0)
    await flush()
    expect(wxMock.createOffscreenCanvas).toHaveBeenCalledTimes(1)
  })

  it('saves a new design while an old preview uploads without using a preview response version', async () => {
    const { page, diy } = load()
    const oldUpload = deferred()
    diy.uploadSchemePreview.mockReturnValueOnce(oldUpload.promise)
    page.addCatalogBead(tap)
    await page.saveDraft()
    jest.advanceTimersByTime(0)
    await flush()
    page.addCatalogBead(tap)
    await page.saveDraft()
    expect(diy.updateScheme).toHaveBeenCalledWith(7, expect.objectContaining({ version: 0, sequence: [1, 1] }))
    oldUpload.resolve({ id: 7, version: 99 })
    await flush()
    expect(page.data.schemeVersion).toBe(1)
  })

  it('does not clear a newer page draft or navigate after leaving during a save', async () => {
    const { page, diy, wxMock, storage } = load()
    const save = deferred()
    diy.createScheme.mockReturnValueOnce(save.promise)
    page.addCatalogBead(tap)
    const pending = page.goCheckout()
    page.onUnload()
    const callsAtUnload = page.setData.mock.calls.length
    storage.set('diy_editor_draft', JSON.stringify({ sequence: [2], name: '另一页的新草稿' }))
    save.resolve({ id: 7, version: 0, name: '方案' })
    await pending
    expect(JSON.parse(storage.get('diy_editor_draft')!).sequence).toEqual([2])
    expect(page.setData).toHaveBeenCalledTimes(callsAtUnload)
    expect(wxMock.navigateTo).not.toHaveBeenCalled()
  })

  it('exports the saved snapshot even if editing resumes while export images are loading', async () => {
    const { page, wxMock, diy } = load()
    const images: any[] = []
    const arcs: any[] = []
    const drawImage = jest.fn()
    wxMock.createOffscreenCanvas.mockImplementation(() => ({
      getContext: () => ({ save() {}, restore() {}, translate() {}, scale() {}, beginPath() {},
        arc(...args: unknown[]) { arcs.push(args) }, fillRect() {}, fill() {}, stroke() {}, drawImage }),
      createImage: () => { const image = { src: '', onload() {}, onerror() {} }; images.push(image); return image },
    }))
    page.data.catalog[0].canvasImageUrl = '/bead.png'
    page.addCatalogBead(tap)
    await page.saveDraft()
    jest.advanceTimersByTime(0)
    await flush()
    expect(images).toHaveLength(1)
    page.addCatalogBead(tap)
    images[0].onload()
    await flush()
    expect(diy.uploadSchemePreview).toHaveBeenCalledWith(7, '/tmp/preview.png', 0)
    // 一颗珠子的保存快照；实际编辑器已经包含两颗，不能参与导出。
    expect(arcs.length).toBeGreaterThan(0)
    expect(drawImage).toHaveBeenCalledTimes(1)
    expect(page.data.sequence).toEqual([1, 1])
    expect(images).toHaveLength(1)
  })
})
