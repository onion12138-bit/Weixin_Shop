/** 集中定义珠序、布局和画布配置的数据结构，统一各绘制环节的单位与字段。 */
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
