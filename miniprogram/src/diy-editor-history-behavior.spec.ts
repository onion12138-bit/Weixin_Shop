declare function require(name: string): any
export {}

jest.mock('../miniprogram/services/diy', () => ({
  createScheme: jest.fn(), updateScheme: jest.fn(), quoteDiy: jest.fn(),
  getDiyConfiguration: jest.fn(), getScheme: jest.fn(),
  yuanToCents: (value: number) => Math.round(value * 100),
}))
jest.mock('../miniprogram/services/catalog', () => ({
  listAllPublicBeads: jest.fn(), listCategories: jest.fn(),
}))
jest.mock('../miniprogram/services/commerce', () => ({ getCommerceRule: jest.fn() }))
jest.mock('../miniprogram/services/scheme-preview', () => ({
  hasPendingSchemePreview: jest.fn().mockReturnValue(false),
  queueSchemePreview: jest.fn(), retryPendingSchemePreviews: jest.fn(),
}))

const tap = (id: number) => ({ currentTarget: { dataset: { id } } })
const touch = (x: number) => ({ touches: [{ x, y: 0 }] })
function deferred() {
  let resolve!: (value: any) => void
  const promise = new Promise<any>((yes) => { resolve = yes })
  return { promise, resolve }
}
function load() {
  let definition: any
  const storage = new Map<string, string>()
  const wxMock = {
    getStorageSync: jest.fn((key) => storage.get(key) || ''),
    setStorageSync: jest.fn((key, value) => storage.set(key, value)),
    removeStorageSync: jest.fn((key) => storage.delete(key)),
    showModal: jest.fn((options) => options.success({ confirm: true })),
    showToast: jest.fn(), canvasToTempFilePath: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (value: any) => { definition = value }
  let diy: any; let catalog: any; let commerce: any; let previews: any
  jest.isolateModules(() => {
    diy = jest.requireMock('../miniprogram/services/diy')
    catalog = jest.requireMock('../miniprogram/services/catalog')
    commerce = jest.requireMock('../miniprogram/services/commerce')
    previews = jest.requireMock('../miniprogram/services/scheme-preview')
    require('../miniprogram/pages/diy/editor')
  })
  const config = {
    rule: { minWristMm: 120, maxWristMm: 220, maxBeadCount: 100,
      standardAllowanceMm: 10, snugAllowanceMm: 5, looseAllowanceMm: 15, toleranceMm: 5 },
    craftFeeRules: [], cordOptions: [{ id: 6001, name: '透明绳', extraFee: 0 }],
  }
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)), config,
    editorActive: true }
  page.setData = jest.fn((patch, done) => { Object.assign(page.data, patch); done?.() })
  page.drawBracelet = jest.fn()
  page.scheduleCanvasRedraw = jest.fn()
  page.schedulePreviewPreparation = jest.fn()
  page.hitTest = (x: number) => x
  page.data.loading = false
  page.data.catalog = [
    { id: 1, name: '白水晶', salePriceCents: 1000, diameterMm: 8 },
    { id: 2, name: '粉水晶', salePriceCents: 1300, diameterMm: 10 },
    { id: 3, name: '紫水晶', salePriceCents: 1600, diameterMm: 12 },
  ]
  diy.createScheme.mockResolvedValue({ id: 7, version: 1, name: '方案' })
  diy.updateScheme.mockResolvedValue({ id: 7, version: 2, name: '方案' })
  diy.getDiyConfiguration.mockResolvedValue(config)
  catalog.listAllPublicBeads.mockResolvedValue(page.data.catalog.map((item: any) => ({
    ...item, salePrice: item.salePriceCents / 100, material: '水晶', color: '白',
    coverUrl: null, diyVisual: null,
  })))
  catalog.listCategories.mockResolvedValue([])
  commerce.getCommerceRule.mockResolvedValue({ minimumOrderAmount: 20 })
  return { page, storage, diy, wxMock, previews, catalog }
}

describe('DIY replacement and history page behavior', () => {
  beforeEach(() => { jest.resetAllMocks(); jest.useFakeTimers() })
  afterEach(() => { jest.clearAllTimers(); jest.useRealTimers() })

  it('lets the user choose an exact replacement and inspect deltas before confirming', () => {
    const { page } = load()
    page.addCatalogBead(tap(1))
    page.replaceSelected()
    expect(page.data.sequence).toEqual([1])
    expect(page.data.replacementIndex).toBe(0)
    page.addCatalogBead(tap(3))
    expect(page.data.sequence).toEqual([1])
    expect(page.data.replacementChangeText).toContain('+¥6.00')
    expect(page.data.replacementChangeText).toContain('8 → 12 mm')
    page.confirmReplacement()
    expect(page.data.sequence).toEqual([3])
    expect(page.data.replacementIndex).toBe(-1)
    page.undoEdit()
    expect(page.data.sequence).toEqual([1])
    expect(page.data.selectedIndex).toBe(0)
    page.redoEdit()
    expect(page.data.sequence).toEqual([3])
  })

  it('cancels a candidate without editing or consuming an undo action', () => {
    const { page } = load()
    page.addCatalogBead(tap(1))
    page.replaceSelected()
    page.addCatalogBead(tap(2))
    page.cancelReplacement()
    page.undoEdit()
    expect(page.data.sequence).toEqual([])
    expect(page.data.canUndo).toBe(false)
  })

  it('replaces only the chosen occurrence and treats a cancelled drag as no edit', () => {
    const { page } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(1)); page.addCatalogBead(tap(1))
    page.onCanvasTouchStart(touch(1)); page.onCanvasTouchEnd()
    page.replaceSelected(); page.addCatalogBead(tap(2)); page.confirmReplacement()
    expect(page.data.sequence).toEqual([1, 2, 1])
    page.onCanvasTouchStart(touch(0)); page.onCanvasTouchMove(touch(2))
    page.onCanvasTouchCancel(); page.onCanvasTouchEnd()
    expect(page.data.sequence).toEqual([1, 2, 1])
    page.undoEdit()
    expect(page.data.sequence).toEqual([1, 1, 1])
  })

  it('records a drag once and clears redo when the user branches after undo', () => {
    const { page } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(2)); page.addCatalogBead(tap(3))
    page.onCanvasTouchStart(touch(0))
    page.onCanvasTouchMove(touch(1)); page.onCanvasTouchMove(touch(2))
    expect(page.data.sequence).toEqual([1, 2, 3])
    page.onCanvasTouchEnd()
    expect(page.data.sequence).toEqual([2, 3, 1])
    page.undoEdit()
    expect(page.data.sequence).toEqual([1, 2, 3])
    expect(page.data.selectedIndex).toBe(0)
    page.removeSelected()
    expect(page.data.sequence).toEqual([2, 3])
    expect(page.data.canRedo).toBe(false)
    page.redoEdit()
    expect(page.data.sequence).toEqual([2, 3])
  })

  it('treats same-place taps as selection and supports quantity plus configuration undo', () => {
    const { page } = load()
    page.addCatalogBead(tap(1))
    page.onCanvasTouchStart(touch(0)); page.onCanvasTouchEnd()
    page.increaseSelectedSku()
    page.decreaseSelectedSku()
    page.undoEdit()
    expect(page.data.sequence).toEqual([1, 1])
    page.undoEdit()
    expect(page.data.sequence).toEqual([1])
    page.changeFitType({ detail: { value: 2 } })
    expect(page.data.fitType).toBe('LOOSE')
    page.undoEdit()
    expect(page.data.fitType).toBe('STANDARD')
    page.undoEdit()
    expect(page.data.sequence).toEqual([])
  })

  it('limits history to fifty actions and does not add actions for rejected changes', () => {
    const { page } = load()
    for (let i = 0; i < 55; i += 1) page.addCatalogBead(tap(1))
    page.config.rule.maxBeadCount = 55
    page.addCatalogBead(tap(1))
    for (let i = 0; i < 50; i += 1) page.undoEdit()
    expect(page.data.sequence).toHaveLength(5)
    expect(page.data.canUndo).toBe(false)
  })

  it('keeps saved identity/version when undoing and persists the latest undo state on hide', async () => {
    const { page, storage, diy } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(2))
    await page.saveDraft()
    expect(storage.has('diy_editor_draft')).toBe(false)
    page.undoEdit()
    expect(page.data.schemeId).toBe(7)
    expect(page.data.schemeVersion).toBe(1)
    page.onHide()
    expect(JSON.parse(storage.get('diy_editor_draft')!).sequence).toEqual([1])
    await page.saveDraft()
    expect(diy.updateScheme).toHaveBeenCalledWith(7, expect.objectContaining({ version: 1, sequence: [1] }))
  })

  it('exports the saved snapshot when undo happens before delayed preview generation', async () => {
    const { page, previews } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(2))
    page.exportPreview = jest.fn().mockResolvedValue('/saved-preview.png')
    await page.saveDraft()
    page.undoEdit()
    const [schemeId, version, generate] = previews.queueSchemePreview.mock.calls[0]
    expect([schemeId, version]).toEqual([7, 1])
    await generate()
    expect(page.exportPreview).toHaveBeenCalledWith(expect.objectContaining({ sequence: [1, 2] }))
    expect(page.data.sequence).toEqual([1])
  })

  it('uses restored draft as a fresh history boundary and clears transient replacement', async () => {
    const { page, storage } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(2))
    page.onHide()
    const cached = JSON.parse(storage.get('diy_editor_draft')!)
    const next = load()
    next.storage.set('diy_editor_draft', JSON.stringify(cached))
    await next.page.bootstrap({})
    expect(next.page.data.sequence).toEqual([1, 2])
    expect(next.page.data.canUndo).toBe(false)
    next.page.data.loading = false // Initial canvas finished rendering.
    next.page.addCatalogBead(tap(3))
    expect(next.page.data.sequence).toEqual([1, 2, 3])
    next.page.undoEdit()
    expect(next.page.data.sequence).toEqual([1, 2])
    expect(next.page.data.canUndo).toBe(false)
  })

  it('ignores a quote and compact preview captured before undo, even before the next frame', async () => {
    const { page, diy, wxMock } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(2))
    const quote = deferred()
    diy.quoteDiy.mockReturnValue(quote.promise)
    const pendingQuote = page.confirmQuote()
    page.canvasNode = {}
    const pendingPreview = page.captureCompactPreview()
    const captureOptions = wxMock.canvasToTempFilePath.mock.calls[0][0]
    page.undoEdit()
    const priceAfterUndo = page.data.priceText
    captureOptions.success({ tempFilePath: '/stale.png' })
    quote.resolve({ quotable: true, totalAmount: 999, currentLengthMm: 999,
      targetLengthMm: 999, sizeStatus: 'FIT', issues: [] })
    await Promise.all([pendingQuote, pendingPreview])
    expect(page.data.sequence).toEqual([1])
    expect(page.data.priceText).toBe(priceAfterUndo)
    expect(page.data.authoritative).toBe(false)
    expect(page.data.compactPreviewUrl).not.toBe('/stale.png')
  })

  it('blocks undo and replacement mutation while a save is in flight', async () => {
    const { page, diy } = load()
    page.addCatalogBead(tap(1))
    page.replaceSelected(); page.addCatalogBead(tap(2))
    const save = deferred()
    diy.createScheme.mockReturnValue(save.promise)
    const pending = page.saveDraft()
    page.undoEdit(); page.confirmReplacement(); page.addCatalogBead(tap(3))
    expect(page.data.sequence).toEqual([1])
    save.resolve({ id: 7, version: 1, name: '方案' })
    await pending
  })

  it('allows a fresh quote after undo without an old completion resetting its loading state', async () => {
    const { page, diy } = load()
    page.addCatalogBead(tap(1)); page.addCatalogBead(tap(2))
    const oldQuote = deferred(); const newQuote = deferred()
    diy.quoteDiy.mockReturnValueOnce(oldQuote.promise).mockReturnValueOnce(newQuote.promise)
    const first = page.confirmQuote()
    page.undoEdit()
    const second = page.confirmQuote()
    oldQuote.resolve({ quotable: true, totalAmount: 999, issues: [] })
    await first
    expect(page.data.quoting).toBe(true)
    expect(page.data.authoritative).toBe(false)
    newQuote.resolve({ quotable: true, totalAmount: 10, currentLengthMm: 8,
      targetLengthMm: 170, sizeStatus: 'TOO_SHORT', issues: [] })
    await second
    expect(page.data.quoting).toBe(false)
    expect(page.data.authoritative).toBe(true)
    expect(page.data.priceText).toContain('10.00')
  })

  it('keeps unavailable saved beads editable but blocks adding, increasing and replacing into them', () => {
    const { page, wxMock } = load()
    const draft = page.schemeToDraft({ id: 9, version: 1, name: '旧方案', sequence: [99, 1],
      wristCircumferenceMm: 160, fitType: 'STANDARD', cordOptionId: 6001,
      catalogSnapshot: [{ skuId: 99, name: '已下架旧珠', diameterMm: 8, skuCode: 'OLD' }] })
    page.refreshDerivedState(draft.sequence, { authoritative: false, persistDraft: false, selectedIndex: 0 })
    expect(page.data.sequence).toEqual([99, 1])
    expect(page.data.materialWarnings.join('')).toContain('已下架旧珠')
    page.addCatalogBead(tap(99)); page.increaseSelectedSku()
    expect(page.data.sequence).toEqual([99, 1])
    expect(wxMock.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('下架') }))
    page.onCanvasTouchStart(touch(1)); page.onCanvasTouchEnd()
    page.replaceSelected(); page.addCatalogBead(tap(99)); page.confirmReplacement()
    expect(page.data.sequence).toEqual([99, 1])
    page.onCanvasTouchStart(touch(0)); page.onCanvasTouchEnd()
    page.replaceSelected(); page.addCatalogBead(tap(2)); page.confirmReplacement()
    expect(page.data.sequence).toEqual([2, 1])
    page.undoEdit()
    expect(page.data.sequence).toEqual([99, 1])
    page.removeSelected()
    expect(page.data.sequence).toEqual([1])
  })

  it('warns about a quantity shortage without silently changing the requested sequence', () => {
    const { page, wxMock } = load()
    page.data.catalog[0].availableQty = 1
    page.addCatalogBead(tap(1)); page.increaseSelectedSku()
    expect(page.data.sequence).toEqual([1, 1])
    expect(page.data.materialWarnings.join('')).toContain('需要 2 颗，当前可用 1 颗')
    expect(wxMock.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('库存不足') }))
    page.undoEdit()
    expect(page.data.materialWarnings).toEqual([])
    page.redoEdit()
    expect(page.data.materialWarnings.join('')).toContain('库存不足')
  })

  it('shows the proposed replacement shortage before confirmation and keeps unknown stock distinct from zero', () => {
    const { page } = load()
    page.data.catalog[1].availableQty = 0
    page.addCatalogBead(tap(1))
    expect(page.data.materialWarnings).toEqual([])
    page.replaceSelected(); page.addCatalogBead(tap(2))
    expect(page.data.sequence).toEqual([1])
    expect(page.data.replacementStockText).toContain('需要 1 颗，当前可用 0 颗')
    page.confirmReplacement()
    expect(page.data.sequence).toEqual([2])
    expect(page.data.materialWarnings.join('')).toContain('库存不足')
  })

  it('marks removed materials unavailable after a full catalog refresh but not a filtered search', async () => {
    const { page, catalog } = load()
    page.addCatalogBead(tap(1))
    catalog.listAllPublicBeads.mockResolvedValue([{ id: 2, name: '粉水晶', material: '水晶',
      color: '粉', salePrice: 13, diameterMm: 10, availableQty: 2, coverUrl: null, diyVisual: null }])
    page.data.categoryId = 100
    await page.searchCatalog()
    expect(page.data.catalog.find((item: any) => item.id === 1).unavailable).not.toBe(true)
    expect(page.data.catalog.find((item: any) => item.id === 2).availableQty).toBe(2)
    page.data.categoryId = 0
    await page.searchCatalog()
    page.increaseSelectedSku()
    expect(page.data.sequence).toEqual([1])
    expect(page.data.materialWarnings.join('')).toContain('下架')
  })
})
