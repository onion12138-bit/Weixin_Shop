import type { SizeStatus } from '../services/diy'

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

export interface SchemeCardInput {
  id: number
  name: string
  beadCount: number
  totalAmount: number
  quoteStatus: string
  previewUrl: string | null
  updatedAt: string
  version: number
}

export interface SchemeCard {
  id: number
  name: string
  beadCountText: string
  amountText: string
  statusText: string
  statusClass: string
  previewUrl: string
  updatedAtText: string
  version: number
}

const PLACEHOLDER_PREVIEW = '/assets/images/bead-placeholder.svg'

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
    previewUrl: input.previewUrl || PLACEHOLDER_PREVIEW,
    updatedAtText: formatDateTime(input.updatedAt),
    version: input.version,
  }
}

export function sizeStatusText(status: SizeStatus): string {
  if (status === 'FIT') return '尺寸合适'
  if (status === 'TOO_SHORT') return '偏短，请增加珠子或调整腕围'
  return '偏长，请减少珠子或调整腕围'
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
