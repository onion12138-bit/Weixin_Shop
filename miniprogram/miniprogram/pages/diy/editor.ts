import { isNavigationPending, navigateOnce, resetNavigation } from '../../utils/navigation'
import { createHistory, type EditorHistory } from '../../domain/editor-history'
import {
  getBead,
  getPackage,
  listAllPublicBeads,
  listCategories,
  type Category,
} from '../../services/catalog'
import { getCommerceRule } from '../../services/commerce'
import {
  createScheme,
  getDiyConfiguration,
  getScheme,
  quoteDiy,
  updateScheme,
  yuanToCents,
  type DiyConfiguration,
  type DiyQuoteResponse,
  type DiySchemeResponse,
  type FitType,
  type SizeStatus,
} from '../../services/diy'
import { hasPendingSchemePreview, queueSchemePreview, retryPendingSchemePreviews } from '../../services/scheme-preview'
import {
  addBead,
  adjustSkuQuantity,
  moveBead,
  removeBead,
  replaceBead,
} from '../../domain/editor-state'
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
import {
  resolveCanvasUrl,
  resolveTrayUrl,
} from '../../domain/diy-visual-fallback'
import {
  readFavoriteBeadIds,
  toggleFavoriteBead,
} from '../../domain/favorite-beads'
import { buildBraceletRenderPlan } from '../../domain/bracelet-render-plan'
import {
  renderBraceletCanvas,
  type BraceletDrawingContext,
  type CanvasImageLike,
} from '../../domain/bracelet-canvas-renderer'
import type {
  DiyVisual,
  PublicBead,
} from '../../utils/catalog-view-model'

interface CatalogItem {
  id: number
  name: string
  material: string
  color: string
  diameterMm: number
  salePriceCents: number
  availableQty: number | null
  unavailable: boolean
  trayImageUrl: string
  canvasImageUrl: string
  coverImageUrl: string
  visualVersion: number
  colorHex: string
  isFavorite: boolean
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

// History includes editable design and selection only. Saved identity/version must never rewind.
type EditorSnapshot = Pick<DraftCache,
  'sequence' | 'selectedIndex' | 'wristCircumferenceMm' | 'fitType' | 'cordOptionId'>

interface CanvasNode {
  width: number
  height: number
  getContext(type: '2d'): CanvasRenderingContext
  createImage?(): CanvasImage
}

interface PreviewSnapshot {
  sequence: number[]
  catalog: CatalogItem[]
  visualConfig: DiyConfiguration['visualConfig'] | undefined
  cordColor: string | undefined
}

interface CanvasImage {
  src: string
  onload: () => void
  onerror: () => void
}

interface CanvasImageLoadRequest {
  canvas: CanvasNode
  cacheKey: string
  imageUrl: string
  generation: number
  imageRevision: number
}

interface CanvasRenderingContext {
  fillRect(x: number, y: number, width: number, height: number): void
  save(): void
  restore(): void
  translate(x: number, y: number): void
  scale(x: number, y: number): void
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
  globalAlpha: number
  lineWidth: number
  strokeStyle: string
  fillStyle: string
}

const DRAFT_CACHE_KEY = 'diy_editor_draft'
const DEFAULT_NAME = '我的手串'
const FIT_TYPES: FitType[] = ['SNUG', 'STANDARD', 'LOOSE']
const FIT_TYPE_LABELS = ['贴手', '标准', '宽松']
const MAX_CONCURRENT_CANVAS_IMAGE_LOADS = 4
const CANVAS_REDRAW_FRAME_MS = 16
const PREVIEW_TRANSITION_MS = 240
const EXPORT_CANVAS_SIZE = 620
const EXPORT_IMAGE_WAIT_MS = 1500
const DRAFT_WRITE_INTERVAL_MS = 250
const COMPACT_PREVIEW_IDLE_MS = 150
const EXPORT_PREPARE_IDLE_MS = 600

Page({
  data: {
    loading: true,
    loadFailed: false,
    saving: false,
    checkingOut: false,
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
    totalBeadCount: 0,
    selectedIndex: -1,
    selectedNumber: 0,
    canUndo: false,
    canRedo: false,
    replacementIndex: -1,
    replacementOriginalName: '',
    replacementCandidateId: 0,
    replacementCandidateName: '',
    replacementChangeText: '',
    replacementStockText: '',
    materialWarnings: [] as string[],
    previewExpanded: true,
    catalog: [] as CatalogItem[],
    visibleCatalog: [] as CatalogItem[],
    catalogLoading: false,
    catalogInitialLoading: true,
    compactPreviewUrl: '',
    favoritesOnly: false,
    categories: [] as Category[],
    categoryId: 0,
    keyword: '',
    countText: '0 颗',
    lengthText: '尚未选珠',
    targetLengthText: '目标 160 mm',
    currentLengthText: '当前 0 mm',
    sizeText: '请选择珠子',
    priceText: '预估 ¥0.00',
    quoteIssues: [] as QuoteIssueItem[],
    authoritative: false,
    minimumOrderAmountCents: 2000,
    minimumOrderAmountText: '¥20.00',
    errorMessage: '',
  },

  /**
   * 编辑器只把“有序 SKU 数组”作为设计状态。
   * 临时序号、数量统计、预估价格和 Canvas 坐标全部由该数组派生，避免多份状态冲突。
   */
  config: null as DiyConfiguration | null,
  layoutPoints: [] as BeadLayoutPoint[],
  canvasNode: null as CanvasNode | null,
  canvasContext: null as CanvasRenderingContext | null,
  canvasLogicalWidth: 0,
  canvasLogicalHeight: 0,
  canvasQueryPending: false,
  canvasQueryRevision: 0,
  canvasImageRevision: 0,
  canvasImages: {} as Record<
    string,
    CanvasImage | 'queued' | 'loading' | 'error'
  >,
  canvasImageQueue: [] as CanvasImageLoadRequest[],
  activeCanvasImageLoads: 0,
  canvasRedrawTimer: null as ReturnType<typeof setTimeout> | null,
  compactPreviewCaptureTimer: null as ReturnType<typeof setTimeout> | null,
  compactPreviewCapturePromise: null as Promise<string> | null,
  canvasRenderRevision: 0,
  compactPreviewCapturedRevision: -1,
  editorActive: false,
  editorGeneration: 0,
  initialVisualLoading: false,
  dragFromIndex: -1,
  dragToIndex: -1,
  pendingAiRecommendationRaw: null as unknown,
  currentPageScrollTop: 0,
  previewAutoCollapseTriggered: false,
  previewExpansionScrollResetting: false,
  previewVisibilityQueryPending: false,
  previewTransitionTimer: null as ReturnType<typeof setTimeout> | null,
  catalogRequestId: 0,
  designRevision: 0,
  quoteRequestId: 0,
  editHistory: null as EditorHistory<EditorSnapshot> | null,
  replacementRevision: -1,
  draftWriteTimer: null as ReturnType<typeof setTimeout> | null,
  draftDirty: false,
  previewPrepareTimer: null as ReturnType<typeof setTimeout> | null,
  preparedPreview: null as { key: string; promise: Promise<string> } | null,
  editorHidden: false,
  editorDisposed: false,

  onLoad(options: Record<string, string>) {
    this.cancelCanvasRedraw()
    this.cancelCompactPreviewCapture()
    this.cancelPreviewTransition()
    this.editorActive = true
    this.editorHidden = false
    this.editorDisposed = false
    this.editorGeneration += 1
    this.editHistory = null
    this.canvasNode = null
    this.canvasContext = null
    this.canvasLogicalWidth = 0
    this.canvasLogicalHeight = 0
    this.canvasQueryPending = false
    this.canvasImages = {}
    this.canvasImageQueue = []
    this.activeCanvasImageLoads = 0
    this.compactPreviewCapturePromise = null
    this.canvasRenderRevision = 0
    this.compactPreviewCapturedRevision = -1
    this.initialVisualLoading = true
    this.setData({
      loading: true,
      catalogInitialLoading: true,
      compactPreviewUrl: '',
    })
    this.currentPageScrollTop = 0
    this.previewAutoCollapseTriggered = false
    this.previewExpansionScrollResetting = false
    this.previewVisibilityQueryPending = false
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
          this.pendingAiRecommendationRaw = null
          try {
            wx.setStorageSync(PENDING_AI_RECOMMENDATION_KEY, '')
          } catch {
            // Never apply raw data if persistent storage cannot be invalidated.
          }
          try {
            wx.removeStorageSync(PENDING_AI_RECOMMENDATION_KEY)
          } catch {
            // The empty overwrite, when available, prevents a replay.
          }
        }
      }
    }
    void this.bootstrap(options)
  },

  onShow() {
    resetNavigation(this)
    this.editorHidden = false
    this.refreshFavoriteState()
    if (this.editorActive && !this.data.loading) {
      this.scheduleCanvasRedraw()
      this.schedulePreviewPreparation()
    }
    void retryPendingSchemePreviews()
  },

  onHide() {
    this.flushDraftCache()
    this.editorHidden = true
    this.cancelCanvasRedraw()
    this.cancelCompactPreviewCapture()
    this.cancelPreviewPreparation()
  },

  onUnload() {
    this.flushDraftCache()
    this.editorDisposed = true
    this.cancelPreviewPreparation()
    this.preparedPreview = null
    this.catalogRequestId += 1
    this.designRevision += 1
    this.cancelCanvasRedraw()
    this.cancelCompactPreviewCapture()
    this.cancelPreviewTransition()
    this.editorActive = false
    this.editorGeneration += 1
    this.canvasNode = null
    this.canvasContext = null
    this.canvasLogicalWidth = 0
    this.canvasLogicalHeight = 0
    this.canvasQueryPending = false
    this.canvasImages = {}
    this.canvasImageQueue = []
    this.activeCanvasImageLoads = 0
    this.compactPreviewCapturePromise = null
    this.canvasRenderRevision = 0
    this.compactPreviewCapturedRevision = -1
    this.initialVisualLoading = false
    this.currentPageScrollTop = 0
    this.previewAutoCollapseTriggered = false
    this.previewExpansionScrollResetting = false
    this.previewVisibilityQueryPending = false
  },

  async bootstrap(options: Record<string, string>) {
    this.setData({
      loading: true,
      loadFailed: false,
      catalogInitialLoading: true,
      errorMessage: '',
    })
    try {
      const beadsPromise = listAllPublicBeads()
      void getCommerceRule()
        .then((rule) => {
          if (this.editorDisposed) return
          const minimumOrderAmountCents = yuanToCents(rule.minimumOrderAmount)
          this.setData({
            minimumOrderAmountCents,
            minimumOrderAmountText: `¥${(minimumOrderAmountCents / 100).toFixed(2)}`,
          })
        })
        .catch(() => undefined)
      const configPromise = getDiyConfiguration().then((config) => {
        this.config = config
        const cordOptions = config.cordOptions.map(toCordOptionItem)
        const wristOptions = buildWristOptions(
          config.rule.minWristMm,
          config.rule.maxWristMm,
        )
        if (this.editorDisposed) return { cordOptions, wristOptions }
        this.setData({
          cordOptions,
          cordOptionNames: cordOptions.map((item) => item.name),
          cordOptionId: cordOptions[0]?.id || this.data.cordOptionId,
          cordOptionIndex: 0,
          wristOptions,
          wristOptionLabels: wristOptions.map(formatWristLabel),
          wristOptionIndex: closestIndex(
            wristOptions,
            this.data.wristCircumferenceMm,
          ),
        })
        return { cordOptions, wristOptions }
      })
      void listCategories()
        .then((categories) => { if (!this.editorDisposed) this.setData({ categories }) })
        .catch(() => { if (!this.editorDisposed) this.setData({ categories: [] }) })
      const catalogPromise = beadsPromise.then((beads) => {
        if (this.editorDisposed) return []
        const favoriteIds = new Set(readFavoriteBeadIds())
        const catalog = beads.map(toCatalogItem).map((item) => ({
          ...item,
          isFavorite: favoriteIds.has(item.id),
        }))
        this.setData({
          catalog,
          visibleCatalog: catalog,
          catalogInitialLoading: false,
        })
        return catalog
      })
      const [{ cordOptions, wristOptions }] = await Promise.all([
        configPromise,
        catalogPromise,
      ])

      // 页面离开后不再弹出恢复草稿提示，也不把迟到的数据送入视图层。
      if (this.editorDisposed) return
      const initial = await this.resolveInitialDraft(options)
      if (this.editorDisposed) return
      if (!cordOptions.some((item) => item.id === initial.cordOptionId)) {
        initial.cordOptionId = cordOptions[0]?.id || initial.cordOptionId
      }
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
      })
      this.editHistory = createHistory(this.editorSnapshot())
      this.setData({ canUndo: false, canRedo: false })
      this.cancelReplacement()
      // 只对刚从服务端载入的保存版本补图，不能用未保存的本地草稿替代。
      if (Number(options.schemeId) > 0 && hasPendingSchemePreview(initial.schemeId)) {
        const snapshot = this.previewSnapshot()
        queueSchemePreview(initial.schemeId, initial.schemeVersion, () => this.exportPreview(snapshot))
      }
    } catch (error) {
      if (this.editorDisposed) return
      this.initialVisualLoading = false
      this.setData({
        loading: false,
        loadFailed: true,
        catalogInitialLoading: false,
        errorMessage: error instanceof Error
          ? error.message
          : 'DIY 设计器加载失败，请稍后重试',
      })
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
    const liveSkuIds = new Set(this.data.catalog.map((item) => item.id))
    this.mergeCatalog(scheme.catalogSnapshot.filter((item) => !liveSkuIds.has(item.skuId)).map((item) => ({
      id: item.skuId,
      name: item.name,
      material: item.skuCode,
      color: item.color || '方案珠',
      diameterMm: item.diameterMm || 8,
      salePriceCents: 0,
      availableQty: null,
      unavailable: true,
      ...visualImageFields(item),
      colorHex: colorFromText(item.color || item.name),
      isFavorite: false,
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

  mergeCatalog(items: CatalogItem[], replaceVisible = false) {
    if (this.editorDisposed) return
    const merged = new Map(this.data.catalog.map((item) => [item.id, item]))
    const favoriteIds = new Set(readFavoriteBeadIds())
    const markedItems = items.map((item) => ({
      ...item,
      isFavorite: favoriteIds.has(item.id),
    }))
    for (const item of markedItems) merged.set(item.id, item)
    const catalog = Array.from(merged.values())
    const visibleSource = replaceVisible ? markedItems : catalog
    this.setData({
      catalog,
      visibleCatalog: this.data.favoritesOnly
        ? visibleSource.filter((item) => item.isFavorite)
        : visibleSource,
    })
    this.setData({ materialWarnings: this.materialWarningsFor(this.data.sequence) })
    if (this.data.replacementCandidateId) this.chooseReplacement(this.data.replacementCandidateId)
  },

  refreshFavoriteState() {
    const favoriteIds = new Set(readFavoriteBeadIds())
    const catalog = this.data.catalog.map((item) => ({
      ...item,
      isFavorite: favoriteIds.has(item.id),
    }))
    const visibleCatalog = this.data.visibleCatalog.map((item) => ({
      ...item,
      isFavorite: favoriteIds.has(item.id),
    })).filter((item) => !this.data.favoritesOnly || item.isFavorite)
    this.setData({ catalog, visibleCatalog })
  },

  toggleFavorite(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id || 0)
    if (!id) return
    const isFavorite = toggleFavoriteBead(id)
    const update = (items: CatalogItem[]) => items
      .map((item) => item.id === id ? { ...item, isFavorite } : item)
    this.setData({
      catalog: update(this.data.catalog),
      visibleCatalog: update(this.data.visibleCatalog)
        .filter((item) => !this.data.favoritesOnly || item.isFavorite),
    })
    wx.showToast({
      title: isFavorite ? '已加入喜欢' : '已取消喜欢',
      icon: 'none',
    })
  },

  toggleFavoritesOnly() {
    this.setData({ favoritesOnly: !this.data.favoritesOnly })
    void this.searchCatalog()
  },

  resetCanvasForResize() {
    this.canvasQueryRevision += 1
    this.canvasNode = null
    this.canvasContext = null
    this.canvasLogicalWidth = 0
    this.canvasLogicalHeight = 0
    this.canvasQueryPending = false
    this.resetCanvasImagesForNewCanvas()
  },

  resetCanvasImagesForNewCanvas() {
    this.cancelCanvasRedraw()
    this.canvasImageRevision += 1
    this.canvasImages = {}
    this.canvasImageQueue = []
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
    const requestId = ++this.catalogRequestId
    const completeDirectory = !this.data.categoryId && !this.data.keyword.trim()
    this.setData({ catalogLoading: true })
    try {
      const items = await listAllPublicBeads({
        categoryId: this.data.categoryId || undefined,
        keyword: this.data.keyword.trim() || undefined,
      })
      if (requestId !== this.catalogRequestId) return
      if (completeDirectory) {
        const publicIds = new Set(items.map((item) => item.id))
        this.setData({ catalog: this.data.catalog.map((item) => publicIds.has(item.id)
          ? item : { ...item, unavailable: true, availableQty: null }) })
      }
      this.mergeCatalog(items.map(toCatalogItem), true)
    } catch (error) {
      if (requestId !== this.catalogRequestId) return
      wx.showToast({
        title: error instanceof Error ? error.message : '珠子加载失败',
        icon: 'none',
      })
    } finally {
      if (requestId === this.catalogRequestId) this.setData({ catalogLoading: false })
    }
  },

  onTrayImageError(event: {
    currentTarget: {
      dataset: Record<string, string | number | boolean | undefined>
    }
  }) {
    const skuId = Number(event.currentTarget.dataset.id || 0)
    const initiatingKey = String(
      event.currentTarget.dataset.visualKey || '',
    )
    let matched = false
    const catalog = this.data.catalog.map((item) => {
      if (item.id !== skuId) return item
      if (initiatingKey !== trayVisualKey(item)) return item
      matched = true
      const coverFallback = item.trayImageUrl !== item.coverImageUrl
        ? item.coverImageUrl
        : ''
      return { ...item, trayImageUrl: coverFallback }
    })
    if (matched) {
      const updated = catalog.find((item) => item.id === skuId)
      this.setData({
        catalog,
        visibleCatalog: this.data.visibleCatalog.map((item) => (
          item.id === skuId && updated ? updated : item
        )),
      })
    }
  },

  addCatalogBead(event: WechatMiniprogram.TouchEvent) {
    if (this.editingBlocked()) return
    const skuId = Number(event.currentTarget.dataset.id || 0)
    if (!skuId || !this.data.catalog.some((item) => item.id === skuId)) return
    if (this.data.catalog.find((item) => item.id === skuId)?.unavailable) {
      wx.showToast({ title: '该珠子已下架，不能新加入', icon: 'none' })
      return
    }
    if (this.data.replacementIndex >= 0) {
      this.chooseReplacement(skuId)
      return
    }
    const selectedIndex = this.data.selectedIndex >= 0
      ? this.data.selectedIndex
      : null
    const next = addBead(this.data.sequence, skuId, selectedIndex)
    this.refreshDerivedState(next, {
      authoritative: false,
      persistDraft: true,
      selectedIndex: selectedIndex === null ? next.length - 1 : selectedIndex + 1,
    })
  },

  removeSelected() {
    if (this.data.selectedIndex < 0) return
    const next = removeBead(this.data.sequence, this.data.selectedIndex)
    this.refreshDerivedState(next, {
      authoritative: false,
      persistDraft: true,
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
      selectedIndex: normalizeSelectedIndex(this.data.selectedIndex, next),
    })
  },

  replaceSelected() {
    if (this.editingBlocked() || this.data.selectedIndex < 0) return
    const original = this.data.catalog.find((item) => item.id === this.selectedSkuId())
    if (!original) return
    this.replacementRevision = this.designRevision
    this.setData({
      replacementIndex: this.data.selectedIndex,
      replacementOriginalName: original.name,
      replacementCandidateId: 0,
      replacementCandidateName: '',
      replacementChangeText: '',
      replacementStockText: '',
    })
  },

  chooseReplacement(skuId: number) {
    if (this.editingBlocked() || this.replacementRevision !== this.designRevision) return
    const candidateItem = this.data.catalog.find((item) => item.id === skuId)
    if (!candidateItem || candidateItem.unavailable) {
      wx.showToast({ title: '该珠子已下架，不能替换为它', icon: 'none' })
      return
    }
    const original = this.catalogMap()[this.data.sequence[this.data.replacementIndex]]
    const candidate = this.catalogMap()[skuId]
    if (!original || !candidate) return
    if (skuId === this.data.sequence[this.data.replacementIndex]) {
      wx.showToast({ title: '请选择另一种珠子', icon: 'none' })
      return
    }
    const delta = candidate.salePriceCents - original.salePriceCents
    const currentLength = this.data.sequence.reduce((sum, id) => sum + (this.catalogMap()[id]?.diameterMm || 0), 0)
    const nextLength = currentLength + candidate.diameterMm - original.diameterMm
    const originalUnavailable = this.data.catalog.find((item) => item.id === this.data.sequence[this.data.replacementIndex])?.unavailable
    const priceChangeText = originalUnavailable ? '原珠已下架，差价暂无法估算'
      : `预估差价 ${delta >= 0 ? '+' : '−'}¥${(Math.abs(delta) / 100).toFixed(2)}`
    this.setData({
      replacementCandidateId: skuId,
      replacementCandidateName: candidate.name,
      replacementChangeText: `${priceChangeText} · 单珠 ${original.diameterMm} → ${candidate.diameterMm} mm · 手串 ${currentLength} → ${nextLength} mm`,
      replacementStockText: this.materialWarningsFor(replaceBead(this.data.sequence, this.data.replacementIndex, skuId)).join('；'),
    })
  },

  confirmReplacement() {
    if (this.editingBlocked() || this.data.replacementIndex < 0
      || !this.data.replacementCandidateId || this.replacementRevision !== this.designRevision) return
    const index = this.data.replacementIndex
    this.refreshDerivedState(
      replaceBead(this.data.sequence, index, this.data.replacementCandidateId),
      {
        authoritative: false,
        persistDraft: true,
        selectedIndex: index,
      },
    )
  },

  cancelReplacement() {
    this.replacementRevision = -1
    this.setData({
      replacementIndex: -1,
      replacementOriginalName: '',
      replacementCandidateId: 0,
      replacementCandidateName: '',
      replacementChangeText: '',
      replacementStockText: '',
    })
  },

  editingBlocked(): boolean {
    return this.data.loading || this.data.saving || this.data.checkingOut || this.editorDisposed
  },

  materialWarningsFor(sequence: BeadSequence): string[] {
    const counts = new Map<number, number>()
    sequence.forEach((id) => counts.set(id, (counts.get(id) || 0) + 1))
    const warnings: string[] = []
    for (const [id, count] of counts) {
      const item = this.data.catalog.find((candidate) => candidate.id === id)
      if (!item || item.unavailable) {
        warnings.push(`${item?.name || `材料 #${id}`}已下架或不可售，请删除或替换`)
      } else if (typeof item.availableQty === 'number' && Number.isSafeInteger(item.availableQty)
        && item.availableQty >= 0 && count > item.availableQty) {
        warnings.push(`${item.name}需要 ${count} 颗，当前可用 ${item.availableQty} 颗，库存不足`)
      }
    }
    return warnings
  },

  editorSnapshot(): EditorSnapshot {
    return {
      sequence: [...this.data.sequence],
      selectedIndex: this.data.selectedIndex,
      wristCircumferenceMm: this.data.wristCircumferenceMm,
      fitType: this.data.fitType,
      cordOptionId: this.data.cordOptionId,
    }
  },

  undoEdit() {
    if (this.editingBlocked() || !this.editHistory?.canUndo()) return
    this.restoreEditorSnapshot(this.editHistory.undo())
  },

  redoEdit() {
    if (this.editingBlocked() || !this.editHistory?.canRedo()) return
    this.restoreEditorSnapshot(this.editHistory.redo())
  },

  restoreEditorSnapshot(snapshot: EditorSnapshot) {
    this.dragFromIndex = -1
    this.dragToIndex = -1
    this.refreshDerivedState(snapshot.sequence, {
      authoritative: false,
      persistDraft: true,
      selectedIndex: snapshot.selectedIndex,
      configuration: snapshot,
      recordHistory: false,
    })
  },

  changeWrist(event: WechatMiniprogram.PickerChange) {
    if (this.data.saving || this.data.checkingOut) return
    const index = Number(event.detail.value || 0)
    const wristCircumferenceMm = this.data.wristOptions[index]
      || this.data.wristCircumferenceMm
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
      configuration: { wristCircumferenceMm },
    })
  },

  changeFitType(event: WechatMiniprogram.PickerChange) {
    if (this.data.saving || this.data.checkingOut) return
    const index = Number(event.detail.value || 0)
    const fitType = FIT_TYPES[index] || 'STANDARD'
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
      configuration: { fitType },
    })
  },

  changeCord(event: WechatMiniprogram.PickerChange) {
    if (this.data.saving || this.data.checkingOut) return
    const index = Number(event.detail.value || 0)
    const cordOption = this.data.cordOptions[index]
    if (!cordOption) return
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
      configuration: { cordOptionId: cordOption.id },
    })
  },

  onCanvasTouchStart(event: WechatMiniprogram.TouchEvent) {
    if (this.editingBlocked()) return
    if (this.data.replacementIndex >= 0) this.cancelReplacement()
    const point = event.touches[0]
    if (!point) return
    const index = this.hitTest(point.x, point.y)
    this.dragFromIndex = index
    this.dragToIndex = index
    this.setData({
      selectedIndex: index,
      selectedNumber: index >= 0 ? index + 1 : 0,
    })
    this.scheduleCanvasRedraw()
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
      selectedIndex: to,
    })
  },

  onCanvasTouchCancel() {
    this.dragFromIndex = -1
    this.dragToIndex = -1
  },

  async persistScheme(): Promise<number> {
    this.flushDraftCache()
    const draftAtSave = this.currentDraft()
    const snapshot = this.previewSnapshot()
    const previewKey = JSON.stringify(snapshot)
    const prepared = this.preparedPreview?.key === previewKey
      ? this.preparedPreview.promise : null
    const payload = {
      name: this.data.name || DEFAULT_NAME,
      version: this.data.schemeVersion,
      wristCircumferenceMm: this.data.wristCircumferenceMm,
      fitType: this.data.fitType,
      cordOptionId: this.data.cordOptionId,
      sequence: [...this.data.sequence],
    }
    const saved = this.data.schemeId
      ? await updateScheme(this.data.schemeId, payload)
      : await createScheme(payload)
    if (!this.editorDisposed) {
      this.setData({ schemeId: saved.id, schemeVersion: saved.version, name: saved.name })
    }
    this.clearSavedDraft(draftAtSave)
    queueSchemePreview(saved.id, saved.version, () => prepared || this.exportPreview(snapshot))
    return saved.id
  },

  async saveDraft() {
    if (this.data.saving || this.data.checkingOut || isNavigationPending(this)) return
    if (!this.data.sequence.length) {
      wx.showToast({ title: '请选择您喜欢的珠子', icon: 'none' })
      return
    }
    this.cancelReplacement()
    this.setData({ saving: true, errorMessage: '' })
    try {
      await this.persistScheme()
      if (this.editorDisposed) return
      wx.showToast({ title: '方案保存成功', icon: 'success' })
    } catch (error) {
      if (this.editorDisposed) return
      this.setData({
        errorMessage: error instanceof Error ? error.message : '保存失败',
      })
    } finally {
      if (!this.editorDisposed) this.setData({ saving: false })
    }
  },

  async goCheckout() {
    if (this.data.checkingOut || this.data.saving || isNavigationPending(this)) return
    if (!this.data.sequence.length) {
      wx.showToast({ title: '请先添加珠子', icon: 'none' })
      return
    }
    if (this.currentEstimateCents() < this.data.minimumOrderAmountCents) {
      wx.showToast({
        title: `订单商品金额需满 ${this.data.minimumOrderAmountText} 才能下单`,
        icon: 'none',
      })
      return
    }
    this.cancelReplacement()
    this.setData({ checkingOut: true, errorMessage: '' })
    try {
      const schemeId = await this.persistScheme()
      if (this.editorDisposed) return
      navigateOnce(this, { url: `/pages/order/confirm?schemeId=${schemeId}` })
    } catch (error) {
      if (this.editorDisposed) return
      this.setData({
        errorMessage: error instanceof Error ? error.message : '方案保存失败',
      })
    } finally {
      if (!this.editorDisposed) this.setData({ checkingOut: false })
    }
  },

  togglePreview() {
    if (this.data.previewExpanded) {
      this.startPreviewCollapse()
      return
    }
    this.expandPreview()
  },

  expandPreview() {
    if (
      this.data.previewExpanded
      || this.previewExpansionScrollResetting
    ) {
      return
    }
    this.cancelPreviewTransition()
    this.previewAutoCollapseTriggered = false
    const canResetPageScroll = this.currentPageScrollTop > 0
      && typeof wx.pageScrollTo === 'function'
    if (!canResetPageScroll) {
      this.setData({ previewExpanded: true }, () => {
        this.drawBracelet()
      })
      return
    }

    this.previewExpansionScrollResetting = true
    wx.pageScrollTo({
      scrollTop: 0,
      duration: PREVIEW_TRANSITION_MS,
    })
    this.previewTransitionTimer = setTimeout(() => {
      this.previewTransitionTimer = null
      this.currentPageScrollTop = 0
      this.previewExpansionScrollResetting = false
      this.setData({ previewExpanded: true }, () => {
        this.drawBracelet()
      })
    }, PREVIEW_TRANSITION_MS)
  },

  startPreviewCollapse() {
    if (
      !this.data.previewExpanded
      || this.previewAutoCollapseTriggered
    ) {
      return
    }
    this.previewAutoCollapseTriggered = true
    this.previewExpansionScrollResetting = false
    this.cancelPreviewTransition()
    // 收起前先画出本帧最后一次编辑，避免复用尚未重绘的旧缩略图。
    if (this.canvasRedrawTimer !== null) {
      this.cancelCanvasRedraw()
      this.drawBracelet()
    }
    const collapse = () => this.setData({ previewExpanded: false })
    if (
      !this.canvasNode
      || (
        this.data.compactPreviewUrl
        && this.compactPreviewCapturedRevision === this.canvasRenderRevision
      )
    ) {
      collapse()
      return
    }
    void this.captureLatestCompactPreview()
      .catch(() => '')
      .finally(collapse)
  },

  onPageScroll(event: { scrollTop?: number }) {
    const scrollTop = Math.max(0, Number(event.scrollTop || 0))
    this.currentPageScrollTop = scrollTop
    if (
      !this.data.previewExpanded
      || this.previewAutoCollapseTriggered
      || this.previewExpansionScrollResetting
      || this.previewVisibilityQueryPending
    ) {
      return
    }
    this.checkExpandedPreviewVisibility()
  },

  checkExpandedPreviewVisibility() {
    this.previewVisibilityQueryPending = true
    const query = wx.createSelectorQuery() as WechatMiniprogram.SelectorQuery & {
      in?(component: object): WechatMiniprogram.SelectorQuery
    }
    const scopedQuery = query.in ? query.in(this) : query
    scopedQuery
      .select('.bracelet-canvas-viewport')
      .fields({ rect: true }, (result) => {
        this.previewVisibilityQueryPending = false
        if (
          !this.data.previewExpanded
          || this.previewAutoCollapseTriggered
          || this.previewExpansionScrollResetting
        ) {
          return
        }
        const rect = result as { bottom?: number } | null
        if (
          rect
          && Number.isFinite(rect.bottom)
          && Number(rect.bottom) <= 0
        ) {
          this.startPreviewCollapse()
        }
      })
      .exec()
  },

  async confirmQuote() {
    if (this.data.quoting || this.editorDisposed) return
    const revision = this.designRevision
    const requestId = ++this.quoteRequestId
    this.setData({ quoting: true, errorMessage: '' })
    try {
      const quote = await quoteDiy({
        wristCircumferenceMm: this.data.wristCircumferenceMm,
        fitType: this.data.fitType,
        cordOptionId: this.data.cordOptionId,
        sequence: this.data.sequence,
      })
      if (this.editorDisposed || revision !== this.designRevision || requestId !== this.quoteRequestId) return
      this.applyQuote(quote)
      if (quote.quotable) {
        wx.showToast({ title: '权威报价已更新', icon: 'success' })
      }
    } catch (error) {
      if (this.editorDisposed || revision !== this.designRevision || requestId !== this.quoteRequestId) return
      this.setData({
        errorMessage: error instanceof Error ? error.message : '报价失败',
      })
    } finally {
      if (!this.editorDisposed && requestId === this.quoteRequestId) this.setData({ quoting: false })
    }
  },

  goSchemes() {
    if (this.data.saving || this.data.checkingOut) return
    navigateOnce(this, { url: '/pages/diy/schemes' })
  },

  refreshDerivedState(
    sequence: BeadSequence,
    options: {
      authoritative: boolean
      persistDraft: boolean
      selectedIndex?: number
      redraw?: boolean
      recordHistory?: boolean
      configuration?: Partial<Pick<EditorSnapshot, 'wristCircumferenceMm' | 'fitType' | 'cordOptionId'>>
    },
  ) {
    if (this.editorDisposed || (options.persistDraft && this.editingBlocked())) return
    if (options.persistDraft && options.recordHistory !== false) {
      const unavailableIds = Array.from(new Set(sequence)).filter((id) => {
        const item = this.data.catalog.find((candidate) => candidate.id === id)
        return !item || item.unavailable
      })
      if (unavailableIds.some((id) => sequence.filter((value) => value === id).length
        > this.data.sequence.filter((value) => value === id).length)) {
        wx.showToast({ title: '下架珠子不能新增或增加数量', icon: 'none' })
        return
      }
    }
    if (options.persistDraft && options.recordHistory !== false && this.config && sequence.length > this.config.rule.maxBeadCount) {
      wx.showToast({ title: `最多添加 ${this.config.rule.maxBeadCount} 颗珠子`, icon: 'none' })
      return
    }
    const before = this.editorSnapshot()
    const recordHistory = options.persistDraft && options.recordHistory !== false
    if (recordHistory) {
      if (!this.editHistory) this.editHistory = createHistory(before)
      // Selecting a bead is not a history action, but undo should restore that selection.
      this.editHistory.replaceCurrent(before)
    }
    if (options.configuration) {
      const wristCircumferenceMm = options.configuration.wristCircumferenceMm ?? this.data.wristCircumferenceMm
      const fitType = options.configuration.fitType ?? this.data.fitType
      const cordOptionId = options.configuration.cordOptionId ?? this.data.cordOptionId
      this.setData({
        wristCircumferenceMm,
        wristCircumferenceText: formatWristLabel(wristCircumferenceMm),
        wristOptionIndex: closestIndex(this.data.wristOptions, wristCircumferenceMm),
        fitType,
        fitTypeIndex: Math.max(0, FIT_TYPES.indexOf(fitType)),
        cordOptionId,
        cordOptionIndex: Math.max(0, this.data.cordOptions.findIndex((item) => item.id === cordOptionId)),
      })
    }
    if (!options.authoritative) {
      this.designRevision += 1
      this.quoteRequestId += 1
      this.cancelReplacement()
      // Invalidate captures immediately; a queued redraw may still be one frame away.
      this.compactPreviewCapturedRevision = -1
      this.setData({ quoting: false, compactPreviewUrl: '' })
    }
    const summary = this.buildLocalSummary(sequence, options.authoritative)
    const selectedIndex = options.selectedIndex === undefined
      ? normalizeSelectedIndex(this.data.selectedIndex, sequence)
      : normalizeSelectedIndex(options.selectedIndex, sequence)
    this.setData({
      sequence,
      totalBeadCount: sequence.length,
      selectedIndex,
      selectedNumber: selectedIndex >= 0 ? selectedIndex + 1 : 0,
      countText: summary.countText,
      lengthText: summary.lengthText,
      targetLengthText: summary.targetLengthText,
      currentLengthText: summary.currentLengthText,
      sizeText: summary.sizeText,
      priceText: summary.priceText,
      quoteIssues: options.authoritative ? this.data.quoteIssues : [],
      authoritative: options.authoritative,
    })
    if (recordHistory && !sameDesign(before, this.editorSnapshot())) {
      this.editHistory!.push(this.editorSnapshot())
    }
    this.setData({ canUndo: this.editHistory?.canUndo() || false, canRedo: this.editHistory?.canRedo() || false })
    const materialWarnings = this.materialWarningsFor(sequence)
    this.setData({ materialWarnings })
    if (recordHistory && materialWarnings.some((warning) => warning.includes('库存不足'))) {
      wx.showToast({ title: '当前设计有材料库存不足，请查看提示', icon: 'none' })
    }
    if (options.persistDraft) this.scheduleDraftWrite()
    if (options.redraw !== false) {
      if (options.persistDraft) this.scheduleCanvasRedraw()
      else this.drawBracelet()
    }
    this.schedulePreviewPreparation()
  },

  scheduleDraftWrite() {
    this.draftDirty = true
    if (this.draftWriteTimer !== null) return
    this.draftWriteTimer = setTimeout(() => {
      this.draftWriteTimer = null
      this.flushDraftCache()
    }, DRAFT_WRITE_INTERVAL_MS)
  },

  flushDraftCache() {
    if (this.draftWriteTimer !== null) clearTimeout(this.draftWriteTimer)
    this.draftWriteTimer = null
    if (!this.draftDirty) return
    try {
      writeDraftCache(this.currentDraft())
      this.draftDirty = false
    } catch { /* 缓存写入失败时保留脏标记，退出或下次编辑时再试。 */ }
  },

  clearSavedDraft(savedDraft: DraftCache) {
    if (this.draftWriteTimer !== null) clearTimeout(this.draftWriteTimer)
    this.draftWriteTimer = null
    this.draftDirty = false
    try {
      const cached = readDraftCache()
      if (cached && JSON.stringify(cached) === JSON.stringify(savedDraft)) {
        wx.removeStorageSync(DRAFT_CACHE_KEY)
      }
    }
    catch { /* 服务端保存已经成功，不将缓存清理失败当成保存失败。 */ }
  },

  previewSnapshot(): PreviewSnapshot {
    const ids = new Set(this.data.sequence)
    return {
      sequence: [...this.data.sequence],
      catalog: this.data.catalog.filter((item) => ids.has(item.id)).map((item) => ({ ...item })),
      visualConfig: this.config?.visualConfig ? { ...this.config.visualConfig } : undefined,
      cordColor: this.currentCord()?.colorHex,
    }
  },

  schedulePreviewPreparation() {
    this.cancelPreviewPreparation()
    if (!this.editorActive || this.editorHidden || !this.config?.visualConfig
      || !this.data.sequence.length || this.data.saving || this.data.checkingOut) return
    this.previewPrepareTimer = setTimeout(() => {
      this.previewPrepareTimer = null
      const snapshot = this.previewSnapshot()
      const key = JSON.stringify(snapshot)
      if (this.preparedPreview?.key === key) return
      const promise = this.exportPreview(snapshot)
      this.preparedPreview = { key, promise }
      void promise.catch(() => {
        if (this.preparedPreview?.promise === promise) this.preparedPreview = null
      })
    }, EXPORT_PREPARE_IDLE_MS)
  },

  cancelPreviewPreparation() {
    if (this.previewPrepareTimer !== null) clearTimeout(this.previewPrepareTimer)
    this.previewPrepareTimer = null
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
      targetLengthText: summary.targetLengthText,
      currentLengthText: summary.currentLengthText,
      sizeText: summary.sizeText,
      priceText: summary.priceText,
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

  currentEstimateCents(): number {
    const config = this.config
    return estimatePriceCents(
      this.data.sequence,
      this.catalogMap(),
      craftFeeCents(config, this.data.sequence.length),
      this.currentCord()?.extraFeeCents || 0,
    )
  },

  catalogMap(): Record<number, {
    diameterMm: number
    salePriceCents: number
    colorHex: string
    name: string
  }> {
    return Object.fromEntries(this.data.catalog.map((item) => [
      item.id,
      {
        diameterMm: item.diameterMm,
        salePriceCents: item.salePriceCents,
        colorHex: item.colorHex,
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

  cancelPreviewTransition() {
    if (this.previewTransitionTimer !== null) {
      clearTimeout(this.previewTransitionTimer)
      this.previewTransitionTimer = null
    }
  },

  refreshCanvasLayout(width: number, height: number) {
    const catalogMap = this.catalogMap()
    this.layoutPoints = layoutBracelet(
      this.data.sequence,
      catalogMap,
      { width, height },
    ).map((point) => ({
      ...point,
      colorHex: catalogMap[point.skuId]?.colorHex,
    }))
  },

  drawBracelet() {
    if (!this.editorActive || this.editorHidden) return
    if (
      this.canvasNode
      && this.canvasContext
      && this.canvasLogicalWidth > 0
      && this.canvasLogicalHeight > 0
    ) {
      this.refreshCanvasLayout(
        this.canvasLogicalWidth,
        this.canvasLogicalHeight,
      )
      this.renderCanvas(
        this.canvasContext,
        this.canvasNode,
        this.canvasLogicalWidth,
        this.canvasLogicalHeight,
      )
      return
    }
    if (this.canvasQueryPending) return
    this.canvasQueryPending = true
    const initiatingGeneration = this.editorGeneration
    const initiatingQueryRevision = this.canvasQueryRevision
    const query = wx.createSelectorQuery() as WechatMiniprogram.SelectorQuery & {
      in?(component: object): WechatMiniprogram.SelectorQuery
    }
    const scopedQuery = query.in ? query.in(this) : query
    scopedQuery
      .select('#bracelet-canvas')
      .fields({ node: true, size: true }, (result) => {
        if (
          !this.editorActive
          || initiatingGeneration !== this.editorGeneration
          || initiatingQueryRevision !== this.canvasQueryRevision
        ) {
          return
        }
        this.canvasQueryPending = false
        if (!result) return
        const canvasResult = result as {
          node?: CanvasNode
          width?: number
          height?: number
        }
        const canvas = canvasResult.node
        const width = canvasResult.width || 300
        const height = canvasResult.height || 300
        if (!canvas) {
          this.refreshCanvasLayout(width, height)
          this.finishInitialVisualLoading(true)
          return
        }
        if (this.canvasNode && this.canvasNode !== canvas) {
          this.resetCanvasImagesForNewCanvas()
        }
        this.canvasNode = canvas
        const dpr = wx.getSystemInfoSync().pixelRatio || 1
        canvas.width = width * dpr
        canvas.height = height * dpr
        const ctx = canvas.getContext('2d')
        ctx.scale(dpr, dpr)
        this.canvasContext = ctx
        this.canvasLogicalWidth = width
        this.canvasLogicalHeight = height
        this.refreshCanvasLayout(width, height)
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
    const visualConfig = this.config?.visualConfig
    if (!visualConfig) {
      this.finishInitialVisualLoading(true)
      return
    }
    const plan = buildBraceletRenderPlan(
      this.layoutPoints,
      visualConfig,
      this.data.selectedIndex,
      { cordColor: this.currentCord()?.colorHex },
    )
    const drawingContext = toBraceletDrawingContext(ctx, width, height)
    renderBraceletCanvas(drawingContext, plan, (point) => {
      const item = this.data.catalog.find(
        (catalogItem) => catalogItem.id === point.skuId,
      )
      return item ? this.resolveCanvasImage(canvas, item) : null
    })
    this.canvasRenderRevision += 1
    this.finishInitialVisualLoading()
    if (
      this.canvasImageQueue.length === 0
      && this.activeCanvasImageLoads === 0
    ) {
      this.scheduleCompactPreviewCapture()
    }
  },

  finishInitialVisualLoading(force = false) {
    if (!this.initialVisualLoading) return
    if (!force && (
      this.canvasImageQueue.length > 0
      || this.activeCanvasImageLoads > 0
    )) return
    this.initialVisualLoading = false
    this.setData({ loading: false })
  },

  captureCompactPreview(): Promise<string> {
    if (this.compactPreviewCapturePromise) {
      return this.compactPreviewCapturePromise
    }
    const canvas = this.canvasNode
    if (!canvas) {
      return Promise.reject(new Error('预览画布尚未就绪'))
    }
    const generation = this.editorGeneration
    const revision = this.canvasRenderRevision
    const designRevision = this.designRevision
    const capture = new Promise<string>((resolve, reject) => {
      wx.canvasToTempFilePath({
        canvas,
        fileType: 'png',
        quality: 0.9,
        success: (response) => {
          if (
            this.editorActive
            && generation === this.editorGeneration
            && canvas === this.canvasNode
            && revision === this.canvasRenderRevision
            && designRevision === this.designRevision
          ) {
            this.compactPreviewCapturedRevision = revision
            this.setData({ compactPreviewUrl: response.tempFilePath })
          }
          resolve(response.tempFilePath)
        },
        fail: (error) => reject(new Error(error.errMsg)),
      })
    })
    this.compactPreviewCapturePromise = capture
    capture.then(
      () => {
        if (this.compactPreviewCapturePromise === capture) {
          this.compactPreviewCapturePromise = null
        }
        if (
          this.editorActive
          && this.compactPreviewCapturedRevision !== this.canvasRenderRevision
        ) {
          this.scheduleCompactPreviewCapture()
        }
      },
      () => {
        if (this.compactPreviewCapturePromise === capture) {
          this.compactPreviewCapturePromise = null
        }
      },
    )
    return capture
  },

  captureLatestCompactPreview(attempts = 2): Promise<string> {
    return this.captureCompactPreview().then((filePath) => {
      if (
        this.compactPreviewCapturedRevision === this.canvasRenderRevision
        || attempts <= 1
      ) {
        return filePath
      }
      return this.captureLatestCompactPreview(attempts - 1)
    })
  },

  scheduleCompactPreviewCapture() {
    this.cancelCompactPreviewCapture()
    if (
      this.editorHidden
      || this.compactPreviewCapturePromise
      || !this.canvasNode
      || this.canvasImageQueue.length > 0
      || this.activeCanvasImageLoads > 0
      || this.compactPreviewCapturedRevision === this.canvasRenderRevision
    ) {
      return
    }
    this.compactPreviewCaptureTimer = setTimeout(() => {
      this.compactPreviewCaptureTimer = null
      if (!this.editorActive || this.editorHidden) return
      void this.captureCompactPreview().catch(() => undefined)
    }, COMPACT_PREVIEW_IDLE_MS)
  },

  cancelCompactPreviewCapture() {
    if (this.compactPreviewCaptureTimer === null) return
    clearTimeout(this.compactPreviewCaptureTimer)
    this.compactPreviewCaptureTimer = null
  },

  resolveCanvasImage(
    canvas: CanvasNode,
    item: Pick<
      CatalogItem,
      'canvasImageUrl' | 'coverImageUrl' | 'visualVersion'
    >,
  ): CanvasImage | null {
    if (!canvas.createImage) return null
    const imageUrls = Array.from(new Set(
      [item.canvasImageUrl, item.coverImageUrl].filter(Boolean),
    ))
    for (const canvasImageUrl of imageUrls) {
      const cacheKey = `${item.visualVersion}:${canvasImageUrl}`
      const cached = this.canvasImages[cacheKey]
      if (
        cached
        && cached !== 'queued'
        && cached !== 'loading'
        && cached !== 'error'
      ) {
        return cached
      }
      if (cached === 'queued' || cached === 'loading') return null
      if (cached === 'error') continue
      this.canvasImages[cacheKey] = 'queued'
      this.canvasImageQueue.push({
        canvas,
        cacheKey,
        imageUrl: canvasImageUrl,
        generation: this.editorGeneration,
        imageRevision: this.canvasImageRevision,
      })
      this.pumpCanvasImageQueue()
      return null
    }
    return null
  },

  pumpCanvasImageQueue() {
    while (
      this.activeCanvasImageLoads < MAX_CONCURRENT_CANVAS_IMAGE_LOADS
      && this.canvasImageQueue.length > 0
    ) {
      const request = this.canvasImageQueue.shift()
      if (!request) return
      if (
        !this.editorActive
        || request.generation !== this.editorGeneration
        || request.imageRevision !== this.canvasImageRevision
        || request.canvas !== this.canvasNode
        || this.canvasImages[request.cacheKey] !== 'queued'
        || !request.canvas.createImage
      ) {
        continue
      }
      const image = request.canvas.createImage()
      this.activeCanvasImageLoads += 1
      this.canvasImages[request.cacheKey] = 'loading'
      image.onload = () => {
        this.completeCanvasImageLoad(request, image)
      }
      image.onerror = () => {
        this.completeCanvasImageLoad(request, null)
      }
      image.src = request.imageUrl
    }
  },

  completeCanvasImageLoad(
    request: CanvasImageLoadRequest,
    image: CanvasImage | null,
  ) {
    if (request.generation !== this.editorGeneration) return
    this.activeCanvasImageLoads = Math.max(
      0,
      this.activeCanvasImageLoads - 1,
    )
    if (request.imageRevision !== this.canvasImageRevision) {
      this.pumpCanvasImageQueue()
      return
    }
    if (
      !this.editorActive
      || request.canvas !== this.canvasNode
    ) {
      this.pumpCanvasImageQueue()
      return
    }
    this.canvasImages[request.cacheKey] = image || 'error'
    this.pumpCanvasImageQueue()
    this.scheduleCanvasRedraw(request.canvas, request.generation)
  },

  scheduleCanvasRedraw(canvas?: CanvasNode | null, generation?: number) {
    const targetCanvas = canvas === undefined ? this.canvasNode : canvas
    const targetGeneration = generation === undefined ? this.editorGeneration : generation
    if (!this.editorActive || this.editorHidden) return
    if (this.canvasRedrawTimer !== null) return
    this.canvasRedrawTimer = setTimeout(() => {
      this.canvasRedrawTimer = null
      if (
        !this.editorActive
        || this.editorHidden
        || targetGeneration !== this.editorGeneration
        || targetCanvas !== this.canvasNode
      ) {
        return
      }
      this.drawBracelet()
    }, CANVAS_REDRAW_FRAME_MS)
  },

  cancelCanvasRedraw() {
    if (this.canvasRedrawTimer === null) return
    clearTimeout(this.canvasRedrawTimer)
    this.canvasRedrawTimer = null
  },

  createExportCanvas(): CanvasNode {
    const runtime = wx as unknown as {
      createOffscreenCanvas?: (options: {
        type: '2d'
        width: number
        height: number
      }) => CanvasNode | null | undefined
    }
    if (typeof runtime.createOffscreenCanvas !== 'function') {
      throw new Error('当前微信版本不支持生成方案预览，请升级后重试')
    }
    let canvas: CanvasNode | null | undefined
    try {
      canvas = runtime.createOffscreenCanvas({
        type: '2d',
        width: EXPORT_CANVAS_SIZE,
        height: EXPORT_CANVAS_SIZE,
      })
    } catch {
      throw new Error('方案预览画布创建失败，请重试')
    }
    if (!canvas || typeof canvas.getContext !== 'function') {
      throw new Error('方案预览画布创建失败，请重试')
    }
    return canvas
  },

  renderExportCanvas(
    canvas: CanvasNode,
    exportImages: Record<number, CanvasImage>,
    suppliedSnapshot?: PreviewSnapshot,
  ) {
    const snapshot = suppliedSnapshot || this.previewSnapshot()
    const visualConfig = snapshot.visualConfig
    if (!visualConfig) {
      throw new Error('方案预览尚未准备好，请稍后重试')
    }
    const catalogMap = Object.fromEntries(snapshot.catalog.map((item) => [item.id, item]))
    const points = layoutBracelet(
      snapshot.sequence,
      catalogMap,
      { width: EXPORT_CANVAS_SIZE, height: EXPORT_CANVAS_SIZE },
    ).map((point) => ({
      ...point,
      colorHex: catalogMap[point.skuId]?.colorHex,
    }))
    const plan = buildBraceletRenderPlan(
      points,
      visualConfig,
      -1,
      { cordColor: snapshot.cordColor },
    )
    const context = canvas.getContext('2d')
    renderBraceletCanvas(
      toBraceletDrawingContext(
        context,
        EXPORT_CANVAS_SIZE,
        EXPORT_CANVAS_SIZE,
      ),
      plan,
      (point) => exportImages[point.skuId] || null,
    )
  },

  loadExportImages(
    canvas: CanvasNode,
    suppliedSnapshot?: PreviewSnapshot,
  ): Promise<Record<number, CanvasImage>> {
    const snapshot = suppliedSnapshot || this.previewSnapshot()
    const catalogMap = Object.fromEntries(
      snapshot.catalog.map((item) => [item.id, item]),
    ) as Record<number, CatalogItem>
    const items = Array.from(new Set(snapshot.sequence))
      .map((skuId) => catalogMap[skuId])
      .filter((item): item is CatalogItem => Boolean(item))
    const exportImages: Record<number, CanvasImage> = {}
    if (!canvas.createImage || items.length === 0) {
      return Promise.resolve(exportImages)
    }

    return new Promise((resolve) => {
      let settled = false
      let nextItemIndex = 0
      let activeItems = 0
      let completedItems = 0
      let timeoutId: ReturnType<typeof setTimeout> | null = null
      const activeImages = new Set<CanvasImage>()

      const detachImage = (image: CanvasImage, abort: boolean) => {
        image.onload = () => undefined
        image.onerror = () => undefined
        activeImages.delete(image)
        if (!abort) return
        try {
          image.src = ''
        } catch {
          // Some Canvas implementations expose a read-only src after failure.
        }
      }

      const finish = (abortActive: boolean) => {
        if (settled) return
        settled = true
        if (timeoutId !== null) clearTimeout(timeoutId)
        if (abortActive) {
          Array.from(activeImages).forEach((image) => {
            detachImage(image, true)
          })
        }
        activeImages.clear()
        resolve(exportImages)
      }

      const pump = () => {
        while (
          !settled
          && activeItems < MAX_CONCURRENT_CANVAS_IMAGE_LOADS
          && nextItemIndex < items.length
        ) {
          const item = items[nextItemIndex]
          nextItemIndex += 1
          activeItems += 1
          const imageUrls = Array.from(new Set(
            [item.canvasImageUrl, item.coverImageUrl].filter(Boolean),
          ))

          const completeItem = (image: CanvasImage | null) => {
            if (settled) return
            if (image) exportImages[item.id] = image
            activeItems = Math.max(0, activeItems - 1)
            completedItems += 1
            if (completedItems === items.length) {
              finish(false)
              return
            }
            pump()
          }

          const tryNextUrl = (urlIndex: number) => {
            if (settled) return
            if (urlIndex >= imageUrls.length) {
              completeItem(null)
              return
            }
            let image: CanvasImage
            try {
              image = canvas.createImage!()
            } catch {
              tryNextUrl(urlIndex + 1)
              return
            }
            activeImages.add(image)
            let attemptFinished = false
            image.onload = () => {
              if (settled || attemptFinished) return
              attemptFinished = true
              detachImage(image, false)
              completeItem(image)
            }
            image.onerror = () => {
              if (settled || attemptFinished) return
              attemptFinished = true
              detachImage(image, false)
              tryNextUrl(urlIndex + 1)
            }
            image.src = imageUrls[urlIndex]
          }

          tryNextUrl(0)
        }
      }

      timeoutId = setTimeout(() => {
        finish(true)
      }, EXPORT_IMAGE_WAIT_MS)
      pump()
    })
  },

  async exportPreview(suppliedSnapshot?: PreviewSnapshot): Promise<string> {
    const snapshot = suppliedSnapshot || this.previewSnapshot()
    const canvas = this.createExportCanvas()
    const exportImages = await this.loadExportImages(canvas, snapshot)
    this.renderExportCanvas(canvas, exportImages, snapshot)
    return new Promise((resolve, reject) => {
      wx.canvasToTempFilePath({
        canvas,
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

function sameDesign(left: EditorSnapshot, right: EditorSnapshot): boolean {
  return left.wristCircumferenceMm === right.wristCircumferenceMm
    && left.fitType === right.fitType
    && left.cordOptionId === right.cordOptionId
    && left.sequence.length === right.sequence.length
    && left.sequence.every((skuId, index) => skuId === right.sequence[index])
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

function toCatalogItem(bead: PublicBead): CatalogItem {
  return {
    id: bead.id,
    name: bead.name,
    material: bead.material,
    color: bead.color,
    diameterMm: bead.diameterMm,
    salePriceCents: yuanToCents(bead.salePrice),
    availableQty: typeof bead.availableQty === 'number' && Number.isSafeInteger(bead.availableQty)
      && bead.availableQty >= 0 ? bead.availableQty : null,
    unavailable: false,
    ...visualImageFields(bead),
    colorHex: colorFromText(`${bead.material}${bead.color}${bead.name}`),
    isFavorite: false,
  }
}

function visualImageFields(bead: {
  coverUrl: string | null
  diyVisual: DiyVisual | null
}): Pick<
  CatalogItem,
  | 'trayImageUrl'
  | 'canvasImageUrl'
  | 'coverImageUrl'
  | 'visualVersion'
> {
  return {
    trayImageUrl: resolveTrayUrl(bead),
    canvasImageUrl: resolveCanvasUrl(bead),
    coverImageUrl: bead.coverUrl || '',
    visualVersion: bead.diyVisual?.version || 0,
  }
}

function trayVisualKey(item: Pick<
  CatalogItem,
  'trayImageUrl' | 'visualVersion'
>): string {
  return `${item.visualVersion}:${item.trayImageUrl}`
}

function toBraceletDrawingContext(
  ctx: CanvasRenderingContext,
  width: number,
  height: number,
): BraceletDrawingContext {
  return {
    width,
    height,
    get fillStyle() {
      return ctx.fillStyle
    },
    set fillStyle(value: string) {
      ctx.fillStyle = value
    },
    get globalAlpha() {
      return ctx.globalAlpha
    },
    set globalAlpha(value: number) {
      ctx.globalAlpha = value
    },
    get lineWidth() {
      return ctx.lineWidth
    },
    set lineWidth(value: number) {
      ctx.lineWidth = value
    },
    get strokeStyle() {
      return ctx.strokeStyle
    },
    set strokeStyle(value: string) {
      ctx.strokeStyle = value
    },
    save: () => ctx.save(),
    restore: () => ctx.restore(),
    translate: (x, y) => ctx.translate(x, y),
    scale: (x, y) => ctx.scale(x, y),
    beginPath: () => ctx.beginPath(),
    arc: (x, y, radius, startAngle, endAngle) => (
      ctx.arc(x, y, radius, startAngle, endAngle)
    ),
    fillRect: (x, y, rectWidth, rectHeight) => (
      ctx.fillRect(x, y, rectWidth, rectHeight)
    ),
    fill: () => ctx.fill(),
    stroke: () => ctx.stroke(),
    drawImage: (image: CanvasImageLike, x, y, imageWidth, imageHeight) => (
      ctx.drawImage(
        image as CanvasImage,
        x,
        y,
        imageWidth,
        imageHeight,
      )
    ),
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
