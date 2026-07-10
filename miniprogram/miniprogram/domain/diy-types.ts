export type BeadSequence = number[]

export interface DiyBeadCatalogItem {
  diameterMm: number
  salePriceCents?: number
}

export interface BraceletCanvas {
  width: number
  height: number
}

export interface BeadLayoutPoint {
  index: number
  skuId: number
  x: number
  y: number
  radius: number
  angle: number
}
