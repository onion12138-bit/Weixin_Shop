import {
  getBead,
  getPackage,
  listAllPublicBeads,
  listBeads,
  listCategories,
  type Category,
} from '../../services/catalog'
import {
  createScheme,
  getDiyConfiguration,
  getScheme,
  quoteDiy,
  updateScheme,
  uploadSchemePreview,
  yuanToCents,
  type DiyConfiguration,
  type DiyQuoteResponse,
  type DiySchemeResponse,
  type FitType,
  type SizeStatus,
} from '../../services/diy'
import {
  addBead,
  adjustSkuQuantity,
  moveBead,
  removeBead,
  replaceBead,
} from '../../domain/editor-state'
import {
  createHistory,
  type EditorHistory,
} from '../../domain/editor-history'
import {
  PENDING_AI_RECOMMENDATION_KEY,
  consumePendingAiRecommendation,
} from '../../domain/recommendation-profile'
import { layoutBracelet } from '../../domain/bracelet-layout'
import { estimatePriceCents } from '../../domain/price-estimate'
import type {
  BeadLayoutPoint,
  BeadSequence,
} from '../../domain/diy-types'
import {
  buildEditorSummary,
  restorePackageSequence,
  type EditorSummary,
} from '../../utils/diy-view-model'

interface CatalogItem {
  id: number
  name: string
  material: string
  color: string
  diameterMm: number
  salePriceCents: number
  imageUrl: string
  colorHex: string
}

interface CordOptionItem {
  id: number
  name: string
  colorHex: string
  extraFeeCents: number
}

interface QuoteIssueItem {
  code: string
  message: string
  skuId: number | null
}

interface DraftCache {
  schemeId: number
  schemeVersion: number
  name: string
  wristCircumferenceMm: number
  fitType: FitType
  cordOptionId: number
  sequence: number[]
  selectedIndex: number
}

interface CanvasNode {
  width: number
  height: number
  getContext(type: '2d'): CanvasRenderingContext
  createImage?(): CanvasImage
}

interface CanvasImage {
  src: string
  onload: () => void
  onerror: () => void
}

interface CanvasRenderingContext {
  clearRect(x: number, y: number, width: number, height: number): void
  beginPath(): void
  arc(
    x: number,
    y: number,
    radius: number,
    startAngle: number,
    endAngle: number,
  ): void
  stroke(): void
  fill(): void
  fillText(text: string, x: number, y: number): void
  drawImage(
    image: CanvasImage,
    x: number,
    y: number,
    width: number,
    height: number,
  ): void
  scale(x: number, y: number): void
  lineWidth: number
  strokeStyle: string
  fillStyle: string
  font: string
  textAlign: 'center' | 'left' | 'right'
  textBaseline: 'middle' | 'top' | 'bottom'
}

const DRAFT_CACHE_KEY = 'diy_editor_draft'
const PREVIEW_PENDING_KEY = 'diy_preview_pending'
const DEFAULT_NAME = '我的手串'
const FIT_TYPES: FitType[] = ['SNUG', 'STANDARD', 'LOOSE']
const FIT_TYPE_LABELS = ['贴手', '标准', '宽松']

Page({
  data: {
    loading: true,
    saving: false,
    quoting: false,
    schemeId: 0,
    schemeVersion: 0,
    name: DEFAULT_NAME,
    wristCircumferenceMm: 160,
    wristCircumferenceText: '16.0 cm',
    wristOptions: [] as number[],
    wristOptionLabels: [] as string[],
    wristOptionIndex: 0,
    fitType: 'STANDARD' as FitType,
    fitTypeLabels: FIT_TYPE_LABELS,
    fitTypeIndex: 1,
    cordOptionId: 6001,
    cordOptions: [] as CordOptionItem[],
    cordOptionNames: [] as string[],
    cordOptionIndex: 0,
    sequence: [] as number[],
    selectedIndex: -1,
    selectedNumber: 0,
    catalog: [] as CatalogItem[],
    categories: [] as Category[],
    categoryId: 0,
    keyword: '',
    countText: '0 颗',
    lengthText: '尚未选珠',
    sizeText: '请选择珠子',
    priceText: '预估 ¥0.00',
    priceBadge: '预估',
    quoteIssues: [] as QuoteIssueItem[],
    canUndo: false,
    canRedo: false,
    authoritative: false,
    errorMessage: '',
  },

  /**
   * 编辑器只把“有序 SKU 数组”作为设计状态。
   * 临时序号、数量统计、预估价格和 Canvas 坐标全部由该数组派生，避免多份状态冲突。
   */
  history: createHistory<number[]>([]),
  config: null as DiyConfiguration | null,
  layoutPoints: [] as BeadLayoutPoint[],
  canvasNode: null as CanvasNode | null,
  canvasImages: {} as Record<string, CanvasImage | 'loading' | 'error'>,
  dragFromIndex: -1,
  dragToIndex: -1,
  pendingAiRecommendationRaw: null as unknown,

  onLoad(options: Record<string, string>) {
    if (options.source === 'ai') {
      try {
        this.pendingAiRecommendationRaw = wx.getStorageSync(
          PENDING_AI_RECOMMENDATION_KEY,
        )
      } catch {
        this.pendingAiRecommendationRaw = null
      } finally {
        try {
          wx.removeStorageSync(PENDING_AI_RECOMMENDATION_KEY)
        } catch {
          // Keep loading; unreadable persisted data must never block the editor.
        }
      }
    }
    void this.bootstrap(options)
  },

  async bootstrap(options: Record<string, string>) {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const beadsPromise = options.source === 'ai'
        ? listAllPublicBeads()
        : listBeads({ page: 1, size: 30 }).then((page) => page.items)
      const [config, categories, beads] = await Promise.all([
        getDiyConfiguration(),
        listCategories(),
        beadsPromise,
      ])
      this.config = config
      const catalog = beads.map(toCatalogItem)
      const cordOptions = config.cordOptions.map(toCordOptionItem)
      const wristOptions = buildWristOptions(
        config.rule.minWristMm,
        config.rule.maxWristMm,
      )

      this.setData({
        categories,
        catalog,
        cordOptions,
        cordOptionNames: cordOptions.map((item) => item.name),
        cordOptionId: cordOptions[0]?.id || this.data.cordOptionId,
        cordOptionIndex: 0,
        wristOptions,
        wristOptionLabels: wristOptions.map(formatWristLabel),
        wristOptionIndex: closestIndex(wristOptions, this.data.wristCircumferenceMm),
      })

      const initial = await this.resolveInitialDraft(options)
      this.history = createHistory(initial.sequence)
      this.setData({
        schemeId: initial.schemeId,
        schemeVersion: initial.schemeVersion,
        name: initial.name,
        wristCircumferenceMm: initial.wristCircumferenceMm,
        wristCircumferenceText: formatWristLabel(initial.wristCircumferenceMm),
        wristOptionIndex: closestIndex(wristOptions, initial.wristCircumferenceMm),
        fitType: initial.fitType,
        fitTypeIndex: Math.max(0, FIT_TYPES.indexOf(initial.fitType)),
        cordOptionId: initial.cordOptionId,
        cordOptionIndex: Math.max(
          0,
          cordOptions.findIndex((item) => item.id === initial.cordOptionId),
        ),
        selectedIndex: normalizeSelectedIndex(
          initial.selectedIndex,
          initial.sequence,
        ),
      })
      this.refreshDerivedState(initial.sequence, {
        authoritative: false,
        persistDraft: false,
        pushHistory: false,
      })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error
          ? error.message
          : 'DIY 设计器加载失败，请稍后重试',
      })
    } finally {
      this.setData({ loading: false })
      this.drawBracelet()
    }
  },

  async resolveInitialDraft(
    options: Record<string, string>,
  ): Promise<DraftCache> {
    if (options.source === 'ai') {
      const config = this.config
      const aiDraft = consumePendingAiRecommendation(
        options.source,
        {
          get: () => this.pendingAiRecommendationRaw,
          remove: () => {
            this.pendingAiRecommendationRaw = null
          },
        },
        {
          allowedSkuIds: this.data.catalog.map((item) => item.id),
          allowedCordOptionIds: this.data.cordOptions.map((item) => item.id),
          minWristCircumferenceMm: config?.rule.minWristMm || 1,
          maxWristCircumferenceMm: config?.rule.maxWristMm || 1000,
          maxSequenceLength: config?.rule.maxBeadCount || 200,
        },
      )
      return aiDraft || blankDraft()
    }

    const schemeId = Number(options.schemeId || 0)
    const packageId = Number(options.packageId || 0)
    const skuId = Number(options.skuId || 0)

    if (schemeId > 0) {
      return this.schemeToDraft(await getScheme(schemeId))
    }
    if (packageId > 0) {
      const detail = await getPackage(packageId)
      this.mergeCatalog(detail.items.map((item) => toCatalogItem(item.bead)))
      return {
        ...blankDraft(),
        name: `${detail.name} DIY`,
        sequence: restorePackageSequence(detail.items),
      }
    }
    if (skuId > 0) {
      const bead = await getBead(skuId)
      this.mergeCatalog([toCatalogItem(bead)])
      return {
        ...blankDraft(),
        name: `${bead.name} 手串`,
        sequence: [skuId],
      }
    }

    const cached = readDraftCache()
    if (!cached) return blankDraft()
    return (await confirmResumeDraft()) ? cached : blankDraft()
  },

  schemeToDraft(scheme: DiySchemeResponse): DraftCache {
    this.mergeCatalog(scheme.catalogSnapshot.map((item) => ({
      id: item.skuId,
      name: item.name,
      material: item.skuCode,
      color: item.status || '方案珠',
      diameterMm: item.diameterMm || 8,
      salePriceCents: 0,
      imageUrl: item.coverUrl || '',
      colorHex: colorFromText(item.name),
    })))
    return {
      schemeId: scheme.id,
      schemeVersion: scheme.version,
      name: scheme.name,
      wristCircumferenceMm: scheme.wristCircumferenceMm,
      fitType: scheme.fitType,
      cordOptionId: scheme.cordOptionId,
      sequence: scheme.sequence,
      selectedIndex: -1,
    }
  },

  mergeCatalog(items: CatalogItem[]) {
    const merged = new Map(this.data.catalog.map((item) => [item.id, item]))
    for (const item of items) merged.set(item.id, item)
    this.setData({ catalog: Array.from(merged.values()) })
  },

  async chooseCategory(event: WechatMiniprogram.TouchEvent) {
    const categoryId = Number(event.currentTarget.dataset.id || 0)
    this.setData({ categoryId })
    await this.searchCatalog()
  },

  onKeywordInput(event: WechatMiniprogram.Input) {
    this.setData({ keyword: event.detail.value })
  },

  async searchCatalog() {
    try {
      const page = await listBeads({
        page: 1,
        size: 30,
        categoryId: this.data.categoryId || undefined,
        keyword: this.data.keyword || undefined,
      })
      this.mergeCatalog(page.items.map(toCatalogItem))
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '珠子加载失败',
      })
    }
  },

  addCatalogBead(event: WechatMiniprogram.TouchEvent) {
    const skuId = Number(event.currentTarget.dataset.id || 0)
    if (!skuId) return
    const selectedIndex = this.data.selectedIndex >= 0
      ? this.data.selectedIndex
      : null
    const next = addBead(this.data.sequence, skuId, selectedIndex)
    this.refreshDerivedState(next, {
      authoritative: false,
      persistDraft: true,
      pushHistory: true,
      selectedIndex: selectedIndex === null ? next.length - 1 : selectedIndex + 1,
    })
  },

  removeSelected() {
    if (this.data.selectedIndex < 0) return
    const next = removeBead(this.data.sequence, this.data.selectedIndex)
    this.refreshDerivedState(next, {
      authoritative: false,
      persistDraft: true,
      pushHistory: true,
      selectedIndex: Math.min(this.data.selectedIndex, next.length - 1),
    })
  },

  increaseSelectedSku() {
    const skuId = this.selectedSkuId()
    if (!skuId) return
    this.refreshDerivedState(
      adjustSkuQuantity(this.data.sequence, skuId, 1),
      {
        authoritative: false,
        persistDraft: true,
        pushHistory: true,
      },
    )
  },

  decreaseSelectedSku() {
    const skuId = this.selectedSkuId()
    if (!skuId) return
    const next = adjustSkuQuantity(this.data.sequence, skuId, -1)
    this.refreshDerivedState(next, {
      authoritative: false,
      persistDraft: true,
      pushHistory: true,
      selectedIndex: normalizeSelectedIndex(this.data.selectedIndex, next),
    })
  },

  replaceSelected() {
    if (this.data.selectedIndex < 0 || this.data.catalog.length === 0) return
    const currentSkuId = this.selectedSkuId()
    const replacement = this.data.catalog.find((item) => item.id !== currentSkuId)
      || this.data.catalog[0]
    this.refreshDerivedState(
      replaceBead(this.data.sequence, this.data.selectedIndex, replacement.id),
      {
        authoritative: false,
        persistDraft: true,
        pushHistory: true,
      },
    )
  },

  undo() {
    if (!this.history.canUndo()) return
    this.refreshDerivedState(this.history.undo(), {
      authoritative: false,
      persistDraft: true,
      pushHistory: false,
      selectedIndex: -1,
    })
  },

  redo() {
    if (!this.history.canRedo()) return
    this.refreshDerivedState(this.history.redo(), {
      authoritative: false,
      persistDraft: true,
      pushHistory: false,
      selectedIndex: -1,
    })
  },

  changeWrist(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value || 0)
    const wristCircumferenceMm = this.data.wristOptions[index]
      || this.data.wristCircumferenceMm
    this.setData({
      wristCircumferenceMm,
      wristCircumferenceText: formatWristLabel(wristCircumferenceMm),
      wristOptionIndex: index,
    })
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
      pushHistory: false,
    })
  },

  changeFitType(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value || 0)
    const fitType = FIT_TYPES[index] || 'STANDARD'
    this.setData({ fitType, fitTypeIndex: index })
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
      pushHistory: false,
    })
  },

  changeCord(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value || 0)
    const cordOption = this.data.cordOptions[index]
    if (!cordOption) return
    this.setData({
      cordOptionId: cordOption.id,
      cordOptionIndex: index,
    })
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
      pushHistory: false,
    })
  },

  onCanvasTouchStart(event: WechatMiniprogram.TouchEvent) {
    const point = event.touches[0]
    if (!point) return
    const index = this.hitTest(point.x, point.y)
    this.dragFromIndex = index
    this.dragToIndex = index
    this.setData({
      selectedIndex: index,
      selectedNumber: index >= 0 ? index + 1 : 0,
    })
    this.drawBracelet()
  },

  onCanvasTouchMove(event: WechatMiniprogram.TouchEvent) {
    if (this.dragFromIndex < 0) return
    const point = event.touches[0]
    if (!point) return
    const index = this.hitTest(point.x, point.y)
    if (index >= 0) this.dragToIndex = index
  },

  onCanvasTouchEnd() {
    const from = this.dragFromIndex
    const to = this.dragToIndex
    this.dragFromIndex = -1
    this.dragToIndex = -1
    if (from < 0 || to < 0 || from === to) return
    this.refreshDerivedState(moveBead(this.data.sequence, from, to), {
      authoritative: false,
      persistDraft: true,
      pushHistory: true,
      selectedIndex: to,
    })
  },

  async saveDraft() {
    if (this.data.saving) return
    this.setData({ saving: true, errorMessage: '' })
    try {
      const payload = {
        name: this.data.name || DEFAULT_NAME,
        version: this.data.schemeVersion,
        wristCircumferenceMm: this.data.wristCircumferenceMm,
        fitType: this.data.fitType,
        cordOptionId: this.data.cordOptionId,
        sequence: this.data.sequence,
      }
      const saved = this.data.schemeId
        ? await updateScheme(this.data.schemeId, payload)
        : await createScheme(payload)
      this.setData({
        schemeId: saved.id,
        schemeVersion: saved.version,
        name: saved.name,
      })
      wx.removeStorageSync(DRAFT_CACHE_KEY)
      await this.uploadPreviewSafely(saved.id)
      wx.showToast({ title: '方案已保存', icon: 'success' })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '保存失败',
      })
    } finally {
      this.setData({ saving: false })
    }
  },

  async confirmQuote() {
    if (this.data.quoting) return
    this.setData({ quoting: true, errorMessage: '' })
    try {
      const quote = await quoteDiy({
        wristCircumferenceMm: this.data.wristCircumferenceMm,
        fitType: this.data.fitType,
        cordOptionId: this.data.cordOptionId,
        sequence: this.data.sequence,
      })
      this.applyQuote(quote)
      if (quote.quotable) {
        wx.showToast({ title: '权威报价已更新', icon: 'success' })
      }
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '报价失败',
      })
    } finally {
      this.setData({ quoting: false })
    }
  },

  goSchemes() {
    wx.navigateTo({ url: '/pages/diy/schemes' })
  },

  refreshDerivedState(
    sequence: BeadSequence,
    options: {
      authoritative: boolean
      persistDraft: boolean
      pushHistory: boolean
      selectedIndex?: number
    },
  ) {
    if (options.pushHistory) this.history.push(sequence)
    const summary = this.buildLocalSummary(sequence, options.authoritative)
    const selectedIndex = options.selectedIndex === undefined
      ? normalizeSelectedIndex(this.data.selectedIndex, sequence)
      : normalizeSelectedIndex(options.selectedIndex, sequence)
    this.setData({
      sequence,
      selectedIndex,
      selectedNumber: selectedIndex >= 0 ? selectedIndex + 1 : 0,
      countText: summary.countText,
      lengthText: summary.lengthText,
      sizeText: summary.sizeText,
      priceText: summary.priceText,
      priceBadge: summary.priceBadge,
      quoteIssues: options.authoritative ? this.data.quoteIssues : [],
      authoritative: options.authoritative,
      canUndo: this.history.canUndo(),
      canRedo: this.history.canRedo(),
    })
    if (options.persistDraft) writeDraftCache(this.currentDraft())
    this.drawBracelet()
  },

  buildLocalSummary(
    sequence: BeadSequence,
    authoritative: boolean,
  ): EditorSummary {
    const config = this.config
    const catalogMap = this.catalogMap()
    const currentLengthMm = sequence.reduce(
      (sum, skuId) => sum + (catalogMap[skuId]?.diameterMm || 0),
      0,
    )
    const targetLengthMm = config
      ? this.data.wristCircumferenceMm + allowanceForFit(config, this.data.fitType)
      : this.data.wristCircumferenceMm
    const sizeStatus = sizeStatusFor(
      currentLengthMm,
      targetLengthMm,
      config?.rule.toleranceMm || 0,
    )
    const estimatedPriceCents = estimatePriceCents(
      sequence,
      catalogMap,
      craftFeeCents(config, sequence.length),
      this.currentCord()?.extraFeeCents || 0,
    )
    return buildEditorSummary({
      beadCount: sequence.length,
      currentLengthMm,
      targetLengthMm,
      sizeStatus,
      estimatedPriceCents,
      authoritative,
    })
  },

  applyQuote(quote: DiyQuoteResponse) {
    const summary = buildEditorSummary({
      beadCount: this.data.sequence.length,
      currentLengthMm: quote.currentLengthMm,
      targetLengthMm: quote.targetLengthMm,
      sizeStatus: quote.sizeStatus,
      estimatedPriceCents: yuanToCents(quote.totalAmount),
      authoritative: quote.quotable,
    })
    this.setData({
      quoteIssues: quote.issues,
      countText: summary.countText,
      lengthText: summary.lengthText,
      sizeText: summary.sizeText,
      priceText: summary.priceText,
      priceBadge: summary.priceBadge,
      authoritative: quote.quotable,
    })
  },

  currentDraft(): DraftCache {
    return {
      schemeId: this.data.schemeId,
      schemeVersion: this.data.schemeVersion,
      name: this.data.name,
      wristCircumferenceMm: this.data.wristCircumferenceMm,
      fitType: this.data.fitType,
      cordOptionId: this.data.cordOptionId,
      sequence: this.data.sequence,
      selectedIndex: this.data.selectedIndex,
    }
  },

  selectedSkuId(): number {
    return this.data.selectedIndex >= 0
      ? this.data.sequence[this.data.selectedIndex] || 0
      : 0
  },

  currentCord(): CordOptionItem | undefined {
    return this.data.cordOptions.find(
      (item) => item.id === this.data.cordOptionId,
    )
  },

  catalogMap(): Record<number, {
    diameterMm: number
    salePriceCents: number
    colorHex: string
    imageUrl: string
    name: string
  }> {
    return Object.fromEntries(this.data.catalog.map((item) => [
      item.id,
      {
        diameterMm: item.diameterMm,
        salePriceCents: item.salePriceCents,
        colorHex: item.colorHex,
        imageUrl: item.imageUrl,
        name: item.name,
      },
    ]))
  },

  hitTest(x: number, y: number): number {
    const point = this.layoutPoints.find((item) => {
      const dx = x - item.x
      const dy = y - item.y
      return Math.sqrt(dx * dx + dy * dy) <= item.radius + 8
    })
    return point?.index ?? -1
  },

  drawBracelet() {
    wx.createSelectorQuery()
      .select('#bracelet-canvas')
      .fields({ node: true, size: true }, (result) => {
        const canvasResult = result as {
          node?: CanvasNode
          width?: number
          height?: number
        }
        const canvas = canvasResult.node
        const width = canvasResult.width || 300
        const height = canvasResult.height || 300
        if (!canvas) {
          this.layoutPoints = layoutBracelet(
            this.data.sequence,
            this.catalogMap(),
            { width, height },
          )
          return
        }
        this.canvasNode = canvas
        const dpr = wx.getSystemInfoSync().pixelRatio || 1
        canvas.width = width * dpr
        canvas.height = height * dpr
        const ctx = canvas.getContext('2d')
        ctx.scale(dpr, dpr)
        this.layoutPoints = layoutBracelet(
          this.data.sequence,
          this.catalogMap(),
          { width, height },
        )
        this.renderCanvas(ctx, canvas, width, height)
      })
      .exec()
  },

  renderCanvas(
    ctx: CanvasRenderingContext,
    canvas: CanvasNode,
    width: number,
    height: number,
  ) {
    const catalogMap = this.catalogMap()
    ctx.clearRect(0, 0, width, height)
    ctx.beginPath()
    ctx.arc(width / 2, height / 2, Math.min(width, height) * 0.34, 0, Math.PI * 2)
    ctx.lineWidth = 2
    ctx.strokeStyle = this.currentCord()?.colorHex || '#8f6775'
    ctx.stroke()

    for (const point of this.layoutPoints) {
      const item = catalogMap[point.skuId]
      const image = this.resolveCanvasImage(canvas, item?.imageUrl || '')
      if (image) {
        ctx.drawImage(
          image,
          point.x - point.radius,
          point.y - point.radius,
          point.radius * 2,
          point.radius * 2,
        )
      } else {
        ctx.beginPath()
        ctx.arc(point.x, point.y, point.radius, 0, Math.PI * 2)
        ctx.fillStyle = item?.colorHex || '#b8899a'
        ctx.fill()
      }
      if (point.index === this.data.selectedIndex) {
        ctx.beginPath()
        ctx.arc(point.x, point.y, point.radius + 4, 0, Math.PI * 2)
        ctx.lineWidth = 2
        ctx.strokeStyle = '#7a4457'
        ctx.stroke()
      }
    }

    ctx.fillStyle = '#4a3941'
    ctx.font = '14px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(this.data.countText, width / 2, height / 2 - 10)
    ctx.fillText(this.data.sizeText, width / 2, height / 2 + 12)
  },

  resolveCanvasImage(
    canvas: CanvasNode,
    imageUrl: string,
  ): CanvasImage | null {
    if (!imageUrl || !canvas.createImage) return null
    const cached = this.canvasImages[imageUrl]
    if (cached && cached !== 'loading' && cached !== 'error') return cached
    if (cached === 'loading' || cached === 'error') return null
    const image = canvas.createImage()
    this.canvasImages[imageUrl] = 'loading'
    image.onload = () => {
      this.canvasImages[imageUrl] = image
      this.drawBracelet()
    }
    image.onerror = () => {
      this.canvasImages[imageUrl] = 'error'
    }
    image.src = imageUrl
    return null
  },

  async uploadPreviewSafely(schemeId: number) {
    try {
      const filePath = await this.exportPreview()
      const saved = await uploadSchemePreview(schemeId, filePath)
      this.setData({ schemeVersion: saved.version })
      wx.removeStorageSync(PREVIEW_PENDING_KEY)
    } catch {
      wx.setStorageSync(
        PREVIEW_PENDING_KEY,
        JSON.stringify({ schemeId, previewPending: true }),
      )
      wx.showToast({ title: '方案已保存，预览稍后重试', icon: 'none' })
    }
  },

  exportPreview(): Promise<string> {
    return new Promise((resolve, reject) => {
      wx.canvasToTempFilePath({
        canvas: this.canvasNode || undefined,
        fileType: 'png',
        quality: 0.9,
        success: (response) => resolve(response.tempFilePath),
        fail: (error) => reject(new Error(error.errMsg)),
      })
    })
  },
})

function blankDraft(): DraftCache {
  return {
    schemeId: 0,
    schemeVersion: 0,
    name: DEFAULT_NAME,
    wristCircumferenceMm: 160,
    fitType: 'STANDARD',
    cordOptionId: 6001,
    sequence: [],
    selectedIndex: -1,
  }
}

function readDraftCache(): DraftCache | null {
  const raw = wx.getStorageSync(DRAFT_CACHE_KEY)
  if (!raw || typeof raw !== 'string') return null
  try {
    const parsed = JSON.parse(raw) as DraftCache
    return Array.isArray(parsed.sequence) ? parsed : null
  } catch {
    return null
  }
}

function writeDraftCache(draft: DraftCache): void {
  wx.setStorageSync(DRAFT_CACHE_KEY, JSON.stringify(draft))
}

function confirmResumeDraft(): Promise<boolean> {
  return new Promise((resolve) => {
    wx.showModal({
      title: '发现未保存设计',
      content: '是否继续上次编辑？选择取消将创建空白方案。',
      confirmText: '继续编辑',
      cancelText: '新建设计',
      success: (response) => resolve(response.confirm),
      fail: () => resolve(false),
    })
  })
}

function toCatalogItem(bead: {
  id: number
  name: string
  material: string
  color: string
  diameterMm: number
  salePrice: number
  coverUrl: string | null
}): CatalogItem {
  return {
    id: bead.id,
    name: bead.name,
    material: bead.material,
    color: bead.color,
    diameterMm: bead.diameterMm,
    salePriceCents: yuanToCents(bead.salePrice),
    imageUrl: bead.coverUrl || '',
    colorHex: colorFromText(`${bead.material}${bead.color}${bead.name}`),
  }
}

function toCordOptionItem(
  item: DiyConfiguration['cordOptions'][number],
): CordOptionItem {
  return {
    id: item.id,
    name: item.name,
    colorHex: item.colorHex || '#8f6775',
    extraFeeCents: yuanToCents(item.extraFee),
  }
}

function buildWristOptions(min: number, max: number): number[] {
  const values: number[] = []
  for (let value = min; value <= max; value += 5) {
    values.push(value)
  }
  return values.length ? values : [160]
}

function formatWristLabel(value: number): string {
  return `${(value / 10).toFixed(1)} cm`
}

function closestIndex(values: number[], target: number): number {
  if (values.length === 0) return 0
  let bestIndex = 0
  let bestDistance = Number.MAX_SAFE_INTEGER
  values.forEach((value, index) => {
    const distance = Math.abs(value - target)
    if (distance < bestDistance) {
      bestDistance = distance
      bestIndex = index
    }
  })
  return bestIndex
}

function normalizeSelectedIndex(index: number, sequence: number[]): number {
  if (index < 0 || sequence.length === 0) return -1
  return Math.min(index, sequence.length - 1)
}

function allowanceForFit(config: DiyConfiguration, fitType: FitType): number {
  if (fitType === 'SNUG') return config.rule.snugAllowanceMm
  if (fitType === 'LOOSE') return config.rule.looseAllowanceMm
  return config.rule.standardAllowanceMm
}

function sizeStatusFor(
  currentLengthMm: number,
  targetLengthMm: number,
  toleranceMm: number,
): SizeStatus {
  if (currentLengthMm < targetLengthMm - toleranceMm) return 'TOO_SHORT'
  if (currentLengthMm > targetLengthMm + toleranceMm) return 'TOO_LONG'
  return 'FIT'
}

function craftFeeCents(
  config: DiyConfiguration | null,
  beadCount: number,
): number {
  if (!config) return 0
  const rule = config.craftFeeRules.find((item) => (
    beadCount >= item.minBeadCount && beadCount <= item.maxBeadCount
  ))
  return rule ? yuanToCents(rule.fee) : 0
}

function colorFromText(value: string): string {
  const palette = [
    '#c78fa1',
    '#8a6f9d',
    '#70a6a1',
    '#b08a63',
    '#7b9bbd',
    '#ad7789',
  ]
  const code = Array.from(value).reduce(
    (sum, char) => sum + char.charCodeAt(0),
    0,
  )
  return palette[code % palette.length]
}
