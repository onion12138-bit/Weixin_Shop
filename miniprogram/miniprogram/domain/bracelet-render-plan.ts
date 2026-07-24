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
  cordColor: string
  beads: BeadRenderItem[]
}

export interface BraceletRenderOptions {
  cordColor?: string
}

const DEFAULT_CORD_COLOR = '#8f6775'

export function buildBraceletRenderPlan(
  points: BeadLayoutPoint[],
  config: BraceletVisualConfig,
  selectedIndex: number,
  options: BraceletRenderOptions = {},
): BraceletRenderPlan {
  const beads = points.map((point) => ({
    ...point,
    drawRadius: point.radius * (1 + config.beadOverlapRatio),
    depth: point.y,
    selected: point.index === selectedIndex,
  }))

  beads.sort((left, right) => left.depth - right.depth || left.index - right.index)

  return {
    config: { ...config },
    cordColor: options.cordColor || DEFAULT_CORD_COLOR,
    beads,
  }
}
