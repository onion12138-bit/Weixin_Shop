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
  colorHex?: string
  x: number
  y: number
  radius: number
  angle: number
}

export interface BraceletVisualConfig {
  canvasBackgroundColor: string
  braceletShadowOpacity: number
  beadShadowOpacity: number
  beadOverlapRatio: number
}
