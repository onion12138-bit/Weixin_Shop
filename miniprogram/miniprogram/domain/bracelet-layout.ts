import type {
  BeadLayoutPoint,
  BeadSequence,
  BraceletCanvas,
  DiyBeadCatalogItem,
} from './diy-types'

export function layoutBracelet(
  sequence: BeadSequence,
  catalog: Record<number, Pick<DiyBeadCatalogItem, 'diameterMm'>>,
  canvas: BraceletCanvas = { width: 300, height: 300 },
): BeadLayoutPoint[] {
  if (sequence.length === 0) return []

  const diameters = sequence.map((skuId) => catalog[skuId]?.diameterMm || 1)
  const totalDiameter = diameters.reduce((sum, diameter) => sum + diameter, 0)
  const maxDiameter = Math.max(...diameters)
  const shortSide = Math.min(canvas.width, canvas.height)
  const centerX = canvas.width / 2
  const centerY = canvas.height / 2
  const scale = Math.min(
    shortSide / Math.max(maxDiameter * 6, 1),
    shortSide / 44,
  )
  const maxRadius = maxDiameter * scale / 2
  const ringRadius = Math.max(0, shortSide / 2 - maxRadius - 4)

  let consumed = 0
  return sequence.map((skuId, index) => {
    const diameterMm = diameters[index]
    const centerLength = consumed + diameterMm / 2
    const angle = -Math.PI / 2
      + centerLength / totalDiameter * Math.PI * 2
    consumed += diameterMm
    return {
      index,
      skuId,
      x: centerX + Math.cos(angle) * ringRadius,
      y: centerY + Math.sin(angle) * ringRadius,
      radius: diameterMm * scale / 2,
      angle,
    }
  })
}
