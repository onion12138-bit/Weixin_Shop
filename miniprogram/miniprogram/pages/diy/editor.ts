import {
  getBead,
  getPackage,
  listAllPublicBeads,
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

interface CanvasImageLoadRequest {
  canvas: CanvasNode
  cacheKey: string
  imageUrl: string
  generation: number
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
const PREVIEW_PENDING_KEY = 'diy_preview_pending'
const DEFAULT_NAME = '我的手串'
const FIT_TYPES: FitType[] = ['SNUG', 'STANDARD', 'LOOSE']
const FIT_TYPE_LABELS = ['贴手', '标准', '宽松']
const MAX_CONCURRENT_CANVAS_IMAGE_LOADS = 4
const CANVAS_REDRAW_FRAME_MS = 16
const PREVIEW_TRANSITION_MS = 240

Page({
  data: {
    loading: true,
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
    sizeText: '请选择珠子',
    priceText: '预估 ¥0.00',
    quoteIssues: [] as QuoteIssueItem[],
    authoritative: false,
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

  onLoad(options: Record<string, string>) {
    this.cancelCanvasRedraw()
    this.cancelCompactPreviewCapture()
    this.cancelPreviewTransition()
    this.editorActive = true
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
    this.refreshFavoriteState()
  },

  onUnload() {
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
      catalogInitialLoading: true,
      errorMessage: '',
    })
    try {
      const beadsPromise = listAllPublicBeads()
      const configPromise = getDiyConfiguration().then((config) => {
        this.config = config
        const cordOptions = config.cordOptions.map(toCordOptionItem)
        const wristOptions = buildWristOptions(
          config.rule.minWristMm,
          config.rule.maxWristMm,
        )
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
        .then((categories) => this.setData({ categories }))
        .catch(() => this.setData({ categories: [] }))
      const catalogPromise = beadsPromise.then((beads) => {
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

      const initial = await this.resolveInitialDraft(options)
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
    } catch (error) {
      this.initialVisualLoading = false
      this.setData({
        loading: false,
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
    this.mergeCatalog(scheme.catalogSnapshot.map((item) => ({
      id: item.skuId,
      name: item.name,
      material: item.skuCode,
      color: item.color || '方案珠',
      diameterMm: item.diameterMm || 8,
      salePriceCents: 0,
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

  async chooseCategory(event: WechatMiniprogram.TouchEvent) {
    const categoryId = Number(event.currentTarget.dataset.id || 0)
    this.setData({ categoryId })
    await this.searchCatalog()
  },

  onKeywordInput(event: WechatMiniprogram.Input) {
    this.setData({ keyword: event.detail.value })
  },

  async searchCatalog() {
    if (this.data.catalogLoading) return
    this.setData({ catalogLoading: true })
    try {
      if (this.data.favoritesOnly) {
        const favoriteIds = new Set(readFavoriteBeadIds())
        const items = (await listAllPublicBeads({
          categoryId: this.data.categoryId || undefined,
          keyword: this.data.keyword || undefined,
        })).filter((item) => favoriteIds.has(item.id))
        this.mergeCatalog(items.map(toCatalogItem), true)
      } else {
        const items = await listAllPublicBeads({
          categoryId: this.data.categoryId || undefined,
          keyword: this.data.keyword || undefined,
        })
        this.mergeCatalog(items.map(toCatalogItem), true)
      }
    } catch (error) {
      wx.showToast({
        title: error instanceof Error ? error.message : '珠子加载失败',
        icon: 'none',
      })
    } finally {
      this.setData({ catalogLoading: false })
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
    const skuId = Number(event.currentTarget.dataset.id || 0)
    if (!skuId) return
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
    if (this.data.selectedIndex < 0 || this.data.catalog.length === 0) return
    const currentSkuId = this.selectedSkuId()
    const replacement = this.data.catalog.find((item) => item.id !== currentSkuId)
      || this.data.catalog[0]
    this.refreshDerivedState(
      replaceBead(this.data.sequence, this.data.selectedIndex, replacement.id),
      {
        authoritative: false,
        persistDraft: true,
      },
    )
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
    })
  },

  changeFitType(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value || 0)
    const fitType = FIT_TYPES[index] || 'STANDARD'
    this.setData({ fitType, fitTypeIndex: index })
    this.refreshDerivedState(this.data.sequence, {
      authoritative: false,
      persistDraft: true,
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
      selectedIndex: to,
    })
  },

  async persistScheme(): Promise<number> {
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
    return saved.id
  },

  async saveDraft() {
    if (this.data.saving) return
    this.setData({ saving: true, errorMessage: '' })
    try {
      await this.persistScheme()
      wx.showToast({ title: '方案保存成功', icon: 'success' })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '保存失败',
      })
    } finally {
      this.setData({ saving: false })
    }
  },

  async goCheckout() {
    if (this.data.checkingOut || this.data.saving) return
    if (!this.data.sequence.length) {
      wx.showToast({ title: '请先添加珠子', icon: 'none' })
      return
    }
    this.setData({ checkingOut: true, errorMessage: '' })
    try {
      const schemeId = await this.persistScheme()
      wx.navigateTo({ url: `/pages/order/confirm?schemeId=${schemeId}` })
    } catch (error) {
      this.setData({
        errorMessage: error instanceof Error ? error.message : '方案保存失败',
      })
    } finally {
      this.setData({ checkingOut: false })
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
      selectedIndex?: number
    },
  ) {
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
      sizeText: summary.sizeText,
      priceText: summary.priceText,
      quoteIssues: options.authoritative ? this.data.quoteIssues : [],
      authoritative: options.authoritative,
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
    if (!this.editorActive) return
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
        ) {
          return
        }
        this.canvasQueryPending = false
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
          this.cancelCanvasRedraw()
          this.canvasImages = {}
          this.canvasImageQueue = []
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
    if (
      this.compactPreviewCaptureTimer !== null
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
      if (!this.editorActive) return
      void this.captureCompactPreview().catch(() => undefined)
    }, CANVAS_REDRAW_FRAME_MS)
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

  scheduleCanvasRedraw(canvas: CanvasNode, generation: number) {
    if (this.canvasRedrawTimer !== null) return
    this.canvasRedrawTimer = setTimeout(() => {
      this.canvasRedrawTimer = null
      if (
        !this.editorActive
        || generation !== this.editorGeneration
        || canvas !== this.canvasNode
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

function toCatalogItem(bead: PublicBead): CatalogItem {
  return {
    id: bead.id,
    name: bead.name,
    material: bead.material,
    color: bead.color,
    diameterMm: bead.diameterMm,
    salePriceCents: yuanToCents(bead.salePrice),
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
