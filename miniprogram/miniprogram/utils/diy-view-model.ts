import type {
  DiySchemeResponse,
  SizeStatus,
} from '../services/diy'

export interface PackageSequenceItem {
  beadSkuId: number
  quantity: number
  sortOrder: number
}

export interface EditorSummaryInput {
  beadCount: number
  currentLengthMm: number
  targetLengthMm: number
  sizeStatus: SizeStatus
  estimatedPriceCents: number
  authoritative: boolean
}

export interface EditorSummary {
  countText: string
  lengthText: string
  sizeText: string
  priceText: string
  priceBadge: string
}

export type SchemeCardInput = Pick<DiySchemeResponse,
  | 'id'
  | 'name'
  | 'beadCount'
  | 'totalAmount'
  | 'quoteStatus'
  | 'sequence'
  | 'catalogSnapshot'
  | 'updatedAt'
  | 'version'
>

export interface SchemePreviewBead {
  key: string
  name: string
  imageUrl: string
  style: string
}

export interface SchemeCard {
  id: number
  name: string
  beadCountText: string
  amountText: string
  statusText: string
  statusClass: string
  previewBeads: SchemePreviewBead[]
  updatedAtText: string
  version: number
}

const PLACEHOLDER_BEAD_IMAGE = '/assets/images/bead-placeholder.svg'
const SCHEME_PREVIEW_SIZE_RPX = 360
const SCHEME_PREVIEW_CENTER_RPX = SCHEME_PREVIEW_SIZE_RPX / 2

/**
 * 将套餐明细中的“某 SKU 数量 N”展开为编辑器使用的有序 SKU 序列。
 *
 * 后端只存 SKU 和数量，前端展示时才临时生成第 1 颗、第 2 颗这样的序号。
 * 这里按 sortOrder 稳定展开，保证从套餐进入编辑器时用户看到的顺序可预测。
 */
export function restorePackageSequence(
  items: PackageSequenceItem[],
): number[] {
  return items
    .map((item, index) => ({ ...item, index }))
    .sort((left, right) => {
      if (left.sortOrder !== right.sortOrder) {
        return left.sortOrder - right.sortOrder
      }
      return left.index - right.index
    })
    .flatMap((item) => Array.from(
      { length: Math.max(0, item.quantity) },
      () => item.beadSkuId,
    ))
}

/**
 * 统一编辑器顶部摘要文案。
 *
 * 预估价来自本地已加载目录，权威报价来自后端；两者用 badge 和前缀区分，
 * 避免用户把本地估算误认为最终制作价格。
 */
export function buildEditorSummary(
  input: EditorSummaryInput,
): EditorSummary {
  const priceBadge = input.authoritative ? '权威' : '预估'
  const pricePrefix = input.authoritative ? '权威报价' : '预估'
  return {
    countText: `${input.beadCount} 颗`,
    lengthText: input.beadCount > 0
      ? `当前 ${formatMm(input.currentLengthMm)} mm / 目标 ${formatMm(input.targetLengthMm)} mm`
      : '尚未选珠',
    sizeText: input.beadCount > 0
      ? sizeStatusText(input.sizeStatus)
      : '请选择珠子',
    priceText: `${pricePrefix} ${formatCents(input.estimatedPriceCents)}`,
    priceBadge,
  }
}

export function toSchemeCard(input: SchemeCardInput): SchemeCard {
  return {
    id: input.id,
    name: input.name,
    beadCountText: `${input.beadCount} 颗`,
    amountText: formatYuan(input.totalAmount),
    statusText: quoteStatusText(input.quoteStatus),
    statusClass: input.quoteStatus.toLowerCase().replace(/_/g, '-'),
    previewBeads: buildSchemePreviewBeads(input),
    updatedAtText: formatDateTime(input.updatedAt),
    version: input.version,
  }
}

/**
 * 方案列表重新使用保存的珠子快照拼出手串。
 *
 * 这样无论历史预览图中是否带有旧版中心投影，列表都会稳定展示完整、
 * 无中心阴影的手串；同时不需要增大卡片预览区域。
 */
function buildSchemePreviewBeads(input: SchemeCardInput): SchemePreviewBead[] {
  const catalogById = new Map(
    input.catalogSnapshot.map((item) => [item.skuId, item]),
  )
  const count = Math.max(1, input.sequence.length)
  const beadSize = Math.max(28, Math.min(66, 1080 / count))
  const radius = SCHEME_PREVIEW_CENTER_RPX - 18 - beadSize / 2

  return input.sequence.map((skuId, index) => {
    const item = catalogById.get(skuId)
    const angle = -Math.PI / 2 + (Math.PI * 2 * index) / count
    const left = SCHEME_PREVIEW_CENTER_RPX
      + Math.cos(angle) * radius
      - beadSize / 2
    const top = SCHEME_PREVIEW_CENTER_RPX
      + Math.sin(angle) * radius
      - beadSize / 2
    return {
      key: `${index}-${skuId}`,
      name: item?.name || '方案珠子',
      imageUrl: item?.diyVisual?.canvasUrl
        || item?.coverUrl
        || PLACEHOLDER_BEAD_IMAGE,
      style: [
        `left:${left.toFixed(2)}rpx`,
        `top:${top.toFixed(2)}rpx`,
        `width:${beadSize.toFixed(2)}rpx`,
        `height:${beadSize.toFixed(2)}rpx`,
      ].join(';'),
    }
  })
}

export function sizeStatusText(status: SizeStatus): string {
  if (status === 'FIT') return '尺寸合适'
  if (status === 'TOO_SHORT') return '偏短，请增加珠子或调整腕围'
  return '偏长，可减少珠子或调整腕围'
}

export function quoteStatusText(status: string): string {
  if (status === 'VALID') return '报价有效'
  if (status === 'STALE') return '需重新报价'
  if (status === 'INVALID') return '不可报价'
  return '未报价'
}

export function formatCents(cents: number): string {
  return `¥${(Math.max(0, Math.round(cents)) / 100).toFixed(2)}`
}

export function formatYuan(value: number): string {
  return `¥${Number(value || 0).toFixed(2)}`
}

function formatMm(value: number): string {
  const normalized = Math.round(value * 10) / 10
  return Number.isInteger(normalized)
    ? String(normalized)
    : normalized.toFixed(1)
}

function formatDateTime(value: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hour = pad(date.getHours())
  const minute = pad(date.getMinutes())
  return `${month}-${day} ${hour}:${minute}`
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}
