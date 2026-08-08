import type {
  BeadRenderItem,
  BraceletRenderPlan,
} from './bracelet-render-plan'

export interface CanvasImageLike {}

export interface BraceletDrawingContext {
  width: number
  height: number
  fillStyle: string
  globalAlpha: number
  lineWidth: number
  strokeStyle: string
  save(): void
  restore(): void
  translate(x: number, y: number): void
  scale(x: number, y: number): void
  beginPath(): void
  arc(
    x: number,
    y: number,
    radius: number,
    startAngle: number,
    endAngle: number,
  ): void
  fillRect(x: number, y: number, width: number, height: number): void
  fill(): void
  stroke(): void
  drawImage(
    image: CanvasImageLike,
    x: number,
    y: number,
    width: number,
    height: number,
  ): void
}

export type ResolveBeadImage = (
  item: BeadRenderItem,
) => CanvasImageLike | null

const FULL_CIRCLE = Math.PI * 2
const FALLBACK_BEAD_COLOR = '#b8899a'

export function renderBraceletCanvas(
  ctx: BraceletDrawingContext,
  plan: BraceletRenderPlan,
  resolveImage: ResolveBeadImage,
): void {
  ctx.save()
  try {
    const centerX = ctx.width / 2
    const centerY = ctx.height / 2
    const braceletRadius = braceletRadiusFor(plan.beads, centerX, centerY)

    ctx.fillStyle = plan.config.canvasBackgroundColor
    ctx.fillRect(0, 0, ctx.width, ctx.height)

    ctx.globalAlpha = 1
    ctx.beginPath()
    ctx.arc(centerX, centerY, braceletRadius, 0, FULL_CIRCLE)
    ctx.lineWidth = 2
    ctx.strokeStyle = plan.cordColor
    ctx.stroke()

    for (const item of plan.beads) {
      drawBeadShadow(ctx, item, plan.config.beadShadowOpacity)
      drawBead(ctx, item, resolveImage(item))
    }

    for (const item of plan.beads) {
      if (!item.selected) continue
      ctx.globalAlpha = 1
      ctx.beginPath()
      ctx.arc(item.x, item.y, item.drawRadius + 4, 0, FULL_CIRCLE)
      ctx.lineWidth = 2
      ctx.strokeStyle = '#7a4457'
      ctx.stroke()
    }
  } finally {
    ctx.restore()
  }
}

function braceletRadiusFor(
  beads: BeadRenderItem[],
  centerX: number,
  centerY: number,
): number {
  if (beads.length === 0) return 0
  return beads.reduce((largest, item) => Math.max(
    largest,
    Math.hypot(item.x - centerX, item.y - centerY),
  ), 0)
}

function drawBeadShadow(
  ctx: BraceletDrawingContext,
  item: BeadRenderItem,
  opacity: number,
): void {
  ctx.globalAlpha = opacity
  ctx.beginPath()
  ctx.arc(
    item.x,
    item.y + item.drawRadius * 0.18,
    item.drawRadius * 0.86,
    0,
    FULL_CIRCLE,
  )
  ctx.fillStyle = '#000000'
  ctx.fill()
}

function drawBead(
  ctx: BraceletDrawingContext,
  item: BeadRenderItem,
  image: CanvasImageLike | null,
): void {
  ctx.globalAlpha = 1
  if (image) {
    ctx.drawImage(
      image,
      item.x - item.drawRadius,
      item.y - item.drawRadius,
      item.drawRadius * 2,
      item.drawRadius * 2,
    )
    return
  }

  ctx.beginPath()
  ctx.arc(item.x, item.y, item.drawRadius, 0, FULL_CIRCLE)
  ctx.fillStyle = item.colorHex || FALLBACK_BEAD_COLOR
  ctx.fill()
}
