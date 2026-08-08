import type { DiySchemeResponse } from '../services/diy'

export interface HomeSchemePreviewBead {
  key: string
  name: string
  imageUrl: string
  style: string
}

export interface HomeSchemeCard {
  id: number
  name: string
  crystals: string
  createdAt: string
  previewBeads: HomeSchemePreviewBead[]
}

const PREVIEW_SIZE_RPX = 132
const PREVIEW_CENTER_RPX = PREVIEW_SIZE_RPX / 2
const PLACEHOLDER_PREVIEW = '/assets/images/bead-placeholder.svg'

/** 首页只展示最新真实方案，并用方案顺序中的真实珠图重新拼出无中心阴影的缩略图。 */
export function toHomeSchemeCard(scheme: DiySchemeResponse): HomeSchemeCard {
  const catalogById = new Map(
    scheme.catalogSnapshot.map((item) => [item.skuId, item]),
  )
  const names = Array.from(new Set(scheme.sequence
    .map((skuId) => catalogById.get(skuId)?.name)
    .filter((name): name is string => Boolean(name))))
  const summaryNames = names.slice(0, 3)
  const crystals = summaryNames.length
    ? `${summaryNames.join(' · ')}${names.length > 3 ? ' 等' : ''}`
    : `${scheme.beadCount} 颗珠子`
  const count = Math.max(1, scheme.sequence.length)
  const beadSize = Math.max(19, Math.min(29, 270 / count))
  const radius = 48 - beadSize / 2

  const previewBeads = scheme.sequence.map((skuId, index) => {
    const item = catalogById.get(skuId)
    const angle = -Math.PI / 2 + (Math.PI * 2 * index) / count
    const left = PREVIEW_CENTER_RPX + Math.cos(angle) * radius - beadSize / 2
    const top = PREVIEW_CENTER_RPX + Math.sin(angle) * radius - beadSize / 2
    return {
      key: `${index}-${skuId}`,
      name: item?.name || '方案珠子',
      imageUrl: item?.diyVisual?.canvasUrl
        || item?.coverUrl
        || PLACEHOLDER_PREVIEW,
      style: [
        `left:${left.toFixed(2)}rpx`,
        `top:${top.toFixed(2)}rpx`,
        `width:${beadSize.toFixed(2)}rpx`,
        `height:${beadSize.toFixed(2)}rpx`,
      ].join(';'),
    }
  })

  return {
    id: scheme.id,
    name: scheme.name,
    crystals,
    createdAt: formatDate(scheme.createdAt),
    previewBeads,
  }
}

function formatDate(value: string): string {
  const exact = value.match(/^(\d{4}-\d{2}-\d{2})/)
  if (exact) return exact[1]
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}
