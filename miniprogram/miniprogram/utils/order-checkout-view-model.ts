import type { CustomerCoupon } from '../services/account'
import type { DiySchemeResponse } from '../services/diy'

export interface CheckoutBeadRow {
  skuId: number
  name: string
  imageUrl: string
  colorHex: string
  specificationText: string
  quantityText: string
}

export interface CheckoutCouponItem extends CustomerCoupon {
  amountText: string
  thresholdText: string
  expiresText: string
  statusText: string
  usable: boolean
  selected: boolean
}

export function buildCheckoutBeads(scheme: DiySchemeResponse): {
  rows: CheckoutBeadRow[]
  hasMore: boolean
} {
  const snapshots = new Map(scheme.catalogSnapshot.map((item) => [item.skuId, item]))
  const orderedSkuIds = Array.from(new Set(scheme.sequence))
  const allRows = orderedSkuIds.map((skuId) => {
    const item = snapshots.get(skuId)
    const quantity = Number(scheme.skuQuantities[skuId] || 0)
    return {
      skuId,
      name: item?.name || `珠子 #${skuId}`,
      imageUrl: item?.coverUrl || item?.diyVisual?.trayUrl || '',
      colorHex: colorHex(item?.color),
      specificationText: item?.diameterMm
        ? `${formatCompactNumber(item.diameterMm)} mm`
        : '规格以实物为准',
      quantityText: `× ${quantity}`,
    }
  })
  return { rows: allRows.slice(0, 3), hasMore: allRows.length > 3 }
}

export function buildCheckoutCoupons(
  coupons: CustomerCoupon[],
  productTotalCents: number,
  selectedCouponId: number,
  now = Date.now(),
): CheckoutCouponItem[] {
  return coupons.map((coupon) => {
    const expired = new Date(coupon.expiresAt).getTime() <= now
    const meetsThreshold = yuanToCents(coupon.minimumSpend) <= productTotalCents
    const usable = coupon.status === 'UNUSED' && !expired && meetsThreshold
    return {
      ...coupon,
      amountText: formatCents(yuanToCents(coupon.discountAmount)),
      thresholdText: Number(coupon.minimumSpend) > 0
        ? `满 ${formatCents(yuanToCents(coupon.minimumSpend))} 可用`
        : '无门槛',
      expiresText: `有效期至 ${formatDate(coupon.expiresAt)}`,
      statusText: couponStatusText(coupon, expired, meetsThreshold),
      usable,
      selected: usable && coupon.id === selectedCouponId,
    }
  })
}

export function couponDiscountCents(
  coupons: CheckoutCouponItem[],
  selectedCouponId: number,
  productTotalCents: number,
): number {
  const coupon = coupons.find((item) => item.id === selectedCouponId && item.usable)
  return coupon
    ? Math.min(productTotalCents, yuanToCents(coupon.discountAmount))
    : 0
}

export function shippingFeeCents(
  productTotalCents: number,
  discountCents: number,
  freeShippingThresholdCents: number,
  baseShippingFeeCents: number,
): number {
  const discountedProductCents = Math.max(0, productTotalCents - discountCents)
  return discountedProductCents >= Math.max(0, freeShippingThresholdCents)
    ? 0
    : Math.max(0, baseShippingFeeCents)
}

export function maskPhone(phone: string): string {
  const normalized = phone.trim()
  if (normalized.length < 7) return normalized
  return `${normalized.slice(0, 3)}****${normalized.slice(-4)}`
}

export function yuanToCents(value: number): number {
  return Math.max(0, Math.round(Number(value || 0) * 100))
}

export function formatCents(value: number): string {
  return `¥${(Math.max(0, value) / 100).toFixed(2)}`
}

function couponStatusText(
  coupon: CustomerCoupon,
  expired: boolean,
  meetsThreshold: boolean,
): string {
  if (coupon.status === 'USED') return '已使用'
  if (coupon.status === 'REVOKED') return '已失效'
  if (coupon.status === 'EXPIRED' || expired) return '已过期'
  if (!meetsThreshold) return `未满 ${formatCents(yuanToCents(coupon.minimumSpend))}`
  return '可使用'
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}.${month}.${day}`
}

function colorHex(value: string | null | undefined): string {
  const color = (value || '').toLowerCase()
  if (color.includes('紫')) return '#b999ce'
  if (color.includes('绿')) return '#83b79d'
  if (color.includes('黄') || color.includes('金')) return '#e8c57d'
  if (color.includes('红')) return '#a55e65'
  if (color.includes('白') || color.includes('透明')) return '#f2ede9'
  if (color.includes('粉')) return '#efbdcd'
  if (/^#[0-9a-f]{6}$/i.test(color)) return color
  return '#cfa8b5'
}

function formatCompactNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}
