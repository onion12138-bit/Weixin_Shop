/** 以不可变数组更新珠子顺序和选择位置，保持撤销记录与画布状态一致。 */
import type { BeadSequence } from './diy-types'

export function addBead(
  sequence: BeadSequence,
  skuId: number,
  selectedIndex: number | null,
): BeadSequence {
  const insertAt = selectedIndex === null
    ? sequence.length
    : Math.min(selectedIndex + 1, sequence.length)
  return [
    ...sequence.slice(0, insertAt),
    skuId,
    ...sequence.slice(insertAt),
  ]
}

export function removeBead(
  sequence: BeadSequence,
  index: number,
): BeadSequence {
  if (index < 0 || index >= sequence.length) return sequence
  return sequence.filter((_, current) => current !== index)
}

export function replaceBead(
  sequence: BeadSequence,
  index: number,
  skuId: number,
): BeadSequence {
  if (index < 0 || index >= sequence.length) return sequence
  return sequence.map((value, current) => current === index ? skuId : value)
}

export function moveBead(
  sequence: BeadSequence,
  from: number,
  to: number,
): BeadSequence {
  if (
    from < 0 ||
    from >= sequence.length ||
    to < 0 ||
    to >= sequence.length ||
    from === to
  ) return sequence
  const next = sequence.slice()
  const [value] = next.splice(from, 1)
  next.splice(to, 0, value)
  return next
}

export function adjustSkuQuantity(
  sequence: BeadSequence,
  skuId: number,
  delta: number,
): BeadSequence {
  if (delta === 0) return sequence
  let next = sequence.slice()
  if (delta > 0) {
    for (let count = 0; count < delta; count += 1) {
      next = insertAfterFirstSkuRun(next, skuId)
    }
    return next
  }
  for (let count = 0; count < Math.abs(delta); count += 1) {
    const index = next.lastIndexOf(skuId)
    if (index < 0) return next
    next = removeBead(next, index)
  }
  return next
}

function insertAfterFirstSkuRun(
  sequence: BeadSequence,
  skuId: number,
): BeadSequence {
  const firstIndex = sequence.indexOf(skuId)
  if (firstIndex < 0) {
    return [...sequence, skuId]
  }
  let insertAt = firstIndex + 1
  while (insertAt < sequence.length && sequence[insertAt] === skuId) {
    insertAt += 1
  }
  return [
    ...sequence.slice(0, insertAt),
    skuId,
    ...sequence.slice(insertAt),
  ]
}
