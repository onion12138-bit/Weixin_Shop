import type {
  BeadLayoutPoint,
  BraceletVisualConfig,
} from './diy-types'

export type { BraceletVisualConfig } from './diy-types'

export interface BeadRenderItem extends BeadLayoutPoint {
  drawRadius: number
  depth: number
  selected: boolean
}

export interface BraceletRenderPlan {
  config: BraceletVisualConfig
  beads: BeadRenderItem[]
}

export function buildBraceletRenderPlan(
  points: BeadLayoutPoint[],
  config: BraceletVisualConfig,
  selectedIndex: number,
): BraceletRenderPlan {
  const beads = points.map((point) => ({
    ...point,
    drawRadius: point.radius * (1 + config.beadOverlapRatio),
    depth: point.y,
    selected: point.index === selectedIndex,
  }))

  beads.sort((left, right) => left.depth - right.depth || left.index - right.index)

  return { config: { ...config }, beads }
}
