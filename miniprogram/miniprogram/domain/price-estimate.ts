/** 按 SKU 汇总数量并以分计算本地估价，实际价格以服务端报价为准。 */
import type { BeadSequence } from './diy-types'

export function countSkuQuantities(
  sequence: BeadSequence,
): Record<number, number> {
  return sequence.reduce<Record<number, number>>((quantities, skuId) => {
    quantities[skuId] = (quantities[skuId] || 0) + 1
    return quantities
  }, {})
}

export function estimatePriceCents(
  sequence: BeadSequence,
  catalog: Record<number, { salePriceCents: number }>,
  craftFeeCents: number,
  cordFeeCents: number,
): number {
  const beadTotal = sequence.reduce(
    (sum, skuId) => sum + (catalog[skuId]?.salePriceCents || 0),
    0,
  )
  return beadTotal + craftFeeCents + cordFeeCents
}
